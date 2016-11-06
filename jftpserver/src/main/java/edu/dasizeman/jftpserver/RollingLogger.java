package edu.dasizeman.jftpserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;

public class RollingLogger {
	private static final String LOG_FILE_NAME = "jftpd.log";
	private static final String OLD_LOG_FILE_FMT = "%s.%03d";
	private static final String OLD_LOG_FILE_PATTERN = String.format("^%s\\.(\\d+)$", LOG_FILE_NAME);
	private static final String NUM_LOG_CONF_KEY = "numlogfiles";
	private static final String LOG_DIR_CONF_KEY = "logdirectory";
	private static final Logger logger = Logger.getGlobal();
	
	
	public static void configure() {
		LogManager.getLogManager().reset();

		// We configure the log path from this project's config file
		int numLogs;
		try {
			ConfigurationFile configFile = ConfigurationFile.getInstance();
			
			// Get the number of logfiles allowed
			String numLogString = configFile.getConfigValue(NUM_LOG_CONF_KEY);
			numLogs = Integer.parseInt(numLogString);
			
			if (numLogs <= 0) {
				throw new LineFormatException(String.format("%s must be at least 1.", NUM_LOG_CONF_KEY));
			}
			
			// Get the log directory path
			String logDirPath = configFile.getConfigValue(LOG_DIR_CONF_KEY);
			
			// Get the log directory and ensure it exists
			File logDir = FileUtils.getFile(logDirPath);
			
			if (!logDir.exists() || !logDir.isDirectory()) {
				throw new FileNotFoundException("Could not open logging directory.");
			}

			File logFile = FileUtils.getFile(logDir, LOG_FILE_NAME);
			
			// Roll if needed
			if (logFile.exists()) {
				
				// First find all existing log files and increment their suffixes
				// We ensure results are sorted in reverse alphabetical order, so we start from the oldest file
				ArrayList<File> oldFiles = new ArrayList<File>(FileUtils.listFiles(logDir, new RegexFileFilter(OLD_LOG_FILE_PATTERN), null));
				Collections.sort(oldFiles);
				Collections.reverse(oldFiles);
				for (File file : oldFiles) {
					// Get the suffix
					Matcher suffixMatcher = Pattern.compile(OLD_LOG_FILE_PATTERN).matcher(file.getName());
					if (suffixMatcher.find() && suffixMatcher.group(1) != null) {
						int suffix = Integer.parseInt(suffixMatcher.group(1));
						suffix++;
						
						// Don't keep more than the configured number of logs
						if (suffix < numLogs) {
							File newFile = new File(String.format(OLD_LOG_FILE_FMT, LOG_FILE_NAME, suffix));
							
							// If a file with this name already exists, it's because we previously had the
							// number of log files configured higher, so we can overwrite it
							if (newFile.exists())
								FileUtils.forceDelete(newFile);
							
							FileUtils.moveFile(file, newFile);
						}
					}
				}
				// Now add the 0 suffix to the most recent log file
				FileUtils.moveFile(logFile, new File(String.format(OLD_LOG_FILE_FMT, LOG_FILE_NAME, 0)));
			}
			
			// Actually open the log file
			FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath());
			fileHandler.setFormatter(new SimplerFormatter());
			fileHandler.setLevel(Level.ALL);
			logger.addHandler(fileHandler);
			logger.setLevel(Level.ALL);
			
		} catch (LineFormatException | IOException | NumberFormatException e) {
			System.out.println(e);
			System.exit(1);
		}
		
		
	}

}
