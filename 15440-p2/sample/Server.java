import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
        
public class Server extends UnicastRemoteObject implements IServer {
    public int port;
    public String rootdir;
    public Server(int p, String rt) throws RemoteException{
    	this.port=p;
    	this.rootdir=rt;
    	
    }

    public String sayHello() {
        return "Hello, world!";
    }

	@Override
	public byte[] downloadFile(String path) throws RemoteException {
		File f = new File(path);
		int len = (int) f.length();
		System.err.println("file length "+len);
		byte buffer[] = new byte[len];
		try {
			String serverFilePath = this.rootdir+String.format("/%s",path);
			System.err.println("Server File Path "+ serverFilePath);
			BufferedInputStream input = new BufferedInputStream(new FileInputStream(serverFilePath));
			input.read(buffer, 0, len);
			input.close();
		}catch(Exception e) {
			System.err.println("Server Failed to read file");
			e.printStackTrace();
		}
		return buffer;
	}


	@Override
	public boolean uploadFile(String path, byte[] buffer) throws RemoteException {
		String serverFilePath = this.rootdir+String.format("/%s",path);
		try {
			System.err.println("uploadFile Path: " + serverFilePath);
			File file = new File(serverFilePath);
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.write(buffer, 0, buffer.length);
			raf.close();
		}catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}


    public static void main(String args[]) {
    	if (args.length < 2) {
    		System.err.println("Not enough args for Server");
    	}
    	System.err.println("Server args "+args[0]+args[1]);
        int port = Integer.parseInt(args[0]);
        try {
			System.err.println("IP address"+InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	try { 
    		LocateRegistry.createRegistry(port); // port
    	} catch (RemoteException e) {
    		System.err.println("Failed to create the RMI registry " + e);
    	}
    	
    	Server server = null;
		try {
			server = new Server(port,args[1]);
		} catch (RemoteException e) {
			System.err.println("Failed to create server");
		} 
    	try {
			Naming.rebind(String.format("//127.0.0.1:%d/Server", port), server);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
    }



}