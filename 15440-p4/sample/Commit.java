import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;

/**
 * 4 userID states for vote
 * 
 * @author danc
 *
 */
enum userIDState {
	APPROVE, NOTAPPROVE, NONE, TIMEOUT
}

/**
 * Commit 1. Handle server commit operations 2. Write log files 3. timeout
 * 
 * @author danc
 *
 */
public class Commit {
	public ConcurrentHashMap<String, ArrayList<String>> sourcesMap = new ConcurrentHashMap<String, ArrayList<String>>();
	public ConcurrentHashMap<String, userIDState> approvalMap = new ConcurrentHashMap<String, userIDState>();
	public ConcurrentHashMap<String, Boolean> ackMap = new ConcurrentHashMap<String, Boolean>();
	int commitID;
	byte[] img;
	String commitFilename;
	String[] sources;
	// For logging
	String logFileName;
	BufferedWriter logWriter;
	String sourcesStr;
	// For timeout
	public final static int TIMEOUT = 6500;

	public Commit(int id, String filename, byte[] img, String[] sources, Boolean notRestore) {
		this.commitID = id;
		this.img = img;
		this.commitFilename = filename;
		this.sources = sources;
		add2SourceMap(sources);
		if (notRestore) {
			// only start a new log file if it is NOT in restore state
			initLog();
		}
	}

	/**
	 * Start a new log file, and backup collage image initial log file has
	 * collageName,collage image length, sources
	 */
	private void initLog() {
		try {
			// save byte[] img to a backup file
			FileOutputStream f = new FileOutputStream("collageCommit" + commitID);
			f.write(img);
			f.close();
			// Init writing log files
			logFileName = commitID + ".LOG";
			FileOutputStream fos = new FileOutputStream(new File(logFileName));
			logWriter = new BufferedWriter(new OutputStreamWriter(fos));
			logWriter.write(LogType.COLLAGE_NAME.toString() + "=>" + this.commitFilename);
			logWriter.newLine();
			logWriter.write(LogType.COLLAGE_LEN.toString() + "=>" + this.img.length);
			logWriter.newLine();
			logWriter.write(LogType.ID_SOURCES.toString() + "=>" + this.sourcesStr);
			logWriter.newLine();
			logWriter.flush();
			Server.PL.fsync();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Write string s to log file
	 * 
	 * @param s
	 */
	private void write2Log(String s) {
		try {
			logWriter.write(s);
			logWriter.newLine();
			logWriter.flush();
			Server.PL.fsync();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Initialize sourceMap, approvalMap and ackMap
	 * 
	 * @param src
	 */
	private void add2SourceMap(String[] src) {
		StringBuilder sb = new StringBuilder();
		for (String s : src) {
			sb.append(s + ",");
			try {
				String[] comb = s.split(":");
				String userID = comb[0];

				approvalMap.put(userID, userIDState.NONE);
				ackMap.put(userID, false);

				String fileName = comb[1];
				if (!sourcesMap.containsKey(userID)) {
					ArrayList<String> l = new ArrayList<String>();
					l.add(fileName);
					sourcesMap.put(userID, l);
				} else {
					ArrayList<String> l = sourcesMap.get(userID);
					l.add(fileName);
					sourcesMap.put(userID, l);
				}
			} catch (PatternSyntaxException e) {
				e.printStackTrace();
			}

		}
		// delete the last ","
		sb.deleteCharAt(sb.length() - 1);
		this.sourcesStr = sb.toString();
	}

	/**
	 * Ask userNodes to vote
	 * 
	 * @param pL
	 */
	public void askForApproval(ProjectLib pL) {
		for (String userID : sourcesMap.keySet()) {
			MyMessage msg = new MyMessage(MsgType.ASKAPPROVAL, commitID, userID, commitFilename, img, sources);
			msg.setUserFilenames(sourcesMap.get(userID));
			try {
				ProjectLib.Message sendMsg = new ProjectLib.Message(userID, MsgSerializer.serialize(msg));
				pL.sendMessage(sendMsg);
				// Keep track of vote, if timeout, abort
				Thread t = new Thread(new VoteTimeOutCheck(userID, msg));
				t.start();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Handle Vote all YES => Commit; one No, or timeout => Abort Distribute
	 * decisions to users
	 * 
	 * @param myMsg
	 * @param pL
	 */
	public void handleUserVote(MyMessage myMsg) {
		// Only check approval if this vote is not timeout yet
		if (!approvalMap.get(myMsg.userID).equals(userIDState.TIMEOUT)) {
			if (myMsg.getIsApprove()) {
				this.approvalMap.put(myMsg.userID, userIDState.APPROVE);
				write2Log(LogType.APPROVE.toString() + "=>" + myMsg.userID);
			} else {
				this.approvalMap.put(myMsg.userID, userIDState.NOTAPPROVE);
				write2Log(LogType.DISAPPROVE.toString() + "=>" + myMsg.userID);
			}

			if (notAllUsersApprove()) {
				distributeResponse(false, myMsg);
			}
			if (allUsersApprove()) { // if all users approve the commit
				distributeResponse(true, myMsg);
			}
		}

	}

	/**
	 * Distribute responses to all the usernode, commit if b is true, else abort
	 * 
	 * @param b
	 * @param myMsg
	 */
	public void distributeResponse(boolean b, MyMessage myMsg) {
		myMsg.setMsgType(MsgType.COMMIT);
		myMsg.setIsCommit(b);
		if (b) {
			try {
				File f = new File(this.commitFilename);
				if (!f.exists()) {
					FileOutputStream fos = new FileOutputStream(this.commitFilename);
					fos.write(this.img);
					fos.close();
				}
				// Send commit response back to user
				try {
					for (String id : sourcesMap.keySet()) {
						myMsg.setUserFilenames(sourcesMap.get(id));
						myMsg.setUserID(id);
						ProjectLib.Message sendMsg = new ProjectLib.Message(id, MsgSerializer.serialize(myMsg));
						Server.PL.sendMessage(sendMsg);
						// If ack timeout, resend commit ack
						Thread t = new Thread(new AckTimeOutCheck(id, sendMsg));
						t.start();

					}
				} catch (Exception e) {
					e.printStackTrace();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			write2Log(LogType.ALL_APPROVE_COMMIT.toString() + "=>");

		} else {
			try {
				for (String id : sourcesMap.keySet()) {
					myMsg.setUserFilenames(sourcesMap.get(id));
					myMsg.setUserID(id);
					ProjectLib.Message sendMsg = new ProjectLib.Message(id, MsgSerializer.serialize(myMsg));
					Server.PL.sendMessage(sendMsg);
					// If ack timeout, resend abort ack
					Thread t = new Thread(new AckTimeOutCheck(id, sendMsg));
					t.start();

				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			write2Log(LogType.DISAPPROVE_ABORT.toString() + "=>");
		}
	}

	/**
	 * Handle ack response, keep track of all ack in ackMap and log file if all
	 * ack, write to log file
	 * 
	 * @param myMsg
	 */
	public void handleACK(MyMessage myMsg) {
		ackMap.put(myMsg.userID, true);
		write2Log(LogType.ACK.toString() + "=>" + myMsg.userID);
		if (allUserACK()) {
			write2Log(LogType.ALL_ACK.toString() + "=>");
		}
	}

	/**
	 * If all users ack, return true else false
	 * 
	 * @return
	 */
	private boolean allUserACK() {
		for (Entry<String, Boolean> s : this.ackMap.entrySet()) {
			if (!s.getValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * If one user disapprove, return true, else return false
	 * 
	 * @return
	 */
	private boolean notAllUsersApprove() {
		for (Entry<String, userIDState> s : this.approvalMap.entrySet()) {
			if (s.getValue().equals(userIDState.NOTAPPROVE)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * if all users approve, return true, else false
	 * 
	 * @return
	 */
	private boolean allUsersApprove() {
		for (Entry<String, userIDState> s : this.approvalMap.entrySet()) {
			if (!s.getValue().equals(userIDState.APPROVE)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Vote Time out thread
	 */
	public class VoteTimeOutCheck implements Runnable {

		String userID;
		MyMessage msg;

		VoteTimeOutCheck(String id, MyMessage m) {
			this.userID = id;
			this.msg = m;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(TIMEOUT);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (approvalMap.get(userID).equals(userIDState.NONE)) {
				// No vote response from user, timeout, abort commit
				approvalMap.put(userID, userIDState.TIMEOUT);
				distributeResponse(false, msg);

			} else {
				return;
			}
		}
	}

	/**
	 * ACK Time out thread
	 */
	public class AckTimeOutCheck implements Runnable {

		String userID;
		ProjectLib.Message msg;

		AckTimeOutCheck(String id, ProjectLib.Message sendMsg) {
			this.userID = id;
			this.msg = sendMsg;
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(TIMEOUT);
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (!ackMap.get(userID)) {
					// No ack response from user, timeout, resend ack
					Server.PL.sendMessage(msg);

				} else {
					break;
				}
			}
			return;
		}
	}

}
