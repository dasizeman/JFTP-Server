package edu.dasizeman.jftpserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * Handler for an FTP control connection
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public class ControlConnectionHandler extends ConnectionHandler {
	
	// This is the maximum length, in bytes, of a command that this server will accept
	private static final int MAX_MSG_SIZE = 1024*1024;
	
	// Telnet end-of-line for delimiting responses
	private static final String TELNET_EOL = "\r\n";
	
	// Logger to log events
	private static final Logger logger = Logger.getGlobal();
	
	// Reference to socket we are handling, for convenience.  This should not be changed during the lifetime
	// of this thread
	private Socket socket;
	
	// Connection input and output streams.  Not sure if we need the Data* wrapper classes since I wrote the
	// PDU logic myself but oh well
	private DataInputStream socketIn;
	private DataOutputStream socketOut;
	
	// For breaking out of the handle loop
	private boolean alive = true;

	@Override
	public void handle(Socket socket) {
		this.socket = socket;
		try {
			this.socketIn = new DataInputStream(socket.getInputStream());
			this.socketOut = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			
			// Terminate this connection thread
			return;
		}
		
		// Testing
		while(alive) {
			writeFTPPDU("RIBBIT.");
			String message = getFTPPDU();
			if (message == null)
				alive = false;
		}
		
		
	}
	
	/**
	 * Read a TELNET style FTP PDU from the socket.  Only commands <= MAX_MSG_SIZE will be considered valid
	 * @return The message that was read
	 */
	private String getFTPPDU() {
		// Buffer for socket data
		byte[] buffer = new byte[MAX_MSG_SIZE];
		
		// For building the message string as it comes off the socket
		StringBuffer out = new StringBuffer();
		int totalRead = 0, numRead = 0;
		try {
			while ((numRead = socketIn.read(buffer)) > 0) {
				totalRead += numRead;
				
				// Bail if the message is too big
				if (totalRead > MAX_MSG_SIZE)
					break;

				// Encode the message as standard ASCII
				String segment = new String(buffer, Charset.forName("US-ASCII"));
				
				// Check for the Telnet EOL
				int eolIdx = segment.indexOf(TELNET_EOL);
				
				// If we have reached the EOL we are done reading
				if (eolIdx >= 0) {
					out.append(segment.substring(0, eolIdx));
					String result = out.toString();
					EventLogger.logNetworkDataReceived(logger, socket, result);
					return result;
				}
			}
			
			// The message was too big, empty the stream buffer
			while (socketIn.read(buffer) > 0) {}

		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			return null;
		}
		
		return null;
	}
	
	/**
	 * Writes the message to the socket, appending the Telnet EOL delimiter
	 * @param message The message to send
	 */
	private void writeFTPPDU(String message) {
		try {
			socketOut.write((message + TELNET_EOL).getBytes());
			EventLogger.logNetworkDataSent(logger, socket, message);
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			alive = false;
		}
	}

}
