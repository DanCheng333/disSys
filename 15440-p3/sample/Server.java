/* Sample code for basic Server */

/*public class Server {
	public static void main ( String args[] ) throws Exception {
		if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
		
		// register with load balancer so requests are sent to this server
		SL.register_frontend();
		
		// main loop
		while (true) {
			Cloud.FrontEndOps.Request r = SL.getNextRequest();
			SL.processRequest( r );
		}
	}
}*/

public class Server {
    public static void main ( String args[] ) throws Exception {
	if (args.length != 2) throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");
	ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );

	// register with load balancer so requests are sent to this server
	SL.register_frontend();

	//if(SL.getStatusVM(4) == Cloud.CloudOps.VMStatus.NonExistent) SL.startVM();
	// main loop
	// 6 AM 1 EX VM
	while (true) {
	    float time = SL.getTime();
	    if(time > 2 && time <= 7) { 
		if(SL.getStatusVM(2) == Cloud.CloudOps.VMStatus.NonExistent) {
		    SL.startVM();
		}
	    }
	    if(time > 7 && time <= 18 || time >= 23) {
		if(SL.getStatusVM(3) == Cloud.CloudOps.VMStatus.NonExistent) {
		    SL.startVM();
		}
	    }
	    if(time > 18 && time < 23) {
		if(SL.getStatusVM(5) == Cloud.CloudOps.VMStatus.NonExistent) {
		    SL.startVM();
		}
	    }
	    Cloud.FrontEndOps.Request r = SL.getNextRequest();
	    SL.processRequest( r );
	}
    }
}

