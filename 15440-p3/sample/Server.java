//Check Point 2 Final Version
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.AlreadyBoundException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.Hashtable;
import java.util.Arrays;
import java.util.Date;
import java.rmi.Naming;
import java.rmi.Remote;

class Properties {
    boolean isFrontTier = false;
    boolean isMaster = false;
    int myID = 0;
    Date date;
    //Timestamp timeStamp;
    long lastProcessTime;
}

public class Server extends UnicastRemoteObject implements IServer{
    public static String [] rmiRegistryList; // 100 total registry most
    public static LinkedList<Cloud.FrontEndOps.Request> requestQueue;
    public static Hashtable<Integer, Boolean> id_roleTable = new Hashtable<Integer, Boolean>();
    public Server() throws RemoteException {}

    private static Properties vmProp = new Properties();
    private static ServerLib SL;
    private static int frontNumb = 0;
    private static int midNumb = 0;
    private static boolean lackFront = false;

    // java RMI
    public static IServer getInstance(String ip, int port, String name) throws RemoteException {
	String url = String.format("//%s:%d/%s", ip, port, name);
	try{
	    return (IServer) Naming.lookup (url);
	} catch (Exception e) {
	    System.err.println(e);
	    return null;
	}
    }

    public static boolean registMaster(String ip, int port) throws RemoteException {
	Server server = null;
	frontNumb += 1;
	requestQueue = new LinkedList<Cloud.FrontEndOps.Request>();
	rmiRegistryList = new String[100];
	try{
	    server = new Server();
	}
	catch(RemoteException e) {
	    System.err.println("Failed to create server " + e);
	    System.exit(1);
	}

	try {
	    Naming.bind(String.format("//%s:%d/%s", ip, port, "Master"), server);
	    System.err.println("I'm master node");
	    return true;
	}
	catch (AlreadyBoundException e) {
	    System.err.println(e);
	    return false;
	}
	catch (RemoteException e) { 
	    System.err.println(e);
	    return false;
	}
	catch (MalformedURLException e) { 
	    System.err.println(e);
	    return false;
	}
    }

    public static boolean registFrontTier(String ip, int port, int id) throws RemoteException {
	Server server = null;
	vmProp.isFrontTier = true;
	try{
	    server = new Server();
	}
	catch(RemoteException e) {
	    System.err.println("Failed to create server " + e);
	    System.exit(1);
	}

	try {
	    Naming.bind(String.format("//%s:%d/%s", ip, port, "FrontTier" + id), server);
	    System.err.println("I'm FrontTier node " + id);
	    return true;
	}
	catch (AlreadyBoundException e) {
	    System.err.println(e);
	    return false;
	}
	catch (RemoteException e) { 
	    System.err.println(e);
	    return false;
	}
	catch (MalformedURLException e) { 
	    System.err.println(e);
	    return false;
	}
    }

    public static boolean registMidTier(String ip, int port, int id) throws RemoteException {
	Server server = null;
	vmProp.isFrontTier = false;
	try{
	    server = new Server();
	}
	catch(RemoteException e) {
	    System.err.println("Failed to create server " + e);
	    System.exit(1);
	}

	try {
	    Naming.bind(String.format("//%s:%d/%s", ip, port, "MidTier" + id), server);
	    System.err.println("I'm MidTire node " + id);
	    return true;
	}
	catch (AlreadyBoundException e) {
	    System.err.println(e);
	    return false;
	}
	catch (RemoteException e) { 
	    System.err.println(e);
	    return false;
	}
	catch (MalformedURLException e) { 
	    System.err.println(e);
	    return false;
	}
    }

    // master operation
    public synchronized Cloud.FrontEndOps.Request pollRequest()
      throws RemoteException{
	  return requestQueue.poll();
      }

    public synchronized void addRequest(Cloud.FrontEndOps.Request r) 
      throws RemoteException{
	  requestQueue.add(r);
      }

    public static synchronized void masterAddRequest(Cloud.FrontEndOps.Request r) {
	requestQueue.add(r);
    }

    public Cloud.FrontEndOps.Request peekRequest() 
      throws RemoteException{
	  return requestQueue.peek();
      }

    public int getRequestLength()
      throws RemoteException{
	  return requestQueue.size();
      }

    public boolean assignTier() {
	return lackFront;
    }

    public int getVMNumber(boolean b) {
	if(b) return frontNumb;
	else return midNumb;
    }

    public synchronized int addVM(int id, boolean b) throws RemoteException{
	if(id_roleTable.get(id) == null) {
	    id_roleTable.put(id, b);
	    if(b) frontNumb += 1;
	    else midNumb += 1;
	    return getID(); 
	}else {
	    return -1; // Already exists
	}
    }

    // tier operation/inspection
    public int getID() throws RemoteException {
	return id_roleTable.size();
    }

    public int getRequestQueueLength() throws RemoteException {
	return requestQueue.size();
    }

    public static void getRmiList(String ip, int port) throws RemoteException {
	try {
	    String [] tmp = Naming.list("//" + ip + ":" + port);
	    for(int i =0; i < tmp.length; i++)
	      System.err.println(tmp[i]);
	}catch (Exception e){
	    System.err.println(e);
	}
    }

    public static void shutDownVM(int id, boolean isFront, String ip, int port)
      throws RemoteException {
	  IServer inst = null;
	  if(isFront) inst = getInstance(ip, port, "FrontTier" + id);
	  else inst = getInstance(ip, port, "MidTier" + id);

	  // do clean shutdown
	  ServerLib SLinst = inst.getSL();
	  SLinst.interruptGetNext();
	  SLinst.shutDown();
	  UnicastRemoteObject.unexportObject(inst, true);
	  System.exit(0);
      }

    public ServerLib getSL() {
	return SL;
    }

    public static void main ( String args[] ) throws Exception {
	if (args.length != 2) throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");

	SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
	int port = Integer.parseInt(args[1]);
	String ip = args[0];
	IServer master = null;
	// time stamp
	vmProp.date = new Date();
	vmProp.lastProcessTime = vmProp.date.getTime();

	if((vmProp.isMaster = registMaster(ip, port)) == false) {
	    master = getInstance(ip, port, "Master");
	    vmProp.isFrontTier = master.assignTier();
	    vmProp.myID = master.getID();
	    master.addVM(vmProp.myID, vmProp.isFrontTier);
	    if(vmProp.isFrontTier == false) {
		// handle request // mid tier
		registMidTier(ip, port, vmProp.myID);
	    }else {
		// register
		registFrontTier(ip, port, vmProp.myID);
		SL.register_frontend();
		//System.err.println(SL.getQueueLength());
	    }
	}else {
	    SL.register_frontend(); // Regist Master
	    SL.startVM(); // create the first mid tier
	    midNumb += 1;
	}

	//getRmiList(ip, port);

	// main loop
	while (true) {
	    //queue len should < numb_fonrt and request queue shoud < numb_mid
	    if(vmProp.isMaster) {
		// init drop
		Cloud.FrontEndOps.Request r = SL.getNextRequest();
		if(SL.getStatusVM(2) == Cloud.CloudOps.VMStatus.Booting) {
		    SL.drop(r);
		}else {
		    masterAddRequest(r);
		}
		// measure current traffic
		int deltaFront = SL.getQueueLength() - frontNumb;
		int deltaMid = requestQueue.size() - midNumb;
		if(deltaFront > 0 || deltaMid > 0) {
		    //lackFront = deltaFront > deltaMid ? true : false;
		    //int tmp = deltaFront > deltaMid ? deltaFront : deltaMid;
		    for(int i = 0; i < deltaFront; i++) {
			lackFront = true;
			if(SL.getStatusVM(id_roleTable.size() + i + 2) == 
			   Cloud.CloudOps.VMStatus.NonExistent){
			    SL.startVM();
			}
		    }
		    for(int i = 0; i < deltaMid; i++) {
			lackFront = false;
			if(SL.getStatusVM(id_roleTable.size() + i + 2) == 
			   Cloud.CloudOps.VMStatus.NonExistent){
			    SL.startVM();
			}
		    }
		}
	    }else if(vmProp.isFrontTier) { //TODO drop when cannot handle
	//	System.err.println("r len : " + master.getRequestLength());
	//	System.err.println("queue len : " + SL.getQueueLength());
	//	System.err.println("m len : " + master.getVMNumber(false));
	//	while(master.getRequestLength() - master.getVMNumber(false)>=-1
	//	      //&& SL.getStatusVM(master.getID() + 2) ==
	//	      //Cloud.CloudOps.VMStatus.Booting)
	//	  )
	//	  { 
	//	    //System.err.println("drop head");
	//	    SL.dropHead(); 
	//	  }
		Cloud.FrontEndOps.Request r = SL.getNextRequest();
		vmProp.date = new Date();
		if(vmProp.date.getTime() - vmProp.lastProcessTime < 7000) {
		    master.addRequest(r);
		    vmProp.lastProcessTime = vmProp.date.getTime();
		}else {
		    shutDownVM(vmProp.myID, true, ip, port);
		}
	    }else if(!vmProp.isFrontTier) {
		if (vmProp.date.getTime() - vmProp.lastProcessTime < 7000) {
		    if(master.getRequestLength() != 0) {
			Cloud.FrontEndOps.Request r = master.pollRequest();
			if(master.getRequestLength() - master.getVMNumber(false) > 0
			      && SL.getStatusVM(master.getID() + 2) ==
			      Cloud.CloudOps.VMStatus.Booting) {
			    //System.err.println("drop r");
			    SL.drop(r);
			}else {
			    SL.processRequest(r);
			}
			vmProp.date = new Date();
			vmProp.lastProcessTime = vmProp.date.getTime();
		    }
		}else {
		    shutDownVM(vmProp.myID, false, ip, port);
		}
	    }
	}
    }
}