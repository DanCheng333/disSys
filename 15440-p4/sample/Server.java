import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server
 * Receive: vote, ack
 * Send: ask for approval, commit response
 * @author danc
 *
 */


public class Server implements ProjectLib.CommitServing  {
	public static AtomicInteger commitCounter = new AtomicInteger(0);
	public static ConcurrentHashMap<Integer,Commit> commitMap = new ConcurrentHashMap<Integer,Commit>();
    public static ProjectLib PL;
    
	public void startCommit( String filename, byte[] img, String[] sources ) {
		int newCC = commitCounter.incrementAndGet();
		Commit m = new Commit(newCC,filename,img,sources,true);
		m.askForApproval(PL);
		commitMap.put(newCC, m);
	}

	
	public static void main ( String args[] ) throws Exception {
		if (args.length != 1) throw new Exception("Need 1 arg: <port>");
		Server srv = new Server();
		PL = new ProjectLib( Integer.parseInt(args[0]), srv );
		
		//Recover crash state based on log files
		StateRestore.recover();
		
		// main loop
		while (true) {
			ProjectLib.Message msg = PL.getMessage();
			MyMessage myMsg = MsgSerializer.deserialize(msg.body);
			Commit m = commitMap.get(myMsg.getCommitID());
			if (myMsg.msgType.equals(MsgType.RSPAPPROVAL)) {
				m.handleUserVote(myMsg);
			}
			else if (myMsg.msgType.equals(MsgType.ACK)) {
				m.handleACK(myMsg);
			}
			else {
				continue;
			}
		}
	}
}

