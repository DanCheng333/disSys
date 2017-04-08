import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Lu on 3/21/16.
 */

public interface IServer extends Remote {
    public Server.Role getRole(Integer vmID) throws RemoteException;
    public Cloud.FrontEndOps.Request getRequest() throws RemoteException;
    public void addRequest(Cloud.FrontEndOps.Request r) throws RemoteException;
    public void shutDownVM(Integer vmId, Server.Role role) throws RemoteException;
    public void startF() throws RemoteException;
    public void startM() throws RemoteException;
	public ConcurrentHashMap<String, String> getCacheMap() throws RemoteException;
	public void cacheAdd(String key, String val) throws RemoteException;
}
