/* Sample skeleton for proxy */

import java.io.*;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

class CacheInfo {
	String cachePathName;
	int versionNum;
	AtomicBoolean modified;
	AtomicBoolean isUsing;
	int size;
	public CacheInfo(String n, int ver, boolean modified,boolean use,int s) {
		this.cachePathName = n;
		this.versionNum = ver;
		this.modified = new AtomicBoolean(modified);
		this.isUsing = new AtomicBoolean(use);
		this.size = s;
	}
}

class FileInfo{
	String pathName;
	String cachePathName;
	String privateCachePathName;
	File file;
	RandomAccessFile raf;
	boolean isPrivateCopy;
	public FileInfo(String path, String c, String pv, File f, RandomAccessFile r, boolean p) {
		this.pathName = path;
		this.cachePathName = c;
		this.privateCachePathName = pv;
		this.file=f;
		this.raf=r;
		this.isPrivateCopy = p;
	}
}


class Proxy {
	public static String serverip;
	public static int port;
	public static String cachedir;
	public static int cachesize;
	public static IServer server;
	public static int clientID;
	
	public static LRU cacheLRU;	
	public static final int MAXFDSIZE = 1000;
	public static final int MAXBUFSIZE = 100;
	
	private static class FileHandler implements FileHandling {
		ConcurrentHashMap<Integer,FileInfo> fd2Raf;
		public String cacheAddedDir;
		public String fileName;
		
		public synchronized void connect2Server() {
			/* Connect to server */
			try {
				String url = String.format("//127.0.0.1:%d/Server", Proxy.port);
				System.err.println("Try to connect to server .... url is "+url);
				try {
					Proxy.server = (IServer)Naming.lookup (url);
					Proxy.clientID = server.getClientID()+1;
					server.setClientID(clientID);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				try {
					System.err.println(Proxy.server.sayHello());
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			} catch (NotBoundException e) {
				System.err.println("Proxy fails to create server");
			}	
		}
		
		public synchronized int getFileFromServer(String path, String cachePath) {
			System.err.println("Download file from server ..... ");
			BufferedOutputStream outputFile;
			try {
				outputFile = new BufferedOutputStream(new FileOutputStream(cachePath));
				byte data[] = server.downloadFile(path);
				System.err.println("datalength " + String.valueOf(data.length));
				outputFile.write(data, 0, data.length);
				// rewrite everything?
				outputFile.flush();
				outputFile.close();
				System.err.println("Finish write to cachefile");
				return data.length;
			} catch (FileNotFoundException e) {
				System.err.println("Failed to create a cachefile");
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("File to write,flush or close");
				e.printStackTrace();
			}
			//Should never get here
			return -1;
		}
		
		public synchronized void uploadFileToServer(String path, String cachePath) {
			System.err.println("Upload file from server ..... ");
			File cacheF = new File(cachePath);
			int len = (int) cacheF.length();
			System.err.println("cache file length " + len);
			
			byte buffer[] = new byte[len];
			try {
				BufferedInputStream input = new BufferedInputStream(new FileInputStream(cachePath));
				input.read(buffer, 0, len);
				input.close();
			} catch (Exception e) {
				System.err.println("Proxy Failed to read cache file");
				e.printStackTrace();
			}
			
			try {
				server.uploadFile(path, buffer);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		private void copyFileContent(String src, String dest) {
			File cacheF = new File(src);
			int len = (int) cacheF.length();
			System.err.println("src file length " + len);
			
			
			try {
				BufferedInputStream input = new BufferedInputStream(new FileInputStream(src));
				BufferedOutputStream outputFile = new BufferedOutputStream(new FileOutputStream(dest));
				int start = 0;
				while(len > MAXBUFSIZE) {
					byte buffer[] = new byte[MAXBUFSIZE];
					input.read(buffer, start, MAXBUFSIZE);						
					outputFile.write(buffer, start, MAXBUFSIZE);								
					len = len - MAXBUFSIZE;
					start = start + MAXBUFSIZE;
					System.err.println("Start: "+start);
					System.err.println("Len:"+ len);
				}
				while (len > 0) {
					byte buffer[] = new byte[len];
					input.read(buffer, start, len);						
					System.err.println("datalength " + String.valueOf(buffer.length));
					outputFile.write(buffer, start, len);								
					System.err.println("Finish write to dest");
					len = len - len;
					start = start + len;
				}
				input.close();
				outputFile.flush();
				outputFile.close();	
			} catch (Exception e) {
				System.err.println("Proxy Failed to read src file");
				e.printStackTrace();
			}
			
		}
		
		private String simplifyPath(String path) {
			LinkedList<String> dirStack = new LinkedList<String>();
			String[] pathSplit = path.split("/");
			//Get rid of .. and .  simplify the path
			for(String p : pathSplit) {
				if( p.equals("..") && !dirStack.isEmpty() ) { 
					dirStack.removeLast();//pop
				} else if(p.length()!=0 && !p.equals("..") && !p.equals(".") ) {
					dirStack.addLast(p); //push
				}   
			}
			
			//Build simplified path
			StringBuilder sb = new StringBuilder();
			if(dirStack.isEmpty()) return "/"; //!!! corner case
			for(String p : dirStack) { // build output
				sb.append("/");
				sb.append(p);
			}
			//Get the directory of the path
			String s="";
			while (dirStack.size() > 1) {
				String d = dirStack.pop();
				System.err.println("dir:"+d);
				s = "/"+s+d;
			}
			this.cacheAddedDir = s;
			//Get the filename of input
			this.fileName = dirStack.pop();
			System.err.println("cacheDir:"+this.cacheAddedDir);
			return sb.toString().substring(1);
		}
		
		
		//If path is directory, we do not cache.
		public synchronized int open( String path, OpenOption o ) {
			System.err.println("-----Call open, client ID:"+Proxy.clientID + " path:" + path+"-----");	
			
			int fd = fd2Raf.size()+1;
			
			/*String c_path;
			String d_path;
			try {
				d_path = new File(Proxy.cachedir).getCanonicalPath();
				System.err.println("dir path is: "+d_path);
				c_path = new File(Proxy.cachedir + path).getCanonicalPath();
				System.err.println("c path is: "+c_path);
				if(!c_path.contains(d_path)) return Errors.EPERM;
			} catch (IOException e2) {
				e2.printStackTrace();
			}*/
			
			//simplify path, get rid of dots
			path = simplifyPath(path);
			System.err.println("path is: " + path);
			String cacheFulldir = Proxy.cachedir;
			if(this.cacheAddedDir != null && this.cacheAddedDir.length() > 0) {
				cacheFulldir = Proxy.cachedir+this.cacheAddedDir;
				File cfd = new File(cacheFulldir);
				if(!cfd.exists()){
					if(!cfd.mkdirs()){
						System.err.println("makedir fails, dir:"+Proxy.cachedir+this.cacheAddedDir);
					}
				}
			}
			
			
			String cachePath = cacheFulldir + "/"+ this.fileName+".txt";
			System.err.println("!!!!!!cachePath:"+cachePath);
			String privateName = this.fileName + "clientID" +String.valueOf(Proxy.clientID);
			String privateCachePath = cacheFulldir + "/"+privateName+".txt";
			System.err.println("!!!!!!privatecachePath:"+privateCachePath);
			int cacheVersion = 0;
			int serverVersion = 0;
			File f;
			
			//HIT, get file from cache
			if(cacheLRU.getMap().containsKey(path)) { //File in cache
				System.err.println("Hit!");
				cacheVersion = cacheLRU.getMap().get(path).versionNum;
				cachePath = cacheLRU.getMap().get(path).cachePathName;
				try {
					serverVersion = server.getVersionNum(path);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				System.err.println("Cache Version"+cacheVersion);
				System.err.println("Server Version" + serverVersion);
				System.err.println("cahcePath in Hit:"+cachePath);
				if (cacheVersion < serverVersion) { 
					//Cache Version Does not Match Server Version
					getFileFromServer(path,cachePath);
					cacheLRU.getMap().get(path).versionNum = serverVersion;
					System.err.println("Version Unmatched! :(");									
				}
				f = new File(cachePath);				
			}
			//MISS, download from server, put in cache
			else {
				System.err.println("Miss, cacheVersionNum " + cacheVersion);
				System.err.println("cahcePath in Miss"+cachePath);
				System.err.println("file:"+path+", new path for this cache: "+cachePath);
				
				try {
					server.initVersionNum(path);
				} catch (RemoteException e1) {
					e1.printStackTrace();
				}
				
				int fileSize = getFileFromServer(path,cachePath);
				CacheInfo cInfo = new CacheInfo(cachePath,cacheVersion,false,true,fileSize);
				if (!cacheLRU.add(path, cInfo)) { //no more mem for cache
					return Errors.ENOMEM;
				}
				
				
				f = new File(cachePath);
				
				if (!f.exists()) {
					System.err.println("create a new cache file called "+cachePath);
					try {
						f.createNewFile();
					} catch (IOException e) {
						System.err.println("failed to create new cache file"); 
						e.printStackTrace();
					}
				}
			}
			cacheLRU.using(path);
			
			if (fd > MAXFDSIZE) {
				return Errors.EMFILE;
			}
			switch(o) {
				case CREATE:
					System.err.println("CREATE, cachePath:"+privateCachePath);
					f = new File(privateCachePath);
					copyFileContent(cachePath,privateCachePath);
					if (f.isDirectory()) {
						return Errors.EISDIR;
					}
					if (!f.exists()) {
						try {
							System.err.println("ct");
							f.createNewFile();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}		
					
					
					try {
						System.err.println("raf");
						RandomAccessFile raf_c = new RandomAccessFile(f,"rw");
						System.err.println("create file info");
						FileInfo fi_c = new FileInfo(path,cachePath,privateCachePath,f,raf_c,true);
						fd2Raf.put(fd,fi_c);
						System.err.println("put in track");
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
								
					break;
				case CREATE_NEW:
					System.err.println("CREATE_NEW, cache path:" + privateCachePath);
					f = new File(privateCachePath);
					copyFileContent(cachePath,privateCachePath);
					if (f.exists()) {
						return Errors.EEXIST;
					}	
					try {
						f.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					if (f.isDirectory()) {
						return Errors.EISDIR;
					}
					try {
						System.err.println("CREATE_NEW");
						RandomAccessFile raf_cn = new RandomAccessFile(f,"rw");
						System.err.println("create file info");
						FileInfo fi_cn = new FileInfo(path,cachePath,privateCachePath,f,raf_cn,true);
						fd2Raf.put(fd,fi_cn);
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
					break;
				case READ:
					System.err.println("READ, cachePath:" + cachePath);
					f = new File(cachePath);
					if (!f.exists()) {
						return Errors.ENOENT;
					}
					RandomAccessFile raf_r = null;
					try {
						if(!f.isDirectory()) {
							raf_r = new RandomAccessFile(f,"r");
						}
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
					FileInfo fi_r = new FileInfo(path,cachePath,"",f,raf_r,false);
					fd2Raf.put(fd,fi_r);
					break;
				case WRITE:
					System.err.println("WRITE cachePath:" + privateCachePath);
					f = new File(privateCachePath);
					copyFileContent(cachePath,privateCachePath);
					if (f.isDirectory()) {
						return Errors.EISDIR;
					}
					if (!f.exists()) {
						return Errors.ENOENT;
					}
					
					try {
						RandomAccessFile raf_w = new RandomAccessFile(f,"rw");
						FileInfo fi_w = new FileInfo(path,cachePath,privateCachePath,f,raf_w,true);
						fd2Raf.put(fd,fi_w);
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
					
					break;
				default:
					return Errors.EINVAL;
			}
			return fd;
		}
		




		//EBADF
		public synchronized int close( int fd ) {
			System.err.println("------close op------");
			FileInfo raf = fd2Raf.get(fd);
			if(raf == null) {
				return Errors.EBADF;
			}
			//Copy private cache copy to cache
			if (raf.isPrivateCopy) {
				String privateCopyPath = raf.privateCachePathName;
				System.err.println("PrivateCopy Path:"+privateCopyPath+" CachePath:"+raf.cachePathName);
				copyFileContent(privateCopyPath,raf.cachePathName);
				File privateCache = new File(privateCopyPath);
				privateCache.delete();
			}
			
			//Upload cache file to server original file
			CacheInfo cInfo = cacheLRU.getMap().get(raf.pathName);
			if (cInfo.modified.get()) {
	            int len = (int) raf.file.length();
	            String path = raf.pathName;
	            String cachePath = cacheLRU.getMap().get(path).cachePathName;
	            uploadFileToServer(path,cachePath);
	            
	            //Put this cache to MRU
	            cInfo.modified.set(false);
	            cInfo.size = len;	            
	            System.err.println("Closing this path in server"+ path+"  file length "+len);
	            System.err.println("Cache file path : "+cachePath);            
			}
			cacheLRU.move2MRU(raf.pathName, cInfo);
			try {
				if (raf.raf != null) {
					raf.raf.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				return -1; //?
			}
			fd2Raf.remove(fd);
			return 0;
		}	
		
		
		//EBADF,EINVAL,EPERM
		public synchronized long write( int fd, byte[] buf ) {
			System.err.println("------write op------");

			FileInfo raf = fd2Raf.get(fd);
			if(raf == null) {
				return Errors.EBADF;
			}
			if (!raf.file.canWrite()) {
				return Errors.EINVAL;
			}
			try {
				raf.raf.write(buf);
				System.err.println("Cache path Name: " + cacheLRU.getMap().get(raf.pathName).cachePathName);
				cacheLRU.getMap().get(raf.pathName).modified.set(true);
				System.err.println("Change cache modifed ?: " +  cacheLRU.getMap().get(raf.pathName).modified);
			} catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			return buf.length;
		}

		//EBADF,ENINVAL,EISDIR
		public synchronized long read( int fd, byte[] buf ) {
			System.err.println("------read op------");

			FileInfo raf = fd2Raf.get(fd);			
			if (raf == null) {
				return Errors.EBADF;
			}
			//System.err.println(raf.file.getName());
			if (raf.file.isDirectory()) {
				return Errors.EISDIR;
			}
			if (!raf.file.canRead()) {
				return Errors.EINVAL;
			}
			int ret = -1;
			try {
				ret = raf.raf.read(buf);
				if (ret == -1) {
					ret = 0;
				}
			} catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			return ret;
		}

		public synchronized long lseek( int fd, long pos, LseekOption o ) {
			System.err.println("------lseek op------");

			FileInfo raf = fd2Raf.get(fd);
			long offset = -1;
			if (raf == null) {
				return Errors.EBADF;
			}
			switch(o) {
				case FROM_CURRENT:
					try {
						raf.raf.seek(raf.raf.getFilePointer() + pos);
						offset = raf.raf.getFilePointer() + pos;
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				case FROM_END:
					try {
						raf.raf.seek(raf.raf.length() - pos);
						offset = raf.raf.length() - pos;
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				case FROM_START:					
					try {
						raf.raf.seek(pos);
						offset = pos;
					} catch (IOException e) {
						e.printStackTrace();
					}
					break;
				default:
					return Errors.EINVAL;
			}
			return offset;
		}

		public synchronized int unlink( String path ) {
			System.err.println("------unlink op------");
			path = simplifyPath(path);
			File f = new File(path);
			if (f.isDirectory()) {
				return Errors.EISDIR;
			}
			if (!f.isFile()) {
				return Errors.ENOENT;
			}
			for (Entry<Integer, FileInfo> c : fd2Raf.entrySet()) {
				if (c.getValue().file.equals(f)) {
					fd2Raf.remove(c.getKey());
				}
			}

			boolean cDelete = cacheLRU.delete(path);
			boolean sDelete = true;
			if (cDelete) {
				try {
					sDelete = server.rmFile(path);					
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			if (!(cDelete && sDelete)) {
				return Errors.ENOENT;
			}
			if (f.exists()) {
				return -1;
			}
			return 0;
		}

		public void clientdone() {
			System.err.println("------clientdone op------");

			//close all files recylce
			if (!fd2Raf.isEmpty()) {
				for (Entry<Integer,FileInfo> c : fd2Raf.entrySet()) {
					try {
						if (c.getValue().raf != null) {
							c.getValue().raf.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					//Reuse fds
					fd2Raf.remove(c.getKey());
				}
			}
			return;
		}

	}
	
	private static class FileHandlingFactory implements FileHandlingMaking {
		public FileHandling newclient() {
			FileHandler fh = new FileHandler();
			fh.fd2Raf = new ConcurrentHashMap<Integer,FileInfo>();
			fh.connect2Server();
			fh.cacheAddedDir = "";
			fh.fileName = "";
			return fh;
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 4) {
			System.err.println("Not enough arguments!");
		}
		System.err.println("Proxy args "+args[0]+args[1]+args[2]+args[3]);	
		//Get Args for proxy
		Proxy.serverip = args[0];
		Proxy.port = Integer.parseInt(args[1]);
		Proxy.cachedir = args[2];
		Proxy.cachesize = Integer.parseInt(args[3]); 
		Proxy.cacheLRU = new LRU(cachesize);
		//File handling
		(new RPCreceiver(new FileHandlingFactory())).run();
		
	
	}
}

