import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author danc
 *
 */


public class Server implements ProjectLib.CommitServing  {
	public AtomicInteger commitCounter = new AtomicInteger(0);
	public static ConcurrentHashMap<Integer,Commit> commitMap = new ConcurrentHashMap<Integer,Commit>();
    public static ProjectLib PL;
    
	public void startCommit( String filename, byte[] img, String[] sources ) {
		commitCounter.incrementAndGet();
		Commit m = new Commit(commitCounter.get(),filename,img,sources);
		m.askForApproval(PL);
		commitMap.put(commitCounter.get(), m);
	}
	
	public static void main ( String args[] ) throws Exception {
		if (args.length != 1) throw new Exception("Need 1 arg: <port>");
		Server srv = new Server();
		PL = new ProjectLib( Integer.parseInt(args[0]), srv );
		
		// main loop
		while (true) {
			ProjectLib.Message msg = PL.getMessage();
			System.err.println( "!!!!!!!Server: Got message from " + msg.addr );
			MyMessage myMsg = MsgSerializer.deserialize(msg.body);
			if (myMsg.msgType.equals(MsgType.RSPAPPROVAL)) {
				System.err.println("Respond received for approval");
				Commit m = commitMap.get(myMsg.getCommitID());
				m.handleUserResponse(myMsg,PL);
			}
			else {
				System.err.println( "Wrong!Should be respond approval type, type: "+myMsg.msgType.toString());
			}
		}
	}
}

