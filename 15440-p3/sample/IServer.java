import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author danc
 *
 */
public interface IServer extends Remote {
	public Server.Role getRole(Integer vmID) throws RemoteException;

	public Cloud.FrontEndOps.Request getRequest() throws RemoteException;

	public void addRequest(Cloud.FrontEndOps.Request r) throws RemoteException;

	public void startF() throws RemoteException;

	public void startM() throws RemoteException;

	// Cache ops
	public ConcurrentHashMap<String, String> getCacheMap() throws RemoteException;

	public void cacheAdd(String key, String val) throws RemoteException;
}
