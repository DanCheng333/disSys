import java.io.Serializable;
import java.util.ArrayList;

/**
 * My Message protocol, contain all info needed 
 * Message type, commitID, userID, collageName, collageImg, sources
 * @author danc
 *
 */
public class MyMessage implements Serializable{
    int commitID;
    String userID;
	byte[] img;
	String commitFilename;
	String[] sources;
	MsgType msgType;
	ArrayList<String> userFilenames;
	boolean isApprove;
	boolean isCommit;
	public MyMessage(MsgType msgType, int commitID, String userID,
			String commitFilename, byte[] img, String[] sources) {
		this.commitID = commitID;
		this.userID = userID;
		this.msgType = msgType;
		this.commitFilename = commitFilename;
		this.img = img;
		this.sources = sources;
		this.isApprove = false;
		this.isCommit = false;
	}
	
	public void setMsgType(MsgType t) {
		this.msgType = t;
	}
	public void setUserID(String id) {
		this.userID = id;
	}
	
	public void setIsApprove(boolean b) {
		this.isApprove = b;
	}
	
	public boolean getIsApprove() {
		return this.isApprove;
	}
	public void setIsCommit(boolean b) {
		this.isCommit = b;
	}
	
	public boolean getIsCommit() {
		return this.isCommit;
	}
	
	public int getCommitID() {
		return this.commitID;
	}
	
	public void setUserFilenames(ArrayList<String> f) {
		this.userFilenames = f;
	}
	
}
