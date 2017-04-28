import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * State Restore based on log files Go through all log files, update commitMap
 * in Server Only commit and resend ack if users all approve, else abort
 * 
 * @author danc
 *
 */
public class StateRestore {
	static String collageName;
	static String sourcesStr;
	static String[] sources;
	static byte[] img;
	static int collageLen;
	static int userNum;
	static int approveNum;
	static int disapproveNum;
	static int ackNum;
	static boolean allApprove;
	static Commit commit;

	/**
	 * Recover states based on log files
	 */
	public static void recover() {
		for (int commitCounter = 1; commitCounter < Integer.MAX_VALUE; commitCounter++) {
			String logFileName = Integer.toString(commitCounter) + ".LOG";
			File logFile = new File(logFileName);
			if (!logFile.exists()) { // Recover all states, no more log file
				return;
			} else { // Recover state based on log file
				Server.commitCounter.set(commitCounter);
				String lastType = "";
				try {
					// Read log files line by line
					FileInputStream fis = new FileInputStream(new File(logFileName));
					BufferedReader logReader = new BufferedReader(new InputStreamReader(fis));
					String line = null;
					while ((line = logReader.readLine()) != null) {
						String[] content = line.split("=>");
						if (content == null) {
							// empty or not fully written line
							continue;
						}
						lastType = content[0];

						if (lastType.equals(LogType.COLLAGE_NAME.toString())) {
							collageName = content[1];
						}
						if (lastType.equals(LogType.COLLAGE_LEN.toString())) {
							collageLen = Integer.parseInt(content[1]);
						}
						if (lastType.equals(LogType.ID_SOURCES.toString())) {
							sourcesStr = content[1];
							restoreCommit(commitCounter);
							commit.logWriter = new BufferedWriter(new FileWriter(logFileName, true));

						}
						if (lastType.equals(LogType.APPROVE.toString())) {
							approveNum++;
						}
						if (lastType.equals(LogType.DISAPPROVE.toString())) {
							disapproveNum++;
						}
						if (lastType.equals(LogType.ALL_APPROVE_COMMIT.toString())) {
							allApprove = true;
						}
						if (lastType.equals(LogType.ACK.toString())) {
							ackNum++;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Abort or send ack based on last log
				MyMessage msg = new MyMessage(MsgType.COMMIT, commitCounter, "userID", collageName, img, sources);

				if (lastType.equals(LogType.ALL_ACK.toString())) { // finish do
																	// nothing
					continue;
				}
				// Send ack
				else if (lastType.equals(LogType.ALL_APPROVE_COMMIT.toString())
						|| lastType.equals(LogType.DISAPPROVE_ABORT.toString())
						|| lastType.equals(LogType.ACK.toString()) || lastType.equals(LogType.APPROVE.toString())) {
					// All approve, send commit ack
					if ((approveNum == userNum && disapproveNum == 0) || allApprove) {
						commit.distributeResponse(true, msg);
					} else {
						// send abort
						commit.distributeResponse(false, msg);
					}
				}
				// abort
				else {
					commit.distributeResponse(false, msg);
				}
			}
		}

	}

	/**
	 * Restore commit variables in Server
	 * 
	 * @param commitCounter
	 */
	private static void restoreCommit(int commitCounter) {
		FileInputStream f;
		try {
			// Get collage backup
			f = new FileInputStream("collageCommit" + commitCounter);
			img = new byte[collageLen];
			f.read(img);
			f.close();
			// Restore commitMap
			String[] sources = sourcesStr.split(",");
			commit = new Commit(commitCounter, collageName, img, sources, false);
			Server.commitMap.put(commitCounter, commit);
			// InitCounter for deciding restore state
			userNum = commit.sourcesMap.size();
			ackNum = 0;
			approveNum = 0;
			disapproveNum = 0;
			allApprove = false;

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
