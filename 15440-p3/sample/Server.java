import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server extends UnicastRemoteObject implements IServer {
	public static final int MASTER = 1;
	private static int startNum = 1;
	private static int startForNum = 0;

	public static String cloud_ip;
	public static int cloud_port;
	public static int vmID;
	public static ServerLib SL;

	public static IServer masterServer;
	private static List<Integer> frontServerList;
	private static List<Integer> middleServerList;
	public static ConcurrentLinkedQueue<Cloud.FrontEndOps.Request> requestQueue;

	public enum Role {
		FRONT, MIDDLE, NONE
	}

	public Server() throws RemoteException {

	}

	public static void masterAction() {
		SL.startVM();
		SL.register_frontend();
		frontServerList = Collections.synchronizedList(new ArrayList<>());
		middleServerList = Collections.synchronizedList(new ArrayList<>());
		requestQueue = new ConcurrentLinkedQueue<Cloud.FrontEndOps.Request>();
		
        
		while(SL.getQueueLength() == 0 );
        long time1 = System.currentTimeMillis();
        
        SL.dropHead();
        
        while(SL.getQueueLength() == 0 );
        long time2 = System.currentTimeMillis();
        long interval = time2 - time1;
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
        for (int i = 0; i < startForNum; ++i) {
        	System.err.println("Start front outside of while loop");
            frontServerList.add(SL.startVM());
        }

        while( middleServerList.size() == 0){
        	
        		SL.dropHead();
        
        	break;
        }


		System.err.println("interval:" + interval + " start:" + startNum + " startFor:" + startForNum);
		// Cloud.FrontEndOps.Request r = null;
		while (true) {
			try {
				// if queue is too long, drop head
				if (requestQueue.size() > middleServerList.size()) {
					while (requestQueue.size() > middleServerList.size() * 2) {
						System.err.println("scale out");
						SL.drop(requestQueue.poll());
					}
				}
			} catch (Exception e) {
				continue;
			}
			
			
			// measure current traffic
			int deltaFront = SL.getQueueLength() - frontServerList.size();
			int deltaMid = requestQueue.size() - middleServerList.size();
			
			int vmSize = middleServerList.size() + frontServerList.size();
			if (deltaFront > 0 || deltaMid > 0) {
				// lackFront = deltaFront > deltaMid ? true : false;
				// int tmp = deltaFront > deltaMid ? deltaFront : deltaMid;
				for (int i = 0; i < deltaFront; i++) {

					if (SL.getStatusVM(vmSize + i) == Cloud.CloudOps.VMStatus.NonExistent) {
						System.err.println("Start front");
						frontServerList.add(SL.startVM());
					}
				}
				for (int i = 0; i < deltaMid; i++) {

					if (SL.getStatusVM(vmSize + i) == Cloud.CloudOps.VMStatus.NonExistent) {
						System.err.println("Start middle");
						middleServerList.add(SL.startVM());
					}
				}
			}
		}
	}

	public static void frontTierAction() {
		System.out.println("==========FrontTier===========");

		SL.register_frontend();
		Cloud.FrontEndOps.Request r = null;
		while (true) {
			while ((r = SL.getNextRequest()) == null) {
			}
			try {
				masterServer.addRequest(r);
			} catch (RemoteException e) {
				System.err.println("add request failed");
				//e.printStackTrace();
			}
		}
	}

	public static void middleTierAction() {
		System.out.println("==========MiddleTier=========");
		while (true) {
			try {
				Cloud.FrontEndOps.Request r = masterServer.getRequest();
				SL.processRequest(r);
			} catch (Exception e) {
				//System.err.println("get request failed");
				//e.printStackTrace();
				continue;
			}

		}
	}

	/**
	 * FrontTiers(include MASTER), MiddleTiers
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		if (args.length != 3)
			throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		cloud_ip = args[0];
		cloud_port = Integer.parseInt(args[1]);
		vmID = Integer.parseInt(args[2]);

		System.err.println("cloud_ip:" + cloud_ip + ", cloud_port" + cloud_port + ", vmID:" + vmID);

		SL = new ServerLib(cloud_ip, cloud_port);
		LocateRegistry.getRegistry(cloud_ip, cloud_port).bind("//127.0.0.1/vmID" + String.valueOf(vmID), new Server());

		if (vmID == MASTER) {
			masterAction();
		}

		// Not MASTER
		else {
			// Look up for master server
			System.err.println("Not master");
			while (true) {
				try {
					masterServer = (IServer) LocateRegistry.getRegistry(cloud_ip, cloud_port)
							.lookup("//127.0.0.1/vmID1");
					System.err.println("Connect to master");
					break;
				} catch (Exception e) {
					continue;
				}
			}

			Role reply = null;
			try {
				reply = masterServer.getRole(vmID);
			} catch (Exception e) {
				return;
			}
			// front
			if (reply == Role.FRONT) {
				System.err.println("FRONT");
				frontTierAction();
				
			}
			// middle
			else if (reply == Role.MIDDLE) {
				System.err.println("MIDDLE");
				middleTierAction();
				
			} else {
				System.err.println(" NONE server!!!");
				masterServer.shutDownVM(vmID, Role.NONE);
			}
		}
	}

	@Override
	public Role getRole(Integer vmID) throws RemoteException {
		if (!frontServerList.contains(vmID)) {
			System.err.println(" Middle, ID:" + vmID);
			middleServerList.add(vmID);
			return Role.MIDDLE;
		} else {
			System.out.println("Front,ID:" + vmID);
			return Role.FRONT;
		}

	}

	@Override
	public Cloud.FrontEndOps.Request getRequest() throws RemoteException {
		Cloud.FrontEndOps.Request r = null;
		while (r != null) {
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
		if (role == Role.FRONT) {
			System.out.println("ShutDown frontTier vmID:" + vmId);
			frontServerList.remove(vmId);

			SL.endVM(vmId);
		} else if (role == Role.MIDDLE) {
			System.out.println("kill processor:" + vmId);
			middleServerList.remove(vmId);
			SL.endVM(vmId);
		} else {
			SL.endVM(vmId);
		}

	}

	public synchronized void shutDown() throws RemoteException {
		UnicastRemoteObject.unexportObject(this, true);
	}

}
