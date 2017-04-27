import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class StateRestore {

	public static void recover() {
		for(int commitCounter = 1; commitCounter < Integer.MAX_VALUE; commitCounter++) {
			String logFileName = Integer.toString(commitCounter)+".LOG";
			File logFile = new File(logFileName);
			if (!logFile.exists()) { //Recover all states
				return;
			}
			else {
				try {
					FileInputStream fis = new FileInputStream(new File(logFileName));
					BufferedReader logReader = new BufferedReader(new InputStreamReader(fis));
					
					String line = null;
					while ((line = logReader.readLine()) != null) {
						String[] content = line.split("=>");
						System.err.println("content:"+content[0]);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}

}
