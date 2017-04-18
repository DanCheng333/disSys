import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {
    String sayHello() throws RemoteException;
    byte[] downloadFile(String path) throws RemoteException;
    boolean uploadFile(String path, byte[] buffer) throws RemoteException;
    void initVersionNum(String path) throws RemoteException;
    int getVersionNum(String path) throws RemoteException;
    int getClientID() throws RemoteException;
    void setClientID(int id) throws RemoteException;
    boolean rmFile(String path) throws RemoteException;
}