import java.rmi.Remote;
import java.rmi.RemoteException;

public class RemoteInt {

	public interface ServerI extends Remote {
		public String sayHello() throws RemoteException;
	}

}