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
	public static final int SCALEOUTPERIOD = 5000;
	public static final int SCALEINPERIOD = 5000;
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

	// Interval rate
	public static long interval1;
	public static long interval2;
	public static long intervalInAccu;
	public static long intervalOutAccu;
	public static long lastScaleIn;
	public static long lastScaleOut;
	public static int scaleInCounter;
	public static int scaleOutCounter;

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

	public static void masterAction() throws RemoteException {
		// Initialize
		initMaster();

		/* Scale in / Scale Out */
		while (true) {
			try {
				/* Scale out if the requestQ is larger than the middle Server */
				if (requestQueue.size() > middleServerList.size() * 1.5) {
					int offset = (int) (((requestQueue.size() - middleServerList.size()) / 2) + 1);
					scaleOut(offset, 0);

				}

			} catch (Exception e) {
				continue;
			}

			/* Calculate request come in rate */
			long lastTimeGetRequest = System.currentTimeMillis();
			int requestLen = requestQueue.size();
			while (requestLen == requestQueue.size()) {
			}

			interval2 = System.currentTimeMillis() - lastTimeGetRequest;
			intervalInAccu += interval2;
			intervalOutAccu += interval2;
			scaleInCounter++;
			scaleOutCounter++;

			// Not going to finish in time.... drop and avoid erroneous sales
			if (requestQueue.size() > middleServerList.size()) {
				while (requestQueue.size() > middleServerList.size() * 1.8) {
					SL.drop(requestQueue.poll());

				}
			} else {
				// Scale in, interval over 101 requests are very slow
				if (scaleInCounter % 20 == 0) {
					int avg = (int) (intervalInAccu / scaleInCounter);
					if (avg > interval1 * 3) { // decrease
						long now = System.currentTimeMillis();
						if (now - lastScaleIn > 5000) {
							int scaleInMidNumber = middleServerList.size() / 5;
							int scaleInFrontNumber = 1;
							scaleIn(scaleInMidNumber, scaleInFrontNumber);
							interval1 = (avg + interval1) / 2;
							lastScaleIn = now;

						}
					}
					intervalInAccu = 0;
					scaleInCounter = 0;
				}
			}
		}

	}

	private static void initMaster() {
		SL.register_frontend();
		frontServerList = Collections.synchronizedList(new ArrayList<>());
		middleServerList = Collections.synchronizedList(new ArrayList<>());
		requestQueue = new ConcurrentLinkedQueue<Cloud.FrontEndOps.Request>();
		cacheHashMap = new ConcurrentHashMap<String, String>();
		intervalInAccu = 0;
		intervalOutAccu = 0;

		// Start with 1 front and 1 middle server
		startMidNum = 1;
		startFrontNum = 1;
		for (int i = 0; i < startMidNum; ++i) {
			middleServerList.add(SL.startVM());
		}
		for (int i = 0; i < startFrontNum; ++i) {
			frontServerList.add(SL.startVM());
		}

		// Before front server and middle server start
		// Drop every requests in the SL
		while (!startF.get() && !startM.get()) {
			Cloud.FrontEndOps.Request r = SL.getNextRequest();
			if (!startF.get() && !startM.get()) {
				SL.drop(r);
			} else {
				requestQueue.add(r);
			}
		}

		// Get the interval of the first two come in requests
		long time1 = System.currentTimeMillis();
		while (SL.getQueueLength() == 0)
			;
		long time2 = System.currentTimeMillis();
		interval1 = time2 - time1;
		// Benchmark and init servers based on the come in rate
		if (interval1 < 100) {
			startMidNum = 9;
			startFrontNum = 1;
		} else if (interval1 < 300) {
			startMidNum = 6;
			startFrontNum = 1;
		} else if (interval1 < 600) {
			startMidNum = 3;
			startFrontNum = 0;
		} else {
			startMidNum = 1;
			startFrontNum = 0;
		}
		scaleOut(startMidNum, startFrontNum);

		lastScaleOut = System.currentTimeMillis();
		lastScaleIn = System.currentTimeMillis();
		scaleInCounter = 0;
		scaleOutCounter = 0;

		// Master stop receiving request
		SL.unregister_frontend();

	}

	/**
	 * ScaleIn decrease servers
	 * 
	 * @param scaleInMidNumber
	 * @param scaleInFrontNumber
	 * @throws RemoteException
	 */
	private static boolean scaleIn(int scaleInMidNumber, int scaleInFrontNumber) throws RemoteException {
		System.err.println("==========scaleIn============");
		long now = System.currentTimeMillis();
		for (int i = 0; i < scaleInMidNumber; i++) {
			if (middleServerList.size() > 1) {
				int id = middleServerList.remove(middleServerList.size() - 1);
				shutdownVM(id);
			}
		}
		for (int i = 0; i < scaleInFrontNumber; i++) {
			if (frontServerList.size() > 1) {
				int id = frontServerList.remove(frontServerList.size() - 1);
				shutdownVM(id);
			}
		}
		lastScaleIn = now;
		return true;

	}

	/**
	 * Scale Out Increase servers
	 * 
	 * @param scaleOutMidNumber
	 * @param scaleOutFrontNumber
	 */
	private static boolean scaleOut(int scaleOutMidNumber, int scaleOutFrontNumber) {
		long now = System.currentTimeMillis();
		System.err.println("============scale Out==========");
		for (int i = 0; i < scaleOutMidNumber; i++) {
			middleServerList.add(SL.startVM());

		}
		for (int i = 0; i < scaleOutFrontNumber; i++) {
			frontServerList.add(SL.startVM());
		}
		lastScaleOut = now;
		return true;

	}

	public static void shutdownVM(int id) throws RemoteException {
		Role reply = null;
		try {
			reply = masterServer.getRole(id);
		} catch (Exception e) {
			return;
		}
		// front
		if (reply == Role.FRONT) {
			System.err.println("Shut down front id:" + id);
			SL.endVM(id);

		}
		// middle
		else if (reply == Role.MIDDLE) {
			System.err.println("Shut down middle id:" + id);
			SL.endVM(id);

		} else {
			System.err.println(" Shut down NONE server!!!");
			shutdownVM(id);
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

				continue;
			}

		}
	}

	/**
	 * Master, FrontTiers, MiddleTiers
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
				shutdownVM(vmID);
			}
		}
	}

	@Override
	public Role getRole(Integer vmID) throws RemoteException {
		if (!frontServerList.contains(vmID)) {
			System.err.println(" Middle, ID:" + vmID);
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
	/**
	 * Front server is started, mark true
	 */
	public void startF() throws RemoteException {
		startF.set(true);

	}

	@Override
	/**
	 * Middle server is started, mark true
	 */
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
