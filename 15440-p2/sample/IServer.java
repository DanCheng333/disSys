import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {
    String sayHello() throws RemoteException;
    byte[] downloadFile(String path) throws RemoteException;
    boolean uploadFile(String path, byte[] buffer) throws RemoteException;
}