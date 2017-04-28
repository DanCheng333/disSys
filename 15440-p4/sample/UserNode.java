import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * UserNode
 * Receive: ask for approval, commit response
 * Send: vote, ack
 * @author danc
 *
 */
public class UserNode implements ProjectLib.MessageHandling {
	public final String myId;
	public static ProjectLib PL;
	public ConcurrentLinkedQueue<String> filesInUse = new ConcurrentLinkedQueue<String>();
	public ConcurrentLinkedQueue<String> filesDeleted = new ConcurrentLinkedQueue<String>();

	public UserNode(String id) {
		myId = id;
	}

	/**
	 * Hanlde incoming msg from server, either asking for approval, commit or
	 * abort
	 */
	public boolean deliverMessage(ProjectLib.Message msg) {
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

	/**
	 * Handle ask for approval, lock files if in use
	 * 
	 * @param myMsg
	 */
	private void handleAskApproval(MyMessage myMsg) {
		ArrayList<String> filenames = myMsg.userFilenames;
		boolean isApprove = false;
		boolean canUse = true;
		for (String s : filenames) {
			// file are locked
			if (filesDeleted.contains(s) || filesInUse.contains(s)) {
				canUse = false;
			} else { // lock file
				filesInUse.add(s);
			}
		}
		if (canUse) { // files are not locked
			String[] fNames = new String[myMsg.userFilenames.size()];
			fNames = myMsg.userFilenames.toArray(fNames);
			isApprove = PL.askUser(myMsg.img, fNames);
		}

		// Send vote back to server
		myMsg.setMsgType(MsgType.RSPAPPROVAL);
		myMsg.setIsApprove(isApprove);

		try {
			ProjectLib.Message sendMsg = new ProjectLib.Message("Server", MsgSerializer.serialize(myMsg));
			PL.sendMessage(sendMsg);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Hanlde commit response, delete files if commit, release lock on files if
	 * abort
	 * 
	 * @param myMsg
	 */
	private void handleCommitResponse(MyMessage myMsg) {
		if (myMsg.getIsCommit()) { // Commit, delete files
			for (String s : myMsg.userFilenames) {
				if (!filesDeleted.contains(s)) {
					filesDeleted.add(s);
					File file = new File(s);
					file.delete();
				}
			}
		} else { // Abort, files no longer in use, release lock
			for (String s : myMsg.userFilenames) {
				if (!filesInUse.remove(s)) { // files already removed
					continue;
				}
			}
		}
		// Send ack back to server
		myMsg.setMsgType(MsgType.ACK);
		try {
			ProjectLib.Message sendMsg = new ProjectLib.Message("Server", MsgSerializer.serialize(myMsg));
			PL.sendMessage(sendMsg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String args[]) throws Exception {
		if (args.length != 2)
			throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);
		PL = new ProjectLib(Integer.parseInt(args[0]), args[1], UN);

	}
}
