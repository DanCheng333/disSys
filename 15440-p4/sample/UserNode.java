import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/* Skeleton code for UserNode */

public class UserNode implements ProjectLib.MessageHandling {
	public final String myId;
	public static ProjectLib PL;
	public ConcurrentLinkedQueue<String> filesInUse = new ConcurrentLinkedQueue<String>();
	public ConcurrentLinkedQueue<String> filesDeleted = new ConcurrentLinkedQueue<String>();

	public UserNode( String id ) {
		myId = id;
	}

	public boolean deliverMessage( ProjectLib.Message msg ) {
		System.out.println( myId + ": Got message from " + msg.addr );
		MyMessage myMsg;
		try {
			myMsg = MsgSerializer.deserialize(msg.body);
			switch (myMsg.msgType) {
			case ASKAPPROVAL:
				handleAskApproval(myMsg);
				break;
			case COMMIT:
				handleCommitResponse(myMsg);
				break;
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	private void handleAskApproval(MyMessage myMsg) {
		ArrayList<String> filenames = myMsg.userFilenames;
		boolean isApprove = false;
		boolean canUse = true;
		for (String s:filenames) {
			if (filesDeleted.contains(s) || filesInUse.contains(s)) {
				System.err.println("file already commited or in use.....!!!");
				canUse = false;
			}
		}
		if (canUse) {
			isApprove = PL.askUser(myMsg.img, myMsg.sources);
		}
		myMsg.setMsgType(MsgType.RSPAPPROVAL);
		myMsg.setIsApprove(isApprove);
		
		try {
			ProjectLib.Message sendMsg =
					new ProjectLib.Message(myMsg.userID,MsgSerializer.serialize(myMsg));
			PL.sendMessage(sendMsg);
			System.err.println("sending approval response... approve?=>"+
			isApprove+"  ID:" + myMsg.userID);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private void handleCommitResponse(MyMessage myMsg) {
		System.err.println("handle Commit~~~");
		
	}

	public static void main ( String args[] ) throws Exception {
		if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);
		PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN );
		
	}
}

