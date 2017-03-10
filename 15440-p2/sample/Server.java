import java.rmi.registry.LocateRegistry;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends UnicastRemoteObject implements IServer {
	public int port;
	public String rootdir;
	public int clientID;
	public ConcurrentHashMap<String, Integer> VersionMap;

	public Server(int p, String rt) throws RemoteException {
		this.port = p;
		this.rootdir = rt;

	}

	public String sayHello() {
		return "Hello, world!";
	}

	@Override
	public byte[] downloadFile(String path) throws RemoteException {
		File f = new File(path);
		int len = (int) f.length();
		System.err.println("file length " + len);
		byte buffer[] = new byte[len];
		try {
			String serverFilePath = this.rootdir + String.format("/%s", path);
			System.err.println("Server File Path " + serverFilePath);
			BufferedInputStream input = new BufferedInputStream(new FileInputStream(serverFilePath));
			input.read(buffer, 0, len);
			input.close();
		} catch (Exception e) {
			System.err.println("Server Failed to read file");
			e.printStackTrace();
		}
		return buffer;
	}
	@Override
	public void updateVersionNum(String path) {
		if (VersionMap.containsKey(path)) {
			int newVer = VersionMap.get(path)+1;
			System.err.println("Update version in server");
			System.err.println("Server versio now:"+ newVer);
			VersionMap.replace(path, newVer); // Inc version Num
		} else { // not in VersionMap.
			System.err.println("Not in version map should never happen");
			VersionMap.put(path, 0);
		} // What happen if cacheGet evicted?
	}
	
	@Override
	public boolean uploadFile(String path, byte[] buffer, int start, int len) throws RemoteException {		
		String serverFilePath = this.rootdir + String.format("/%s", path);		
		File serverFile = new File(serverFilePath);
		try {
			RandomAccessFile serverRaf = new RandomAccessFile(serverFile, "rw");
			serverRaf.seek(start);
			serverRaf.write(buffer, 0, len);
			serverRaf.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return true;

	}

	@Override
	public int getVersionNum(String path) {
		return VersionMap.get(path);
	}

	@Override
	public void initVersionNum(String path) {
		VersionMap.put(path, 0);
	}

	@Override
	public int getClientID() {
		return this.clientID;
	}
	
	@Override
	public void setClientID(int id) {
		this.clientID = id;
	}
	
	@Override
	public boolean rmFile(String path) {
		if (VersionMap.containsKey(path))  {
			VersionMap.remove(path);
		}
		String serverFilePath = this.rootdir + String.format("/%s", path);
		System.err.print("...Call remove file path in server :"+serverFilePath);
		File file = new File(serverFilePath);
		if (!file.exists()) {
			System.err.print("This file do not exist");
			return false;
		}
		if(!file.delete()) {
			System.err.println("delete fail");
			return false;
		}
		return true;
		
	}
	
	public static void main(String args[]) {
		if (args.length < 2) {
			System.err.println("Not enough args for Server");
		}
		System.err.println("Server args " + args[0] + args[1]);
		int port = Integer.parseInt(args[0]);

		try {
			LocateRegistry.createRegistry(port); // port
		} catch (RemoteException e) {
			System.err.println("Failed to create the RMI registry " + e);
		}

		try {
			Server server = new Server(port, args[1]);
			server.clientID = 0;
			server.VersionMap = new ConcurrentHashMap<String, Integer>();
			try {
				Naming.rebind(String.format("//127.0.0.1:%d/Server", port), server);
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		} catch (RemoteException e) {
			e.printStackTrace();
			System.err.println("Failed to create server");
		}
	}

}