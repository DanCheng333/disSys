/* Sample code for basic Server */

public class Server {
	public static void main ( String args[] ) throws Exception {
		if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
		
		// register with load balancer so requests are sent to this server
		SL.register_frontend();
		
		// main loop
		while (true) {
			float timeOfDay = SL.getTime();
			/* Benchmarking */
			//1:4-8  => 2 VMs
			if (timeOfDay >= 4 && timeOfDay <= 8) {
				if (SL.getStatusVM(2) == Cloud.CloudOps.VMStatus.NonExistent) {
					SL.startVM();
				}
			}
			//2:9-15  => 3 VMs
			if (timeOfDay >= 9 && timeOfDay <= 15) {
				if (SL.getStatusVM(3) == Cloud.CloudOps.VMStatus.NonExistent) {
					SL.startVM();
				}
			}
			//3:16-21  => 4 VMs
			if (timeOfDay >= 16 && timeOfDay <= 21) {
				if (SL.getStatusVM(4) == Cloud.CloudOps.VMStatus.NonExistent) {
					SL.startVM();
				}
			}
			
			Cloud.FrontEndOps.Request r = SL.getNextRequest();
			SL.processRequest( r );
		}
	}
}


