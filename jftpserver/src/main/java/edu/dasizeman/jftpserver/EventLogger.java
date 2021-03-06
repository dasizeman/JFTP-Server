package edu.dasizeman.jftpserver;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Wrapper for logging various events
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public class EventLogger {
	private static final String LOG_FORMAT = "[%s] %s: %s";

	public static void logConnectionException(Logger logger, Socket socket, Exception e) {
		logger.log(Level.SEVERE, String.format(LOG_FORMAT, getConnectionIDString(socket),"Connection error", e.getMessage()));
	}
	
	public static void logGeneralException(Logger logger, String context,Exception e) {
		logger.log(Level.SEVERE, String.format(LOG_FORMAT, context, e.getClass().getName(), e.getMessage()));
	}
	
	public static void logEvent(Logger logger, String context, String message) {
		logger.log(Level.INFO, String.format(LOG_FORMAT, "-", context,message));
	}
	
	public static void logListenException(Logger logger, ServerSocket socket, Exception e) {
		logger.log(Level.SEVERE, String.format(LOG_FORMAT, getListenIDString(socket),"Listen error", e.getMessage()));
	}
	
	public static void logNetworkDataSent(Logger logger, Socket socket, String data) {
		logger.log(Level.FINE, String.format(LOG_FORMAT, getConnectionIDString(socket), "Sending", data));
	}
	
	public static void logNetworkDataReceived(Logger logger, Socket socket, String data) {
		logger.log(Level.FINE, String.format(LOG_FORMAT, getConnectionIDString(socket), "Receiving", data));
	}
	
	private static String getConnectionIDString(Socket socket) {
		String address = socket.getInetAddress().getHostAddress();
		String port = Integer.toString(socket.getPort());
		
		return String.format("%s:%s", address, port);
	}
	
	private static String getListenIDString(ServerSocket socket) {
		if (socket == null)
			return "-";
		String address = socket.getInetAddress().getHostAddress();
		String port = Integer.toString(socket.getLocalPort());
		
		return String.format("%s:%s", address, port);
	}
}
