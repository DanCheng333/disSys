/* Sample skeleton for proxy */

import java.io.*;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

class CacheInfo {
	String cachePathName;
	int versionNum;
	boolean modified;
	public CacheInfo(String n, int ver, boolean modified) {
		this.cachePathName = n;
		this.versionNum = ver;
	}
}

class FileInfo{
	String pathName;
	String cachePathName;
	File file;
	RandomAccessFile raf;
	public FileInfo(String path, String cachePath, File f, RandomAccessFile r) {
		this.pathName = path;
		this.cachePathName = cachePath;
		this.file=f;
		this.raf=r;
	}
}


class Proxy {
	public static String serverip;
	public static int port;
	public static String cachedir;
	public static int cachesize;
	public static IServer server;
	
	public static LinkedList<String> cache;
	public static ConcurrentHashMap<String,CacheInfo> cacheMap;
	
	public static final int MAXFDSIZE = 1000;
	
	private static class FileHandler implements FileHandling {
		ConcurrentHashMap<Integer,FileInfo> fd2Raf;
		
		public synchronized void connect2Server() {
			/* Connect to server */
			try {
				String url = String.format("//127.0.0.1:%d/Server", Proxy.port);
				System.err.println("Try to connect to server .... url is "+url);
				try {
					Proxy.server = (IServer)Naming.lookup (url);
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
		
		public synchronized void getFileFromServer(String path, String cachePath) {
			BufferedOutputStream outputFile;
			try {
				outputFile = new BufferedOutputStream(new FileOutputStream(cachePath));
				byte data[] = server.downloadFile(path);
				System.err.print("datalength " + String.valueOf(data.length));
				outputFile.write(data, 0, data.length);
				// rewrite everything?
				outputFile.flush();
				outputFile.close();
				System.err.print("Finish write to cachefile");
			} catch (FileNotFoundException e) {
				System.err.print("Failed to create a cachefile");
				e.printStackTrace();
			} catch (IOException e) {
				System.err.print("File to write,flush or close");
				e.printStackTrace();
			}
		}
		
		public synchronized int open( String path, OpenOption o ) {
			System.err.println("Call open");	
			
			int fd = fd2Raf.size()+1;
			String cachePath = Proxy.cachedir + "/"+String.valueOf(fd)+".txt";
			int cacheVersion = 0;
			int serverVersion = 0;
			File f;
			
			//HIT, get file from cache
			if(cache.contains(path)) { //File in cache
				System.err.println("Hit!");
				cacheVersion = cacheMap.get(path).versionNum;
				cachePath = cacheMap.get(path).cachePathName;
				cacheMap.get(path).modified = false; //open is not modifying
				try {
					serverVersion = server.getVersionNum(path);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.err.println("Cache Version"+cacheVersion);
				System.err.println("Server Version" + serverVersion);
				System.err.println("cahcePath in Hit:"+cachePath);
				if (cacheVersion < serverVersion) { 
					//Cache Version Does not Match Server Version
					getFileFromServer(path,cachePath);
					cacheMap.get(path).versionNum = serverVersion;
					System.err.println("Version Unmatched! :(");									
				}
				f = new File(cachePath);				
			}
			//MISS, download from server, put in cache
			else {
				System.err.println("Miss, cacheVersionNum " + cacheVersion);
				cache.add(path);
				System.err.println("cahcePath in Miss"+cachePath);
				CacheInfo cInfo = new CacheInfo(cachePath,cacheVersion,false);
				cacheMap.put(path, cInfo);
				System.err.println("file:"+path+", new path for this cache: "+cachePath);
				
				try {
					server.initVersionNum(path);
				} catch (RemoteException e1) {
					e1.printStackTrace();
				}
				getFileFromServer(path,cachePath);
				
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
			
	
			if (fd > MAXFDSIZE) {
				return Errors.EMFILE;
			}
			switch(o) {
				case CREATE:
					System.err.println("CREATE");
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
						FileInfo fi_c = new FileInfo(path,cachePath,f,raf_c);
						fd2Raf.put(fd,fi_c);
						System.err.println("put in track");
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
								
					break;
				case CREATE_NEW:
					System.err.println("CREATE_NEW");
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
						FileInfo fi_cn = new FileInfo(path,cachePath,f,raf_cn);
						fd2Raf.put(fd,fi_cn);
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
					break;
				case READ:
					System.err.println("READ");
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
					FileInfo fi_r = new FileInfo(path,cachePath,f,raf_r);
					fd2Raf.put(fd,fi_r);
					break;
				case WRITE:
					System.err.println("WRITE");
					if (f.isDirectory()) {
						return Errors.EISDIR;
					}
					if (!f.exists()) {
						return Errors.ENOENT;
					}
					
					try {
						RandomAccessFile raf_w = new RandomAccessFile(f,"rw");
						FileInfo fi_w = new FileInfo(path,cachePath,f,raf_w);
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
			System.err.println("close op");
			FileInfo raf = fd2Raf.get(fd);
			if(raf == null) {
				return Errors.EBADF;
			}
			
			//Upload cache file to server original file
			if (cacheMap.get(raf.pathName).modified) {
	            int len = (int) raf.file.length();
	            String path = raf.pathName;
	            System.err.println("Closing this path in server"+ path+"  file length "+len);
	            String cachePath = cacheMap.get(path).cachePathName;
	            System.err.println("Cache file path : "+cachePath);
	            
	            //update files
	            byte buffer[] = new byte[len];
	            try {
	                raf.raf.read(buffer, 0, len);
	            } catch (IOException e1) {
	                System.err.println("read cache content failed");
	                e1.printStackTrace();
	            }
	            try {
	                server.uploadFile(path, buffer);
	                System.err.println("Cache ver:"+cacheMap.get(path).versionNum);
	                System.err.println("Server ver:"+server.getVersionNum(path));
	            } catch (RemoteException e1) {
	                System.err.println("upload files failed");
	                e1.printStackTrace();
	            }
			}
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
			System.err.println("write op");

			FileInfo raf = fd2Raf.get(fd);
			if(raf == null) {
				return Errors.EBADF;
			}
			if (!raf.file.canWrite()) {
				return Errors.EINVAL;
			}
			try {
				raf.raf.write(buf);
				System.err.println("Cache path Name: " + cacheMap.get(raf.pathName).cachePathName);
				cacheMap.get(raf.pathName).modified = true;
				System.err.println("Change cache modifed ?: " +  cacheMap.get(raf.pathName).modified);
			} catch (IOException e) {
				e.printStackTrace();
				return Errors.EBADF;
			}
			return buf.length;
		}

		//EBADF,ENINVAL,EISDIR
		public synchronized long read( int fd, byte[] buf ) {
			System.err.println("read op");

			FileInfo raf = fd2Raf.get(fd);			
			if (raf == null) {
				return Errors.EBADF;
			}
			//System.err.println(raf.file.getName());
			if (raf.file.isDirectory()) {
				return Errors.EISDIR;
			}
			//System.err.println("eninval");
			if (!raf.file.canRead()) {
				return Errors.EINVAL;
			}
			int ret = -1;
			try {
				ret = raf.raf.read(buf);
				//System.err.println("ret is");
				//System.err.println(ret);
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
			System.err.println("lseek op");

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
			System.err.println("unlink op");

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
			f.delete();
			if (f.exists()) {
				return -1;
			}
			return 0;
		}

		public void clientdone() {
			System.err.println("clientdone op");

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
		Proxy.cache = new LinkedList<String>();
		Proxy.cacheMap = new ConcurrentHashMap<String, CacheInfo>();
		//File handling
		(new RPCreceiver(new FileHandlingFactory())).run();
		
	
	}
}

