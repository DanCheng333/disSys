/*import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {
	 // master operation
    public boolean assignTier() throws RemoteException;
    public Cloud.FrontEndOps.Request pollRequest() throws RemoteException;
    public Cloud.FrontEndOps.Request peekRequest() throws RemoteException;
    public int getRequestLength() throws RemoteException;
    public void addRequest(Cloud.FrontEndOps.Request r) throws RemoteException;

    // master inspect
    public int addVM(int i, boolean b) throws RemoteException;
    public int getID() throws RemoteException;
    public int getVMNumber(boolean b) throws RemoteException;
    public int getRequestQueueLength() throws RemoteException;

    // other
    public ServerLib getSL() throws RemoteException;
}
*/


import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by Lu on 3/21/16.
 */

public interface IServer extends Remote {
    public Content getRole(Integer vmID) throws RemoteException;
    public Cloud.FrontEndOps.Request getFromCentralizedQueue() throws RemoteException;
    public void addToCentralizedQueue(Cloud.FrontEndOps.Request r) throws RemoteException;
    public void killMe(Integer vmId, boolean type) throws RemoteException;
    public void killYourself() throws RemoteException;

}