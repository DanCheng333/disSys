import java.io.*;
import java.util.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

public class Cache extends UnicastRemoteObject implements Cloud.DatabaseOps {
    public ConcurrentHashMap<String, String> ht;
    public ServerLib SL;
    public static IServer masterServer;
    
    public Cache(IServer ms, ServerLib sl) throws RemoteException {
        this.masterServer = ms;
        this.SL = sl;
    }

    public String get(String key) throws RemoteException {
        this.ht = masterServer.getCacheHashMap();
        if (ht.containsKey(key)) {
            return ht.get(key);
        } else {
            Cloud.DatabaseOps db = SL.getDB();
            String val = db.get(key);
            masterServer.cacheAddKeyVal(key, val);
            return val;
        }
    }

    public boolean set(String key, String val, String auth)
    throws RemoteException {
        Cloud.DatabaseOps db = SL.getDB();
        boolean success = db.set(key, val, auth);
        if (success) {
            this.ht = masterServer.getCacheHashMap();
            if (ht.containsKey(key)) {
                System.out.println("Cache set hit");
                masterServer.cacheAddKeyVal(key, val);
            } else {
                System.out.println("Cache set miss, adding.");
                masterServer.cacheAddKeyVal(key, val);
            }
        }
        return success;
    }
    
    public boolean transaction(String item, float price, int qty)
    throws RemoteException {
        Cloud.DatabaseOps db = SL.getDB();
        return db.transaction(item, price, qty);
    }
}