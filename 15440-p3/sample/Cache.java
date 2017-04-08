import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author danc
 *
 */
public class Cache extends UnicastRemoteObject implements Cloud.DatabaseOps {
	public ConcurrentHashMap<String, String> cacheMap;
	public ServerLib SL;
	public static IServer masterServer;

	public Cache(IServer ms, ServerLib sl) throws RemoteException {
		this.masterServer = ms;
		this.SL = sl;
	}

	/**
	 * Returns string associated with key
	 */
	public String get(String key) throws RemoteException {
		this.cacheMap = masterServer.getCacheMap();
		// If in the map return
		if (cacheMap.containsKey(key)) {
			return cacheMap.get(key);
		}
		// Not in the map, get it from DB and add it to cache
		else {
			Cloud.DatabaseOps db = SL.getDB();
			String val = db.get(key);
			masterServer.cacheAdd(key, val);
			return val;
		}
	}

	/**
	 * Adds key value pair
	 */
	public boolean set(String key, String val, String auth) throws RemoteException {
		Cloud.DatabaseOps db = SL.getDB();
		boolean isSet = db.set(key, val, auth);
		if (isSet) {
			this.cacheMap = masterServer.getCacheMap();
			if (cacheMap.containsKey(key)) {
				System.out.println("Cache isSet, in map!");
				masterServer.cacheAdd(key, val);
			} else {
				System.out.println("Cache not set or not in map!");
				masterServer.cacheAdd(key, val);
			}
		}
		return isSet;
	}

	/**
	 * Perform purchase transaction in database, returning true on success
	 */
	public boolean transaction(String item, float price, int qty) throws RemoteException {
		Cloud.DatabaseOps db = SL.getDB();
		return db.transaction(item, price, qty);
	}
}