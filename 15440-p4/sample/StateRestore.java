import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class StateRestore {
	static String collageName;
	static String sources;
	static int collageLen;
	
	public static void recover() {
		for(int commitCounter = 1; commitCounter < Integer.MAX_VALUE; commitCounter++) {
			String logFileName = Integer.toString(commitCounter)+".LOG";
			File logFile = new File(logFileName);
			if (!logFile.exists()) { //Recover all states
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
							sources = content[1];
							System.err.println( "sources " + sources+" ,line num: " + lineNum);
							restoreCommit(commitCounter);			
						}
						if (lastType.equals(LogType.APPROVE.toString())) {
							
						}
						if (lastType.equals(LogType.DISAPPROVE.toString())) {
							
						}
						lineNum++;
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.err.println( "last type in this commit file" + lastType);
				
				if (lastType.equals(LogType.ALL_ACK.toString())) { //finish
					System.err.println( "nothing crash in this commitID:"+commitCounter);
					continue;
				}
				//need to resend ack
				else if (lastType.equals(LogType.ALL_APPROVE_COMMIT.toString())|| 
						lastType.equals(LogType.DISAPPROVE_ABORT.toString()) 
						|| lastType.equals(LogType.ACK.toString())) {
					
				}
				//abort
				else {
					
				}
			}
		}
		
	}

	private static void restoreCommit(int commitCounter) {
		//save byte[] img to a backup file
		FileInputStream f;
		try {
			f = new FileInputStream("collageCommit"+commitCounter);
			byte[] img = new byte[collageLen];
			f.read(img);
			f.close();
			String[] sourcesArr = sources.split(",");
			Commit m = new Commit(commitCounter,collageName,img,sourcesArr,false);
			Server.commitMap.put(commitCounter, m);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}
