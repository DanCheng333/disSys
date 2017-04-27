import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author danc
 *
 */


public class Server implements ProjectLib.CommitServing  {
	public static AtomicInteger commitCounter = new AtomicInteger(0);
	public static ConcurrentHashMap<Integer,Commit> commitMap = new ConcurrentHashMap<Integer,Commit>();
    public static ProjectLib PL;
    
	public void startCommit( String filename, byte[] img, String[] sources ) {
		System.err.println( ">>>>>>>> startCommit, commitfileName => "+filename);
		int newCC = commitCounter.incrementAndGet();
		Commit m = new Commit(newCC,filename,img,sources,true);
		System.err.println( ">>>Commit ID"+newCC);
		m.askForApproval(PL);
		commitMap.put(newCC, m);
	}

	
	public static void main ( String args[] ) throws Exception {
		if (args.length != 1) throw new Exception("Need 1 arg: <port>");
		Server srv = new Server();
		PL = new ProjectLib( Integer.parseInt(args[0]), srv );
		
		StateRestore.recover();
		
		// main loop
		while (true) {
			ProjectLib.Message msg = PL.getMessage();
			System.err.println( "!!!!!!!Server: Got message from " + msg.addr );
			MyMessage myMsg = MsgSerializer.deserialize(msg.body);
			System.err.println( "Commit ID : " +myMsg.getCommitID());
			Commit m = commitMap.get(myMsg.getCommitID());
			for (int i : commitMap.keySet()) {
				System.err.println("Contains commit id"+i);
			}
			if (myMsg.msgType.equals(MsgType.RSPAPPROVAL)) {
				System.err.println("Respond received for approval");		
				m.handleUserVote(myMsg);
			}
			else if (myMsg.msgType.equals(MsgType.ACK)) {
				System.err.println("Receive ack from user:"+myMsg.userID);
				m.handleACK(myMsg);
			}
			else {
				System.err.println( "Wrong!Should be respond approval type, type: "+myMsg.msgType.toString());
			}
		}
	}
}

