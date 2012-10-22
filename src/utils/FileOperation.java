/*
 * Created on Jul 22, 2005
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package utils;

/**
 * @author Alex
 *
 * Contains some normal file operation functions.
 * 
 * Note that for functions dealing with exec, I AVOIDED Runtime.exec() because
 * I found the following not working (tested under UNIX environment):
 * - it cannot execute any system commands
 * - setting any environment variable will erase all default environment that came
 *   with the parent program (this java program)
 * 
 * Instead, ProcessBuilder does not have any of the above limitations and hence I
 * used that instead.
 */

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder;
import java.lang.Process;

public class FileOperation {
	
	public static void mkdir(String path) {
		try {
			File dir = new File(path);
			if (!dir.exists()) {
				dir.mkdirs();
			}
		} catch (SecurityException se) {
			System.err.println("(FileOperation): Exception occured while making directory " 
				+ path + ":\n" + se.getMessage());
		}
	}
	
	
	public static void asciiCopy(String fromFile, String toFile) {
		// Make a directory for the destination file if it does not exist
		String toParentDirectories = toFile.substring(0, toFile.lastIndexOf("/"));
		mkdir(toParentDirectories);
		
		// Do the actual copying
		try {
			BufferedReader reader = new BufferedReader(new FileReader(fromFile));
			BufferedWriter writer = new BufferedWriter(new FileWriter(toFile, false));
			String line = null;
						
			while ( (line = reader.readLine()) != null) {
				writer.write(line);
				writer.newLine();
			}
			
			writer.flush();
			reader.close();
			writer.close();
		} catch (Exception e) {
			System.err.println("(FileOperation): Exception occured while copy ascii file from "
				+ fromFile + " to " + toFile);
		}
	}
	
	public static String getLastNLines(String filePath, int n) {
		String returnBuf = "";
		String line;
		int lineCount = getTotalLineCount(filePath);
		
		// max function is there in case n is greater than lineCount
		int linesToSkip = Math.max(lineCount - n, 0);
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(filePath));
	
			// Skip to the desired line
			for (int i = 0; i < linesToSkip; i++) {
				reader.readLine();
			}
			
			// Now read the lines and put them into a String buffer to be returned
			while ( (line = reader.readLine()) != null) {
				returnBuf += line + "\n";
			}
		} catch (Exception e) {
			System.err.println("(FileOperation): Exception occured while getting line count from file "
				+ filePath);
		}

		return returnBuf;
	}
	
	
	public static int getTotalLineCount(String filePath) {
		int n = 0;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(filePath));
			while (reader.readLine() != null)
				n++;
		} catch (Exception e) {
			System.err.println("(FileOperation): Exception occured while getting line count from file "
				+ filePath);
		}
		return n;
	}
	
	
	public static String getFilename(String path) {
		return (path.lastIndexOf("/") >= 0)
			? path.substring(path.lastIndexOf("/"))
			: path;
	}


	/**
	 * Tests if a file (locally for over http) exists
	 * @param path
	 * @return
	 */
	public static boolean exists(String path){
		if (path.startsWith("http://")) {
			try {
			    HttpURLConnection.setFollowRedirects(false);
			    // note : you may also need
			    //        HttpURLConnection.setInstanceFollowRedirects(false)
			    HttpURLConnection con =
			    	(HttpURLConnection) new URL(path).openConnection();
			    con.setRequestMethod("HEAD");
			    return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
		    } catch (Exception e) {
		    	return false;
		    }
		} else {
			return new File(path).exists();
		}
	}
	
	/**
	 * Executes given command with the provided environment variables (on top of 
	 * the environment variables with this program) and working directory (i.e.
	 * this can be the directory in which the invoked script resides)
	 * @param command
	 * @return
	 */
	public static int syncExecute(String command, Map envMap, File path) {
		return syncExecute(command.split(" "), envMap, path);
	}

	/**
	 * Executes given command in a string array
	 * @param command
	 * @return
	 */
	public static int syncExecute(String[] command, Map envMap, File path) {
		ProcessBuilder pb = new ProcessBuilder(Arrays.asList(command));
		pb.redirectErrorStream(true);
		Map defaultEnvMap = pb.environment();
		if (envMap != null && !envMap.isEmpty())
			defaultEnvMap.putAll(envMap);
		if (path != null)
			pb.directory(path);
		
		try {
			String buffer = null;
			Process p = pb.start();
			BufferedReader outputReader = new BufferedReader(
					new InputStreamReader(p.getInputStream()));
			while ( (buffer = outputReader.readLine()) != null) {
				System.out.println(buffer);
			}
			outputReader.close();
			outputReader = null;
			
			return p.waitFor();
		} catch (Exception e) {
			System.err.println("(FileOperation): EXCEPTION: " + e.getMessage());
			return 911;
		}
	}
	
	
	
	/**
	 * Executes the given command in the background.
	 * @param command
	 */
	public static void asyncExecute(String command, Map envMap, File path) {
		asyncExecute(command.split(" "), envMap, path);
	}
	
	
	/**
	 * Executes the given command in the background.  This does not show the script output
	 * because this would sync the child thread with ours.
	 * @param command
	 */
	public static void asyncExecute(String[] command, Map envMap, File path) {
		ProcessBuilder pb = new ProcessBuilder(Arrays.asList(command));
		Map defaultEnvMap = pb.environment();
		if (envMap != null && !envMap.isEmpty())
			defaultEnvMap.putAll(envMap);
		if (path != null)
			pb.directory(path);
		
		try {
			Process p = pb.start();
		} catch (Exception e) {
			System.err.println("(FileOperation): EXCEPTION: " + e.getMessage());
		}
	}
	
	/**
	 * Give it a file and it will display all of its contents on the screen.  Good for
	 * showing text files such as help files.
	 * 
	 * @param filePath
	 * @return
	 */
	public static boolean displayFile(String filePath) {
		if (!new File(filePath).exists()) {
			System.out.println("FileOperation:displayFile() File not found at " 
					+ filePath);
			return false;
		}
		
		try {
			BufferedReader in = new BufferedReader(new FileReader(filePath));
			String line;
		
			while ( (line =in.readLine()) != null )
				System.out.println(line);
		} catch (IOException ignored) {
			System.out.println("ERROR: FileOperation:displayFile()- Exception occurred" 
					+ " while reading file " + filePath);
			return false;
		}

		System.out.println("\n");
		return true;
	}
}
