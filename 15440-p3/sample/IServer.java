import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {
	
	public Server.Role getRole(Integer vmID) throws RemoteException;
	public Cloud.FrontEndOps.Request getRequest() throws RemoteException;
    public void addRequest(Cloud.FrontEndOps.Request r) throws RemoteException;
    public void shutDownVM(Integer vmId, Server.Role role) throws RemoteException;
    public void kill() throws RemoteException;
}