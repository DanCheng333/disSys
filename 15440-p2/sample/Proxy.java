/* Sample skeleton for proxy */

import java.io.*;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

class FileInfo{
	File file;
	RandomAccessFile raf;
	public FileInfo(File f, RandomAccessFile r) {
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
	public static ConcurrentHashMap<String,String> cacheMap;
	
	public static final int MAXFDSIZE = 1000;
	
	private static class FileHandler implements FileHandling {
		ConcurrentHashMap<Integer,FileInfo> fd2Raf;
		
		public synchronized int open( String path, OpenOption o ) {
			try {
				String url = String.format("//127.0.0.1:%d/Server", Proxy.port);
				System.err.println("url is "+url);
				try {
					Proxy.server = (IServer)Naming.lookup (url);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.err.println("CLient call hello");
				try {
					System.err.println(Proxy.server.sayHello());
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (NotBoundException e) {
				System.err.println("Proxy fails to create server");
			}
			
			
			
			int fd = fd2Raf.size()+1;
			String newPath = path;
			//HIT, get file from cache
			if(cache.contains(path)) {
				System.err.print("Hit!");
				newPath = cacheMap.get(path);
			}
			//MISS, download from server, put in cache
			else {
				newPath = Proxy.cachedir + "/"+String.valueOf(fd)+".txt";
				cache.add(path);
				cacheMap.put(path, newPath);
				System.err.print("file:"+path+", new path for this cache: "+newPath);
				
				//Create a newFile and write to it.
				File f = new File(newPath);
				if (!f.exists()) {
					System.err.print("create a new file called "+newPath);
					try {
						f.createNewFile();
					} catch (IOException e) {
						System.err.print("failed to create new file"); 
						e.printStackTrace();
					}
				}
				BufferedOutputStream outputFile;
				try {
					outputFile = new BufferedOutputStream(new FileOutputStream(newPath));
					byte data[] = server.downloadFile(path);
					outputFile.write(data, 0, data.length);
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
						FileInfo fi_c = new FileInfo(f,raf_c);
						fd2Raf.put(fd,fi_c);
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
						FileInfo fi_cn = new FileInfo(f,raf_cn);
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
					FileInfo fi_r = new FileInfo(f,raf_r);
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
						FileInfo fi_w = new FileInfo(f,raf_w);
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
			System.err.println("bad fd");
			
			if (raf == null) {
				return Errors.EBADF;
			}
			System.err.println(raf.file.getName());
			System.err.println("dir");

			if (raf.file.isDirectory()) {
				return Errors.EISDIR;
			}
			System.err.println("eninval");
			if (!raf.file.canRead()) {
				return Errors.EINVAL;
			}
			int ret = -1;
			try {
				ret = raf.raf.read(buf);
				System.err.println("ret is");
				System.err.println(ret);
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
		Proxy.cacheMap = new ConcurrentHashMap<String, String>();
		//File handling
		(new RPCreceiver(new FileHandlingFactory())).run();
		
	
	}
}

