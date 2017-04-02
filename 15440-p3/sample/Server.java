import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server extends UnicastRemoteObject implements IServer {
	public static final int MASTER = 1;
	
	private static final long SCALE_OUT_APP_THRESHOLD = 60000;
    private static final long SCALE_OUT_FOR_THRESHOLD = 60000;
    private static final long SCALE_In_THRESHOLD = 60000;
    private static final long MAX_FORWARDER_NUM = 1;
    private static final long MAX_APP_NUM = 12;
    private static long interval = 1000;
    private static int startNum = 1;
    private static int startForNum = 0;
    private static long lastScaleOutAppTime;
    private static long lastScaleOutForTime;
    private static long lastScaleInTime;
	
	public static String cloud_ip;
	public static int cloud_port;
	public static int vmID;
	public static ServerLib SL;
	
	public static IServer masterServer;	
	private static List<Integer> frontServerList;
    private static List<Integer> middleServerList;
	public static ConcurrentLinkedQueue <Cloud.FrontEndOps.Request> requestQueue;
	
    private static boolean kill = false;
    private static boolean unregister = false;
	public enum Role {
	    FRONT, MIDDLE, NONE
	}
	
	public Server() throws RemoteException {
		this.frontServerList = Collections.synchronizedList(new ArrayList<>());
    	this.middleServerList = Collections.synchronizedList(new ArrayList<>());
	}
	
	
	public static void scaleOutApp(int number){
        int originalNum = number;
        number = Math.min(number, 7);
        if (middleServerList.size() < MAX_APP_NUM && System.currentTimeMillis() - lastScaleOutAppTime >= SCALE_OUT_APP_THRESHOLD) {
            System.out.println("original number:" + originalNum);
            System.out.println("scaleOutApp:" + number);
            for (int i = 0; i < Math.min(number, MAX_APP_NUM - middleServerList.size()); ++i) {
                SL.startVM();
            }
            lastScaleOutAppTime = System.currentTimeMillis();
        }
    }


    public static void scaleOutFor(int number){
        if (frontServerList.size() < MAX_FORWARDER_NUM && System.currentTimeMillis() - lastScaleOutForTime >= SCALE_OUT_FOR_THRESHOLD) {
            System.out.println("scale out for");
            frontServerList.add(SL.startVM());
            lastScaleOutForTime = System.currentTimeMillis();
        }
    }
    
    public static boolean scaleIn(int appNumber, int forNumber) throws Exception{
        appNumber = Math.min(middleServerList.size()-1, appNumber);
        forNumber = Math.min(frontServerList.size(), forNumber);
        if (System.currentTimeMillis() - lastScaleInTime >= SCALE_In_THRESHOLD) {
            // scale in app
            for (int i = 0; i < appNumber; ++i){
                int vmId = middleServerList.remove(middleServerList.size()-1);
                System.out.println("endApp:"+vmId);
                IServer curServer = (IServer) LocateRegistry.getRegistry(cloud_ip, cloud_port).lookup("//localhost/no"+String.valueOf(vmId));
                curServer.kill();
            }

            for (int i = 0; i < forNumber; ++i){
                int vmId = frontServerList.remove(0);
                System.out.println("endFor:"+vmId);
                IServer curServer = (IServer) LocateRegistry.getRegistry(cloud_ip, cloud_port).lookup("//localhost/no"+String.valueOf(vmId));
                curServer.kill();
            }
            lastScaleInTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }
	public static void masterAction() throws Exception {
		/*SL.startVM();
    	SL.register_frontend();
    	
    	requestQueue = new ConcurrentLinkedQueue <Cloud.FrontEndOps.Request>();*/
		SL.startVM();
        frontServerList = Collections.synchronizedList(new ArrayList<>());
        middleServerList = Collections.synchronizedList(new ArrayList<>());
        //DB = SL.getDB();
        //cache = new ConcurrentHashMap<>(512);
        requestQueue = new ConcurrentLinkedQueue<>();

        SL.register_frontend();
        while(SL.getQueueLength() == 0 );
        long time1 = System.currentTimeMillis();
        SL.dropHead();
        while(SL.getQueueLength() == 0 );
        long time2 = System.currentTimeMillis();
        interval = time2 - time1;
        System.out.println("time2-time1:" + interval);

        if (interval < 130) {
            startNum = 7;
            startForNum = 1;
        } else if (interval < 200) {
            startNum = 6;
            startForNum = 1;
        } else if (interval < 650) {
            startNum = 3;
            startForNum = 0;
        } else {
            startNum = 1;
            startForNum = 0;
        }

        for (int i = 0; i < startNum; ++i) {
            SL.startVM();
        }

        if (interval < 300) {
            lastScaleInTime = 0 ;
            lastScaleOutAppTime = System.currentTimeMillis();
            lastScaleOutForTime = System.currentTimeMillis();
        }else {
            lastScaleInTime = System.currentTimeMillis();
            lastScaleOutAppTime = 0;
            lastScaleOutForTime = 0;
        }

        for (int i = 0; i < startForNum; ++i) {
            frontServerList.add(SL.startVM());
        }

        while( middleServerList.size() == 0){
            SL.dropHead();
            try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

        System.out.println("interval:" + interval + " start:" + startNum + " startFor:" + startForNum);
        Cloud.FrontEndOps.Request r = null;
        while (true) {
                  // consider scaleout
                int queLen = SL.getQueueLength();
                if (queLen > middleServerList.size() * 1.5){
                    scaleOutFor(1);
                    int number = (int)(queLen/middleServerList.size()*4);
                    scaleOutApp(number);             
                }
             // if queue is too long, drop head
                if (requestQueue.size() > middleServerList.size()) {
                    while (requestQueue.size() > middleServerList.size() * 1.5) {
                        SL.drop(requestQueue.poll());
                    }
                } else {
                    // consider scalein
                    long lastTimeGetReq = System.currentTimeMillis();
                    while ((r = SL.getNextRequest()) == null) {
                    }
                    long period = System.currentTimeMillis() - lastTimeGetReq;

                    if (period > interval * 3){
                        int scaleInAppNumber = (int) (period - interval)/40;
                        int scaleInForNumber = scaleInAppNumber > 5 ? 1 : 0;
                        if (scaleIn(scaleInAppNumber, scaleInForNumber)) {
                            interval = period;
                        }
                    }
                    requestQueue.add(r);
                }
	
          }
	}
        
        
	public static void frontTierAction() {
		System.out.println("==========FrontTier===========");
        SL.register_frontend();
        Cloud.FrontEndOps.Request r = null;
        while (true) {
            while ((r = SL.getNextRequest()) == null){}
            try {
				masterServer.addRequest(r);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            // if have to kill self
            if (kill){
                if (unregister == false) {
                    SL.unregister_frontend();
                    unregister = true;
                }
                if (SL.getQueueLength() == 0){
                    try {
						masterServer.shutDownVM(vmID, Role.FRONT);
					
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                    
                }
            }
        }
	}
	
	
	public static void middleTierAction() {
		  System.out.println("==========MiddleTier=========");
          while(true) {
              try {
                  Cloud.FrontEndOps.Request r = masterServer.getRequest();
                  SL.processRequest(r);
                  if (kill){
                      masterServer.shutDownVM(vmID, Role.MIDDLE);
                     
                  }
              } catch (Exception e){
//                  e.printStackTrace();
                  continue;
              }

          }
	}
	
	
    /**
     * FrontTiers(include MASTER), MiddleTiers
     * @param args
     * @throws Exception
     */
	public static void main ( String args[] ) throws Exception {
		if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		cloud_ip = args[0];
        cloud_port = Integer.parseInt(args[1]);
        vmID = Integer.parseInt(args[2]);
        
        System.err.println("cloud_ip:"+cloud_ip+ ", cloud_port"+ cloud_port+", vmID:" + vmID);

		SL = new ServerLib(cloud_ip, cloud_port);
        LocateRegistry.getRegistry(cloud_ip, cloud_port).bind("//localhost/no" + String.valueOf(vmID), new Server());

        if (vmID == MASTER)	{
        	masterAction();   	      	
        }
		
        //Not MASTER
        else{
        	//Look up for master server
        	while (true) {
                try {
                    masterServer = (IServer) LocateRegistry.getRegistry(cloud_ip, cloud_port).lookup("//localhost/no1");
                    break;
                } catch (Exception e) {
                	e.printStackTrace();
                	continue;
                }
            }

            // get role
            Role reply = null;
            try {
                reply = masterServer.getRole(vmID);
            } catch (Exception e){
                return;
            }
            // front
            if (reply == Role.FRONT) {
            	frontTierAction();
            }
            // middle
            else if(reply == Role.MIDDLE){
            	middleTierAction();
            }
            else {
            	masterServer.shutDownVM(vmID, Role.NONE);
            }
	}
}



	@Override
	public Role getRole(Integer vmID) throws RemoteException {
		if( middleServerList.contains(vmID) ) {
            System.out.println(" Middle, ID:"+ vmID);
            middleServerList.add(vmID);
            return Role.MIDDLE;
        }
        else if ( frontServerList.contains(vmID) ){
            System.out.println("Front,ID:"+ vmID);
            return Role.FRONT;
        }
        else {
        	return Role.NONE;
        }
	}



	@Override
	public Cloud.FrontEndOps.Request getRequest() throws RemoteException {
		Cloud.FrontEndOps.Request r = null;
        while (r != null){
            r = requestQueue.poll();
        }
        return r;
	}



	@Override
	public void addRequest(Cloud.FrontEndOps.Request r) throws RemoteException {
		 requestQueue.add(r);		
	}



	@Override
	public void shutDownVM(Integer vmId, Role role) throws RemoteException {
		if (role == Role.FRONT){
            System.out.println("ShutDown frontTier vmID:" + vmId);
            frontServerList.remove(vmId);
            SL.endVM(vmId);
        } else if (role == Role.MIDDLE){
            System.out.println("kill processor:" + vmId);
            middleServerList.remove(vmId);
            SL.endVM(vmId);
        }
        else {
        	SL.endVM(vmId);
        }
		
	}
	@Override
	public void kill() throws RemoteException{
        kill = true;
	}
    
	public synchronized void shutDown() throws RemoteException {
		UnicastRemoteObject.unexportObject(this, true);	
	}
}


