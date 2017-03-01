import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
        
public class Server extends UnicastRemoteObject implements Remote {
    public int port;
    public String rootdir;
    public Server(int p, String rt) throws RemoteException{
    	super(0);
    	this.port=p;
    	this.rootdir=rt;
    	
    }

    public String sayHello() {
        return "Hello, world!";
    }
        
    public static void main(String args[]) {
    	if (args.length < 2) {
    		System.err.println("Not enough args for Server");
    	}
    	System.err.println("Server args "+args[0]+args[1]);
        int port = Integer.parseInt(args[0]);
    	try { // create registry if it doesn’t exist 
    		LocateRegistry.createRegistry(port); // port
    	} catch (RemoteException e) {
    	}
    	
    	Server server = null;
		try {
			server = new Server(port,args[1]);
		} catch (RemoteException e) {
			e.printStackTrace();
		} 
    	try {
			Naming.rebind("//localhost/Server", server);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
    }
}