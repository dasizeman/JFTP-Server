package edu.dasizeman.jftpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for a simple FTP server
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 */
public class Main {

	public static void main(String[] args) {
		String[] requiredArgs = new String[]{"-log", "-port"};
		ParseMap parsedArgs = Parser.Parse(args, requiredArgs);
		
		if (parsedArgs == null) {
			System.out.println("The following arguments are required:");
			System.out.println("-log : path to a log file.");
			System.out.println("-port : port to listen for server connections on.");
			return;
		}
		
		// Set up logging
		RollingLogger.configure();
		Logger logger = Logger.getGlobal();
		
		/*
		FileHandler logFile = null;
		StreamHandler consoleLog = null;
		// Open the log file
		try {
			logFile = new FileHandler(parsedArgs.get("-log"), true);
		} catch (SecurityException | IOException e) {
			System.out.println("Could not open log file.");
			System.exit(1);
		}
		
		SimplerFormatter simpleFormatter = new SimplerFormatter();
		
		// Set up console logging to flush to the console immediately
		consoleLog = new StreamHandler(System.out, simpleFormatter) {
	        @Override
	        public synchronized void publish(final LogRecord record) {
	            super.publish(record);
	            flush();
	        }
	    };
	    
	    // Set how much each log target should log and how it should be formatted
		consoleLog.setLevel(Level.ALL);
		logFile.setFormatter(simpleFormatter);
		logFile.setLevel(Level.ALL);
		
		
		logger.addHandler(logFile);
		//logger.addHandler(consoleLog);
		
		// Log everything to some source
		logger.setLevel(Level.ALL);
		
		//System.out.println(FileUtils.getUserDirectoryPath());
		
		/*
		credManager.addCredential("dave", "magic");
		try {
			credManager.writeCredentialFile();
		} catch (FileNotFoundException e1) {
			System.out.println(e1);
		}
		*/
		
		
		// Create a server socket to listen for connections
		int port = 0;
		try {
			port = Integer.parseInt(parsedArgs.get("-port"));
		} catch (NumberFormatException e) {
			logger.log(Level.SEVERE, e.toString());
			return;
		}
		
		ServerSocket listenSocket = null;
		try {
			listenSocket = new ServerSocket(port);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.toString());
			return;
		}
		
		// Listen to and handle connections forever
		while (true) {
			try {
				TCPConnectionFactory.listenAndHandle(listenSocket, new ControlConnectionHandler());
			} catch (IOException e) {
				logger.log(Level.SEVERE, e.toString());
			}
		}

	}

}
