import java.rmi.NoSuchObjectException;
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

	public static long interval1;
	public static long interval2;
	public static long lastScaleIn;
	public static long lastScaleOut;

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

	public static void masterAction() throws RemoteException {
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
		while (SL.getQueueLength() == 0)
			;
		long time2 = System.currentTimeMillis();
		interval1 = time2 - time1;
		System.err.println("WHile, interval1 :" + interval1);

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

		SL.unregister_frontend();

		while (true) {
			try {
				if (requestQueue.size() > middleServerList.size() * 2) {
					System.err.println("fall in range 2");
					int offset = (int) (requestQueue.size() / middleServerList.size() * 10);
					System.err.println("!!!!!!!!Add middle tiers!!!!!!!!!! offset : " + offset);
					scaleOut(offset, 0);
				}
				if (requestQueue.size() > middleServerList.size() * 1.5
						&& requestQueue.size() < middleServerList.size() * 2) {
					System.err.println("fall in range 1.5");
					int offset = (int) (requestQueue.size() / middleServerList.size() * 4);
					System.err.println("!!!!!!!!Add middle tiers!!!!!!!!!! offset : " + offset);
					scaleOut(offset, 0);
				}

			} catch (Exception e) {
				continue;
			}

			/* Request come in rate */
			long lastTimeGetReq = System.currentTimeMillis();
			int requestLen = requestQueue.size();
			while (requestLen == requestQueue.size()) {
			}

			interval2 = System.currentTimeMillis() - lastTimeGetReq;

			
			if (interval2 > interval1 * 2) { // decrease
																			// servers
				System.err.println("fall in range 2");
				System.err.println("interval2 > interval1 * 3,1:" + interval1 + ",2:" + interval2);
				System.err.println("decrease servers, scale in");
				int scaleInMidNumber = middleServerList.size() / 5;
				int scaleInFrontNumber = 1;
				System.err
						.println("scaleInMidNumber:" + scaleInMidNumber + ", scaleInFrontNumber:" + scaleInFrontNumber);
				if (scaleIn(scaleInMidNumber, scaleInFrontNumber)) {
					interval1 = interval2;
				}
				
			}

		}

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
		System.err.println("Before scaleIn===== mid size: " + middleServerList.size() + "===== front size: "
				+ frontServerList.size());
		long now = System.currentTimeMillis();
		if (now - lastScaleIn > SCALEINPERIOD) {
			System.err.println("============Time to scale in================");
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
			System.err.println("After scaleIn===== mid size: " + middleServerList.size() + "===== front size: "
					+ frontServerList.size());
			lastScaleIn = now;
			return true;
		}
		return false;

	}

	/**
	 * Increase servers
	 * 
	 * @param scaleOutMidNumber
	 * @param scaleOutFrontNumber
	 */
	private static boolean scaleOut(int scaleOutMidNumber, int scaleOutFrontNumber) {
		System.err.println("==========scaleOut============");
		long now = System.currentTimeMillis();
		if (now - lastScaleOut > SCALEOUTPERIOD) {
			System.err.println("============Time to scale Out==========");
			for (int i = 0; i < scaleOutMidNumber; i++) {
				middleServerList.add(SL.startVM());

			}
			for (int i = 0; i < scaleOutFrontNumber; i++) {
				frontServerList.add(SL.startVM());
			}
			lastScaleOut = now;
			return true;
		}
		return false;
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
			// frontServerList.remove(id);
			SL.endVM(id);

		}
		// middle
		else if (reply == Role.MIDDLE) {
			System.err.println("Shut down middle id:" + id);
			// middleServerList.remove(id);
			SL.endVM(id);

		} else {
			System.err.println(" Shut down NONE server!!!");
			masterServer.shutDownVM(vmID, Role.NONE);
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
