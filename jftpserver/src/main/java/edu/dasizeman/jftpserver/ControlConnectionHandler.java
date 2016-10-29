package edu.dasizeman.jftpserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
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
	
	// Welcome message
	private static final String WELCOME_MSG = "Welcome to JFTP, homie.";
	
	private static final String[] SUPPORTED_CMDS = new String[]{"USER", "PASS", "CWD", "CDUP", "QUIT", "PASV", "EPSV",
			"PORT", "EPRT", "RETR", "PWD", "LIST", "HELP"};
	
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
	
	// Authentication for this connection
	private String username = null;
	private String password = null;
	
	// The type of data connection
	private enum DataConnectionType {
		ACTIVE,
		PASSIVE
	}
	
	private DataConnectionType dataConnectionType = null;
	
	/**
	 * Connection initialization stuff
	 * @return If the initialization was successful
	 */
	private boolean init() {
		// Load the credential file
		JSONCredentialManager.getInstance().loadCredentialFile(JSONCredentialManager.CRED_FILE_PATH);
		try {
			this.socketIn = new DataInputStream(socket.getInputStream());
			this.socketOut = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			
			// Terminate this connection thread
			return false;
		}
		
		return true;
	}
	
	private void close() {
		try {
			socket.getInputStream().close();
			socket.getOutputStream().close();
			socket.close();
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
		}
	}

	@Override
	public void handle(Socket socket) {
		this.socket = socket;
		if (!init())
			return;

		// Send welcome message
		sendFTPResponse(FTPResponse.NEW_USER_SERVICE_RDY, WELCOME_MSG);
		
		// Handle requests until the user leaves or something goes wrong
		while(alive) {
			String message = getFTPPDU();
			
			// Die if a badly delimited or too large message is received
			if (message == null)
				alive = false;
			
			handleFTPCommand(message);
		}
		
		// Clean up the connection and die
		close();
	}
	
	private void sendHelp() {
		StringBuffer helpMsg = new StringBuffer();
		helpMsg.append("The following commands are supported: ");
		for (String cmd: SUPPORTED_CMDS) {
			helpMsg.append(String.format("%s,", cmd));
		}
		
		sendFTPResponse(FTPResponse.STATUS_REPLY, helpMsg.toString());
	}
	
	/**
	 * A lil' wrapper for FTP commands we receive
	 * @author Dave Sizer <dave@sizetron.net>
	 *
	 */
	private class FTPCommandData {
		public FTPCommand command;
		public String[] args;
		
		public FTPCommandData(String commandStr) {
			String[] tokens = commandStr.split(" ");
			if (tokens.length < 1) {
				command = null;
				return;
			}
			
			command = FTPCommand.getByName(tokens[0]);
			args = Arrays.copyOfRange(tokens, 1, tokens.length);
		}
	}
	
	/**
	 * Parses an incoming FTP command and sends the appropriate response
	 * @param command The command to process
	 */
	private void handleFTPCommand(String command) {
		
		// Do some preliminary parsing of the input
		FTPCommandData commandData = new FTPCommandData(command);
		
		// Is this command valid?
		if (commandData.command == null) {
			sendFTPResponse(FTPResponse.UNRECOGNIZED_CMD, null);
			return;
		}
		
		// Make sure we are authenticated.  If the authentication helper
		// has returned false, authentication is not complete and it has
		// sent some response related to authenticating, so we bail here
		if (!checkAuthentication(commandData))
			return;
		
		switch (commandData.command) {
		case HELP:
			sendHelp();
			break;
		case QUIT:
			sendFTPResponse(FTPResponse.CLOSING_CTRL_CONN, "Later, hater.");
			alive = false;
			break;
		default:
			sendFTPResponse(FTPResponse.UNIMPLEMENTED_CMD, null);
			break;
		}

		
	}
	
	/**
	 * Make sure the user is properly authenticated, send any needed responses if not 
	 * @param commandData The command we are checking authentication for
	 * @return Whether we were authenticated or not
	 */
	private boolean checkAuthentication(FTPCommandData commandData) {
		boolean alreadyAuthenticated = (username != null && password != null);
		
		if ((commandData.command == FTPCommand.USER || commandData.command == FTPCommand.PASS)
				&& commandData.args.length < 1) {
			sendFTPResponse(FTPResponse.BAD_CMD_PARAMETERS, null);
			return false;
		}
		
		switch (commandData.command) {
		case USER:
			username = commandData.args[0];
			password = null;
			sendFTPResponse(FTPResponse.NEED_PASS, null);
			return false;
		case PASS:
			
			// They sent pass before username
			if (username == null) {
				sendFTPResponse(FTPResponse.BAD_CMD_SEQUENCE, "Login with USER first.");
				return false;
			}
			
			password = commandData.args[0];
			
			// Bad creds, close connection
			if (!JSONCredentialManager.getInstance().checkCredential(username, password)) {
				sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Bad credentials, bye ;)");
				alive = false;
				return false;
			}
			
			// If we're here, creds are good
			sendFTPResponse(FTPResponse.LOGIN_OK, String.format("Sup, %s. Welcome back.", username));
			return false;
			
		default:
			if (!alreadyAuthenticated) {
				// They have entered user but not pass
				if (username != null && password == null) {
					sendFTPResponse(FTPResponse.BAD_CMD_SEQUENCE, null);
					username = null;
				} else
					sendFTPResponse(FTPResponse.NOT_LOGGED_IN, null);
				return false;
			}
		}	
		
		return true;
	}
		
	
	/**
	 * Send a response to the client
	 * @param response The response to send
	 * @param message The response message.  If null, a default message is sent
	 */
	private void sendFTPResponse(FTPResponse response, String message) {
		String sendMessage;
		
		// Send the default message if one wasn't specified
		if (message == null)
			sendMessage = response.message;
		else
			sendMessage = message;
		
		writeFTPPDU(String.format("%s %s", Integer.toString(response.code), sendMessage));
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
