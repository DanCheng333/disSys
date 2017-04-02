import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.Timestamp;

public class Server implements IServer{
    public CopyOnWriteArrayList<String> frontServers;
    public CopyOnWriteArrayList<String> middleServers;
    public ServerLib SL;
    public boolean isFirst = true;
    public ConcurrentLinkedQueue<Cloud.FrontEndOps.Request> globalRequestQueue;
    public ConcurrentLinkedQueue<String> VMNameQueue;
    public int VMCounter;
    public String name;
    public Date ts;
    public IServer stub;
    public int globalQLength;
    public int prevQLength;
    public int dropCount;
    public int monitorCounter;
    
    public static IServer masterServer;
    public static Registry reg;
    public static final int SCALEDOWNTHRESHOLD = 2200;
    public static final int MONITORPOLLRATE = 24;
    
    public Server() {
        this.frontServers  = new CopyOnWriteArrayList<String>();
        this.middleServers = new CopyOnWriteArrayList<String>();
        this.isFirst = true;
    }
    
    public String getName() throws RemoteException {
        return VMNameQueue.poll();
    }
    
    public Cloud.FrontEndOps.Request getGlobalNextRequest() throws RemoteException{
        return globalRequestQueue.poll();
    }
    
    
    public void addRequestToGlobalQueue(Cloud.FrontEndOps.Request r) throws RemoteException {
        globalRequestQueue.add(r);
    }
    
    public int getGlobalRequestQueueLength() throws RemoteException {
        return globalRequestQueue.size();
    }
    
    public void adjustFrontMiddleRatio() throws RemoteException {
        if (this.frontServers.size() * 4 > this.middleServers.size()) {
            try {
                String name = this.frontServers.get(0);
                IServer uselessServer = (IServer)reg.lookup(name);
                uselessServer.shutdownVM();
                this.frontServers.remove(name);
            } catch (NotBoundException e) {
                System.out.println("not bound");
            }
        }
    }
    
    public void startFrontVM() throws RemoteException {
        String name = "front:" + Integer.toString(VMCounter);
        VMCounter++;
        VMNameQueue.add(name);
        SL.startVM();
    }
    
    
    public void startMiddleVM() throws RemoteException {
        String name = "middle:" + Integer.toString(VMCounter);
        VMCounter++;
        VMNameQueue.add(name);
        SL.startVM();
    }
    
    public void addVMName(String name) throws RemoteException {
        String[] parts = name.split(":");
        if(parts[0].equals("front")) {
            frontServers.add(name);
        } else if (parts[0].equals("middle")) {
            middleServers.add(name);
        }
    }
    
    public void removeVMName(String name) throws RemoteException {
        String[] parts = name.split(":");
        if(parts[0].equals("front")) {
            frontServers.remove(name);
        } else if (parts[0].equals("middle")) {
            middleServers.remove(name);
        }
    }
    
    
    public int getMiddleVMNum() throws RemoteException {
        return middleServers.size();
    }
    
    public void shutdownVM() throws RemoteException {
        ServerLib sl = this.SL;
        if ((this.name.split(":")[0]).equals("front")) {
            System.out.println("unregister front");
            sl.unregister_frontend();
            while (sl.getQueueLength() > 0) {
                this.masterServer.addRequestToGlobalQueue(sl.getNextRequest());
            }
        }
        
        if ((this.name.split(":")[0]).equals("middle")) {
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (NoSuchObjectException e) {
                e.printStackTrace();
            }
        }
        
        
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        }
        sl.interruptGetNext();
        sl.shutDown();
    }
    
    
    public boolean inFrontServers(String name) throws RemoteException  {
        return this.frontServers.contains(name);
    }
    public boolean inMiddleServers(String name) throws RemoteException {
        return this.middleServers.contains(name);
    }
    
    
    
    
    
    public static long getDateDiff(Date date1, Date date2) {
        long diffInMillies = date2.getTime() - date1.getTime();
        return diffInMillies;
    }
    
    public static void main ( String args[] ) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");
        Server s = new Server();
        try {
            s.stub = (IServer) UnicastRemoteObject.exportObject(s, 0);
            reg = LocateRegistry.getRegistry( args[0], Integer.parseInt(args[1]));
            reg.bind("master", s.stub);
        } catch (AlreadyBoundException e) {
            // Master node already exists, get roles from master
            s.isFirst = false;
        } catch (AccessException e) {
            System.out.println(e);
        } catch (NullPointerException e) {
            System.out.println(e);
        } catch (RemoteException e) {
            System.out.println(e);
        }
        
        ServerLib serverL = new ServerLib( args[0], Integer.parseInt(args[1]) );
        s.SL = serverL;
        
        if(s.isFirst) {
            s.globalRequestQueue = new ConcurrentLinkedQueue<Cloud.FrontEndOps.Request>();
            s.VMNameQueue = new ConcurrentLinkedQueue<String>();
            s.VMCounter = 0;
            s.monitorCounter = 0;
            s.dropCount = 0;
            // register with load balancer so requests are sent to this server
            s.SL.register_frontend();
            s.startMiddleVM();
            
            
            int middleNum = 0;
            while (s.middleServers.size() == 0) {
                if (s.SL.getQueueLength() > 0) {
                    s.SL.dropHead();
                    s.dropCount++;
                    if ((s.dropCount > 0) && (s.dropCount % 7 == 0)) {
                        s.startMiddleVM();
                        middleNum++;
                        if ((middleNum > 0) && (middleNum % 3 == 1)) {
                            s.startFrontVM();
                        }
                    }
                }
                s.monitorCounter++;
            }
            
            s.globalQLength = s.globalRequestQueue.size();
            s.prevQLength = s.SL.getQueueLength();
            // main loop
            while (true) {
                int length = s.globalRequestQueue.size();
                int qL = s.SL.getQueueLength();
                
                // check the combined size periodacially
                if (s.monitorCounter % MONITORPOLLRATE == 0) {
                    int dl = length-s.globalQLength;
                    int avLen = length/s.middleServers.size();
                    int dql = qL - s.prevQLength;
                    int startingFNum = 0;
                    int startingMNum = 0;
                    
                    Object[] VMNameQueueArray = s.VMNameQueue.toArray();
                    for (int i = 0; i < s.VMNameQueue.size(); i++) {
                        if (((String)VMNameQueueArray[i]).split(":")[0].equals("front")) {
                            startingFNum++;
                        }
                        if (((String)VMNameQueueArray[i]).split(":")[0].equals("middle")) {
                            startingMNum++;
                        }
                    }
                    
                    System.out.printf("(dl:"+ Integer.toString(dl) + "   aL:" + Integer.toString(avLen) + "   dql:" + Integer.toString(dql) +"   s:" + Integer.toString(s.frontServers.size()+1) + "("+ Integer.toString(startingFNum) +")"+":" + Integer.toString(s.middleServers.size()) + "(" + Integer.toString(startingMNum) +")" +")\n");
                    
                    if ((dl > 2) || ((dl > 0) && (avLen > 2))) {
                        s.startMiddleVM();
                    }
                    
                    if (((s.middleServers.size() + startingMNum) < 11)) {
                        if ((dl > 1) && (avLen > 3) ) {
                            s.startMiddleVM();
                        }
                        if ((dl > 5) && (avLen > 0)) {
                            s.startMiddleVM();
                        }
                        if ((dl > 4) && (avLen > 3)) {
                            s.startMiddleVM();
                        }
                        if ((dl > 9) && (avLen > 9)) {
                            s.startMiddleVM();
                        }
                        if (dl > 10) {
                            s.startMiddleVM();
                        }
                        if (dl > 15) {
                            s.startMiddleVM();
                        }
                    }
                    
                    if (dql > 1 && ((s.frontServers.size()+startingFNum) * 3 < s.middleServers.size())) {
                        s.startFrontVM();
                    }
                    
                    if (dql > 7) {
                        s.startFrontVM();
                    }
                    
                    
                    int midshutdownNum = 0;
                    if (((dl < 0) && (avLen == 0)) ||
                        ((dl == 0) && (avLen == 0) && s.middleServers.size() > 4)){
                        midshutdownNum++;
                        if ((dl < -15) && s.middleServers.size() > 6) {
                            midshutdownNum++;
                        }
                        
                        if (dl < -18) {
                            midshutdownNum++;
                        }
                    }
                    
                    while (midshutdownNum > 0) {
                        for (int i = 0; i < s.middleServers.size(); i++) {
                            String name = s.middleServers.get(i);
                            if (!(name.split(":")[1].equals("0"))) {
                                s.middleServers.remove(name);
                                IServer uselessMServer = (IServer)reg.lookup(name);
                                try {
                                    reg.unbind(name);
                                } catch (NotBoundException e) {
                                    e.printStackTrace();
                                }
                                uselessMServer.shutdownVM();
                                midshutdownNum--;
                                break;
                            }
                        }
                        
                    }
                    s.prevQLength = qL;
                    s.globalQLength = length;
                }
                
                Cloud.FrontEndOps.Request r = s.SL.getNextRequest();
                s.globalRequestQueue.add(r);
                s.monitorCounter++;
            }
        } else {
            try {
                reg = LocateRegistry.getRegistry( args[0], Integer.parseInt(args[1]));
                masterServer = (IServer)reg.lookup("master");
                s.name = masterServer.getName();
                reg.bind(s.name, s.stub);
                masterServer.addVMName(s.name);
                
                
            } catch (NotBoundException e) {
                System.out.println(e);
            } catch (AlreadyBoundException e) {
                System.out.println(e);
            } catch (AccessException e) {
                System.out.println(e);
            } catch (NullPointerException e) {
                System.out.println("Got null name while binding");
                System.out.println(e);
            } catch (RemoteException e) {
                System.out.println(e);
            }
            
            
            String[] parts = s.name.split(":");
            if(parts[0].equals("front")) {
                s.SL.register_frontend();
                while (masterServer.inFrontServers(s.name)) {
                    try {
                        Cloud.FrontEndOps.Request r = s.SL.getNextRequest();
                        if (r != null) {
                            masterServer.addRequestToGlobalQueue(r);
                        }
                    } catch (RemoteException e){}
                }
                
            } else if (parts[0].equals("middle")) {
                if (s.ts == null) {
                    s.ts = new Date();
                }
                
                
                // main loop
                while (masterServer.inMiddleServers(s.name)) {
                    try {
                        Date curr = new Date();
                        long diff = getDateDiff(s.ts,curr);
                        if ((diff > SCALEDOWNTHRESHOLD) && !(parts[1].equals("0"))) {

                            reg = LocateRegistry.getRegistry( args[0], Integer.parseInt(args[1]));
                            IServer uselessServer = (IServer)reg.lookup(s.name);
                            uselessServer.shutdownVM();
                            masterServer.removeVMName(s.name);
                            masterServer.adjustFrontMiddleRatio();
                            break;
                        } else {
                            Cloud.FrontEndOps.Request r = masterServer.getGlobalNextRequest();
                            if (r != null) {
                                s.SL.processRequest( r);
                                s.ts = curr;
                            }
                        }
                    } catch (RemoteException e) {}
                }
                
                System.out.println("shutting down");
            }
        }
    }
}