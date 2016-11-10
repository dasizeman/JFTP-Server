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
		String[] requiredArgs = new String[]{"-port"};
		ParseMap parsedArgs = Parser.Parse(args, requiredArgs);
		
		if (parsedArgs == null) {
			System.out.println("The following arguments are required:");
			System.out.println("-port : port to listen for server connections on.");
			return;
		}
		
		// Set up logging
		RollingLogger.configure();
		Logger logger = Logger.getGlobal();
		
		
		
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
