import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * @author danc
 *
 */
public class Server extends UnicastRemoteObject implements IServer {
	public static final int MASTER = 1;
	private static int startMidNum = 1;
	private static int startFrontNum = 0;

	private static AtomicBoolean startF;
	private static AtomicBoolean startM;

	public static String cloud_ip;
	public static int cloud_port;
	public static int vmID;
	public static ServerLib SL;

	public static IServer masterServer;
	private static List<Integer> frontServerList;
	private static List<Integer> middleServerList;
	public static ConcurrentLinkedQueue<Cloud.FrontEndOps.Request> requestQueue;

	// Cache ops
	public static ConcurrentHashMap<String, String> cacheHashMap;
	public static Cloud.DatabaseOps cache;

	public enum Role {
		FRONT, MIDDLE, NONE
	}

	public Server() throws RemoteException {
		startF = new AtomicBoolean(false);
		startM = new AtomicBoolean(false);
	}

	/* Before front server and middle server before start, => SL.drophead */

	public static void masterAction() {
		// SL.startVM();
		SL.register_frontend();
		frontServerList = Collections.synchronizedList(new ArrayList<>());
		middleServerList = Collections.synchronizedList(new ArrayList<>());
		requestQueue = new ConcurrentLinkedQueue<Cloud.FrontEndOps.Request>();

		cacheHashMap = new ConcurrentHashMap<String, String>();

		startMidNum = 1;
		startFrontNum = 1;
		for (int i = 0; i < startMidNum; ++i) {
			System.err.println("Add first Middle");
			middleServerList.add(SL.startVM());
		}
		for (int i = 0; i < startFrontNum; ++i) {
			System.err.println("Add first Front");
			frontServerList.add(SL.startVM());
		}

		/*
		 * System.err.println("WHile1"); while(SL.getQueueLength() == 0 ); long
		 * time1 = System.currentTimeMillis();
		 */
		while (!startF.get() && !startM.get()) {
			Cloud.FrontEndOps.Request r = SL.getNextRequest();
			if (!startF.get() && !startM.get()) {
				SL.drop(r);
			} else {
				requestQueue.add(r);
			}
			// System.err.println("drop request");
		}
		System.err.println("start M and F");

		long time1 = System.currentTimeMillis();
		System.err.println("WHile2");
		while (SL.getQueueLength() == 0)
			;
		long time2 = System.currentTimeMillis();
		long interval = time2 - time1;
		System.err.println("WHile, interval :" + interval);

		if (interval < 100) {
			startMidNum = 9;
			startFrontNum = 1;
		} else if (interval < 300) {
			startMidNum = 6;
			startFrontNum = 1;
		} else if (interval < 600) {
			startMidNum = 3;
			startFrontNum = 0;
		} else {
			startMidNum = 1;
			startFrontNum = 0;
		}

		for (int i = 0; i < startMidNum; ++i) {
			System.err.println("Init:Add Middle");
			middleServerList.add(SL.startVM());
		}
		for (int i = 0; i < startFrontNum; ++i) {
			System.err.println("Init: Add front");
			frontServerList.add(SL.startVM());
		}

		SL.unregister_frontend();

		// while( middleServerList.size() == 0){
		// SL.dropHead();
		// }

		// System.err.println("interval:" + interval + " start:" + startMidNum +
		// "
		// startFor:" + startFrontNum);
		// Cloud.FrontEndOps.Request r = null;
		while (true) {
			System.err.println("WHile1");
			long lastTimeGetReq = System.currentTimeMillis();
			int requestLen = requestQueue.size();
			while (requestLen == requestQueue.size()) {
			}
			System.err.println("WHile2");
			long period = System.currentTimeMillis() - lastTimeGetReq;
			System.err.println("WHile, period :" + period);
/*
			try { // consider scaleout
				int queLen = SL.getQueueLength();
				if (queLen > middleServerList.size() * 1.5) {
					scaleOutFor(1);
					int number = (int) (queLen / middleServerList.size() * 4);
					scaleOutApp(number);
				}

				// if queue is too long, drop head
				if (requestQueue.size() > middleServerList.size()) {
					while (requestQueue.size() > middleServerList.size() * 1.5) {
						SL.drop(requestQueue.poll());
					}
				} else { // consider scalein
					long lastTimeGetReq = System.currentTimeMillis();
					int requestLen = requestQueue.size();
					while ((requestLen == requestQueue.size()) {
					}
					long period = System.currentTimeMillis() - lastTimeGetReq;

					if (period > interval * 3) {
						int scaleInAppNumber = (int) (period - interval) / 40;
						int scaleInForNumber = scaleInAppNumber > 5 ? 1 : 0;
						if (scaleIn(scaleInAppNumber, scaleInForNumber)) {
							interval = period;
						}
					}
					requestQueue.add(r);
				}
			} catch (Exception e) {
				continue;
			}*/

			try
			// interval (long time => middleServer.drop)scale in/scale out
			{
				int deltaSize = requestQueue.size() - middleServerList.size();
				if (deltaSize > 0) {
					while (requestQueue.size() > middleServerList.size() * 2) {
						for (int i = 0; i < deltaSize / 2 + 1; i++) {
							System.err.println("Add middle tiers");
							middleServerList.add(SL.startVM());
						}
					}
				}
			} catch (Exception e) {
				continue;
			}

		}
	}

	public static void frontTierAction() throws RemoteException {
		System.out.println("==========FrontTier===========");
		masterServer.startF();
		SL.register_frontend();
		Cloud.FrontEndOps.Request r = null;
		while (true) {
			while ((r = SL.getNextRequest()) == null) {
			}
			try {
				masterServer.addRequest(r);
			} catch (RemoteException e) {
				System.err.println("add request failed");
				// e.printStackTrace();
			}
		}
	}

	public static void middleTierAction() throws RemoteException {
		System.out.println("==========MiddleTier=========");
		masterServer.startM();
		try {
			cache = new Cache(masterServer, SL);

		} catch (RemoteException e) {
		}
		while (true) {
			try {
				Cloud.FrontEndOps.Request r = masterServer.getRequest();
				SL.processRequest(r, cache);
			} catch (Exception e) {
				// System.err.println("get request failed");
				// e.printStackTrace();
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

		// Master
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
			// middleServerList.add(vmID);
			return Role.MIDDLE;
		} else {
			System.out.println("Front,ID:" + vmID);
			return Role.FRONT;
		}

	}

	@Override
	public Cloud.FrontEndOps.Request getRequest() throws RemoteException {
		Cloud.FrontEndOps.Request r = requestQueue.poll();
		while (r == null) {
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

	@Override
	public void startF() throws RemoteException {
		startF.set(true);

	}

	@Override
	public void startM() throws RemoteException {
		startM.set(true);

	}

	@Override
	public ConcurrentHashMap<String, String> getCacheMap() throws RemoteException {
		return cacheHashMap;

	}

	@Override
	public void cacheAdd(String key, String val) throws RemoteException {
		cacheHashMap.put(key, val);
	}

}
