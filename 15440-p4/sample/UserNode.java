import java.io.File;
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
				System.err.println("======= ASK FOR APPROVAL ========");
				handleAskApproval(myMsg);
				break;
			case COMMIT:
				System.err.println("========= COMMIT RESPONSE =======");
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
			else {
				filesInUse.add(s);
			}
		}
		if (canUse) {
			System.err.println(" NO FILE IN USE ..");
			String[] fNames = new String[myMsg.userFilenames.size()];
			fNames = myMsg.userFilenames.toArray(fNames);

			isApprove = PL.askUser(myMsg.img, fNames);
			System.err.println("User response ======> " + isApprove);
		}
		myMsg.setMsgType(MsgType.RSPAPPROVAL);
		myMsg.setIsApprove(isApprove);
		
		try {
			ProjectLib.Message sendMsg =
					new ProjectLib.Message("Server",MsgSerializer.serialize(myMsg));
			PL.sendMessage(sendMsg);
			System.err.println("sending approval response... approve?=>"+
			isApprove+"  ID:" + myMsg.userID);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private void handleCommitResponse(MyMessage myMsg) {
		if (myMsg.getIsCommit()) { //Commit collage, delete files
			System.err.println("Is committed!!!delete files");
			for (String s:myMsg.userFilenames) {
				if (!filesDeleted.contains(s)) {
					filesDeleted.add(s);
					System.err.println("====files deleted:"+s+" =====");
					File file = new File(s);
					file.delete();
				}
			}
		}
		else { //not commited, abort, files no longer in use
			System.err.println("Not committed");
			for (String s:myMsg.userFilenames) {
				if (!filesInUse.remove(s)) {
					System.err.println("files not in use but try to remove...");
				}
			}
		}
		
	}

	public static void main ( String args[] ) throws Exception {
		if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);
		PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN );
		
	}
}

