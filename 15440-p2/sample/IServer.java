import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {
    String sayHello() throws RemoteException;
    byte[] downloadFile(String path) throws RemoteException;
    byte[] uploadFile(String path, byte[] buffer, int versionNum) throws RemoteException;
    void initVersionNum(String path) throws RemoteException;
    int getVersionNum(String path) throws RemoteException;
}