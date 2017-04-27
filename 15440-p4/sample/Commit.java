import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;

enum userIDState {
	APPROVE, NOTAPPROVE, NONE
}

public class Commit {
	public ConcurrentHashMap<String, ArrayList<String>> sourcesMap = new ConcurrentHashMap<String, ArrayList<String>>();
	public ConcurrentHashMap<String, userIDState> approvalMap = new ConcurrentHashMap<String, userIDState>();
	public ConcurrentHashMap<String, Boolean> ackMap = new ConcurrentHashMap<String, Boolean>();
	int commitID;
	byte[] img;
	String commitFilename;
	String[] sources;
	//For logging
	String logFileName;
	BufferedWriter logWriter;
	String sourcesStr;
	String idStr;

	public Commit(int id, String filename, byte[] img, String[] sources) {
		this.commitID = id;
		this.img = img;
		this.commitFilename = filename;
		this.sources = sources;
		add2SourceMap(sources);
		initLog();
	}
	
	private void initLog() {
		try
		{
			logFileName = commitID+".LOG";
			FileOutputStream fos = new FileOutputStream(new File(logFileName));
			logWriter = new BufferedWriter(new OutputStreamWriter(fos));
			logWriter.write("COLLAGE_NAME:"+this.commitFilename);
			logWriter.newLine();
			logWriter.write("USERID:"+this.idStr);
			logWriter.newLine();
			logWriter.write("SOURCES:"+this.sourcesStr);
			logWriter.newLine();
			logWriter.flush();
			Server.PL.fsync();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}

	/**
	 * Initialize sourceMap, approvalMap and ackMap
	 * and string for id and sources
	 * @param src
	 */
	private void add2SourceMap(String[] src) {
		StringBuilder sourcesSB = new StringBuilder();
		StringBuilder idSB = new StringBuilder();
		for (String s : src) {
			try {
				String[] comb = s.split(":");
				String userID = comb[0];
				
				approvalMap.put(userID, userIDState.NONE);
				ackMap.put(userID, false);
				
				String fileName = comb[1];
				
				sourcesSB.append(fileName+",");
				idSB.append(userID+",");
				
				System.err.println("UserID:" + userID + ", fileName:" + fileName);
				if (!sourcesMap.containsKey(userID)) {
					ArrayList<String> l = new ArrayList<String>();
					l.add(fileName);
					sourcesMap.put(userID, l);
				} else {
					ArrayList<String> l = sourcesMap.get(userID);
					if (!l.add(fileName)) {
						System.err.println("file already in UserID,file:" + fileName);
					}
					sourcesMap.put(userID, l);
				}
			} catch (PatternSyntaxException e) {
				System.err.println("Source format is wrong.." + s);
			}

		}
		//delete the last ","
		sourcesSB.deleteCharAt(sourcesSB.length()-1);
		idSB.deleteCharAt(idSB.length()-1);
		this.sourcesStr = sourcesSB.toString();
		this.idStr = idSB.toString();
	}

	/**
	 * Ask userNode to vote
	 * @param pL
	 */
	public void askForApproval(ProjectLib pL) {
		for (String userID : sourcesMap.keySet()) {
			MyMessage msg = new MyMessage(MsgType.ASKAPPROVAL, commitID, userID, commitFilename, img, sources);
			msg.setUserFilenames(sourcesMap.get(userID));
			try {
				ProjectLib.Message sendMsg = new ProjectLib.Message(userID, MsgSerializer.serialize(msg));
				pL.sendMessage(sendMsg);
				System.err.println("Asking for approval...");
				System.err.println("Msg sent to userID:" + userID);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
	
	/**
	 * Handle Vote
	 * all YES => Commit
	 * one No, or timeout => Abort
	 * Distribute decisions to users
	 * @param myMsg
	 * @param pL
	 */
	public void handleUserVote(MyMessage myMsg, ProjectLib pL) {
		if (myMsg.getIsApprove()) {
			this.approvalMap.put(myMsg.userID, userIDState.APPROVE);
		} else {
			this.approvalMap.put(myMsg.userID, userIDState.NOTAPPROVE);
		}

		myMsg.setMsgType(MsgType.COMMIT);
		if (notAllUsersApprove()) {
			// Send commit response back to user
			myMsg.setIsCommit(false);
			try {
				for (String id : sourcesMap.keySet()) {
					myMsg.setUserFilenames(sourcesMap.get(id));
					myMsg.setUserID(id);
					ProjectLib.Message sendMsg = new ProjectLib.Message(id, MsgSerializer.serialize(myMsg));
					pL.sendMessage(sendMsg);
					System.err.println("Tell user is NOT committed, id:" + myMsg.userID);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		if (allUsersApprove()) { // if all users approve the commit
			try {
				System.err.println("Commit collage, Write to files.....commitFilename:" + this.commitFilename);
				FileOutputStream fos = new FileOutputStream(this.commitFilename);
				fos.write(this.img);
				fos.close();

				// Send commit response back to user
				myMsg.setIsCommit(true);
				try {
					for (String id : sourcesMap.keySet()) {
						myMsg.setUserFilenames(sourcesMap.get(id));
						myMsg.setUserID(id);
						ProjectLib.Message sendMsg = new ProjectLib.Message(id, MsgSerializer.serialize(myMsg));
						pL.sendMessage(sendMsg);
						System.err.println("Tell user is committed, id:" + myMsg.userID);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public void handleACK(MyMessage myMsg) {
		ackMap.put(myMsg.userID, true);
		if (allUserACK()) {
			System.err.println(" ====== END All userNode ack ====");
		}
	}
	
	private boolean allUserACK() {
		for (Entry<String, Boolean> s : this.ackMap.entrySet()) {
			if (!s.getValue()) {
				System.err.println("Not all ack yet...");
				return false;
			}
		}
		System.err.println(" All ack! ");
		return true;
	}

	private boolean notAllUsersApprove() {
		for (Entry<String, userIDState> s : this.approvalMap.entrySet()) {
			if (s.getValue().equals(userIDState.NOTAPPROVE)) {
				System.err.println("One user disapprove ID:" + s.getKey());
				return true;
			}
		}
		return false;
	}

	private boolean allUsersApprove() {
		for (Entry<String, userIDState> s : this.approvalMap.entrySet()) {
			if (!s.getValue().equals(userIDState.APPROVE)) {
				System.err.println("Not all approve yet...");
				return false;
			}
		}
		System.err.println(" All approved! ");
		return true;
	}


}
