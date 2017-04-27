import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

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
	
	public static void recover() {
		System.err.println( ">>>>>>>>>	RECOVER >>>>>>>> ");
		for(int commitCounter = 1; commitCounter < Integer.MAX_VALUE; commitCounter++) {
			String logFileName = Integer.toString(commitCounter)+".LOG";
			File logFile = new File(logFileName);
			if (!logFile.exists()) { //Recover all states
				System.err.println( ">>>>>>>>> END OF RECOVER >>>>>>");
				System.err.println( "");
				System.err.println( "");
				return;
			}
			else {
				Server.commitCounter.set(commitCounter);
				String lastType = "";
				try {
					FileInputStream fis = new FileInputStream(new File(logFileName));
					BufferedReader logReader = new BufferedReader(new InputStreamReader(fis));			
					String line = null;
					int lineNum = 1;
					
					while ((line = logReader.readLine()) != null) {
						String[] content = line.split("=>");
						if (content == null) {
							System.err.println( "no =>"+",this line is:"+line);
							continue;
						}
						lastType = content[0];
						if (lastType.equals(LogType.COLLAGE_NAME.toString())) {
							collageName = content[1];
							System.err.println( "Collage name"+ collageName + " ,line num: " + lineNum);
						}
						if (lastType.equals(LogType.COLLAGE_LEN.toString())) {
							collageLen = Integer.parseInt(content[1]);
							System.err.println( "Collage len"+ collageLen + " ,line num: " + lineNum);
						}
						if (lastType.equals(LogType.ID_SOURCES.toString())) {
							sourcesStr = content[1];
							System.err.println( "sources " + sourcesStr+" ,line num: " + lineNum);
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
						lineNum++;
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				System.err.println( "last type in this commit file" + lastType);
				MyMessage msg = new MyMessage(MsgType.COMMIT, commitCounter, "userID",
						collageName,img, sources);
				
				
				if (lastType.equals(LogType.ALL_ACK.toString())) { //finish
					System.err.println( "nothing crash in this commitID:"+commitCounter);
					continue;
				}
				//need to resend ack
				else if (lastType.equals(LogType.ALL_APPROVE_COMMIT.toString())|| 
						lastType.equals(LogType.DISAPPROVE_ABORT.toString()) 
						|| lastType.equals(LogType.ACK.toString())
						|| lastType.equals(LogType.APPROVE.toString())) {
					System.err.println( "approveNum:"+approveNum);
					System.err.println( "disapproveNum:"+disapproveNum);
					System.err.println( "allApprove:"+allApprove);
					if (approveNum == userNum &&
							disapproveNum == 0 &&
							allApprove) { //logic check
						System.err.println( "****All approve. should resend ack****");
						

						commit.distributeResponse(true, msg);
					}
					else {
						System.err.println( "****abort****");
						commit.distributeResponse(false, msg);
					}
				}
				//abort
				else {
					System.err.println( "****abort****");
					commit.distributeResponse(false, msg);
				}
			}
		}
		
	}

	private static void restoreCommit(int commitCounter) {
		//save byte[] img to a backup file
		FileInputStream f;
		try {
			f = new FileInputStream("collageCommit"+commitCounter);
			img = new byte[collageLen];
			f.read(img);
			f.close();
			String[] sources = sourcesStr.split(",");
			commit = new Commit(commitCounter,collageName,img,sources,false);
			Server.commitMap.put(commitCounter, commit);
			userNum = commit.sourcesMap.size();
			ackNum = 0;
			approveNum = 0;
			disapproveNum = 0;
			allApprove = false;
			System.err.println( "Num of Users : " + userNum);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}
