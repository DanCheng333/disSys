import java.io.*;
import java.util.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;


public interface IServer extends Remote {
    // Role type definition
    public enum Role {
        FRONT, MIDDLE, NONE
    }
    
    // Method declarations
    public String getName() throws RemoteException;
    public Cloud.FrontEndOps.Request getGlobalNextRequest() throws RemoteException;
    public void addRequestToGlobalQueue(Cloud.FrontEndOps.Request r) throws RemoteException;
    public void startFrontVM() throws RemoteException;
    public void startMiddleVM() throws RemoteException;
    public void addVMName(String name) throws RemoteException;
    public void removeVMName(String name) throws RemoteException;
    public int getMiddleVMNum() throws RemoteException;
    public void shutdownVM () throws RemoteException;
    public int getGlobalRequestQueueLength() throws RemoteException;
    public void adjustFrontMiddleRatio() throws RemoteException;
    public boolean  inFrontServers(String name) throws RemoteException;
    public boolean inMiddleServers(String name) throws RemoteException;
    
}