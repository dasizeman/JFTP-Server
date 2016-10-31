package edu.dasizeman.jftpserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
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
	private BufferedReader socketIn;
	private BufferedWriter socketOut;
	
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
	
	// Current data listener
	private ServerSocket dataListener;
	
	private Socket dataConnection;
	
	
	// Port for active connections
	private int activePort;
	private String activeHostString;
	
	
	// Filesystem manager 
	private FilesystemManager filesystem;
	
	/**
	 * Connection initialization stuff
	 * @return If the initialization was successful
	 */
	private boolean init() {
		// Load the credential file
		JSONCredentialManager.getInstance().loadCredentialFile(JSONCredentialManager.CRED_FILE_PATH);
		try {
			this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.socketOut = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			
			// Terminate this connection thread
			return false;
		}
		
		filesystem = new FilesystemManager("");
		
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
			if (message == null) {
				EventLogger.logConnectionException(logger, socket, new Exception("Bad FTP PDU received."));
				sendFTPResponse(FTPResponse.UNRECOGNIZED_CMD, null);
				continue;
			}
			
			handleFTPCommand(message);
		}
		
		// Clean up the connection and die
		close();
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
			sendFTPResponse(FTPResponse.UNIMPLEMENTED_CMD, null);
			return;
		}
		
		// Make sure we are authenticated.  If the authentication helper
		// has returned false, authentication is not complete and it has
		// sent some response related to authenticating, so we bail here
		if (!checkAuthentication(commandData))
			return;
		
		switch (commandData.command) {
		case PORT:
			doPORT(commandData);
			break;
		case RETR:
			sendFile(commandData);
			break;
		case TYPE:
				sendFTPResponse(FTPResponse.COMMAND_OK, "What is type, anyway?");
			break;
		case NOOP:
			sendFTPResponse(FTPResponse.COMMAND_OK, "NOOP ok.");
			break;
		case PASV:
		case EPSV:
			doPASV(commandData);
			break;
		case CDUP:
			// Add the .. arg and call the CWD handler
			commandData.args = new String[]{".."};
			doCWD(commandData);
			break;
		case CWD:
			doCWD(commandData);
			break;
		case LIST:
			Socket dataSocket = getDataSocket();
			if (dataSocket != null) {
				// Create a String stream for the directory listing
				InputStream stringStream = new ByteArrayInputStream(filesystem.ls().getBytes());
				
				// Send the listing over the socket
				sendFTPResponse(FTPResponse.ABOUT_TO_OPEN_DATA, "Here comes the directory listing.");
				new DataConnectionHandler().startSend(dataSocket, stringStream, this);
				dataConnectionType = null;
				break;
			} 
			sendFTPResponse(FTPResponse.CANT_OPEN_DATA_CONN, "Use PORT or PASV first.");
			dataConnectionType = null;
			break;
		case PWD:
			sendFTPResponse(FTPResponse.PATH_CREATED, String.format("%s", filesystem.pwd()));
			break;
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
	
	private void sendFile(FTPCommandData commandData) {
		if (commandData.args.length < 1) {
			sendFTPResponse(FTPResponse.BAD_CMD_PARAMETERS, "Please specify a file");
			return;
		}
		String fileName = String.join(" ", commandData.args);
		
		FileInputStream fileStream = filesystem.getFileStream(fileName);
		if (fileStream == null) {
			sendFTPResponse(FTPResponse.FILE_UNAVAIL, "File not available");
			return;
		}
		Socket dataSocket = getDataSocket();
		if (dataSocket == null) {
			sendFTPResponse(FTPResponse.CANT_OPEN_DATA_CONN, "Use PORT or PASV first.");
			dataConnectionType = null;
			return;
		}
		
		sendFTPResponse(FTPResponse.ABOUT_TO_OPEN_DATA, String.format("%s incoming.", commandData.args[0]));
		new DataConnectionHandler().startSend(dataSocket, fileStream, this);
		dataConnectionType = null;
	}
	
	
	private void doPASV(FTPCommandData commandData) {
		boolean portBound = false;
		while (!portBound) {
			try {
				InetAddress bindAddress = getLocalInterface();
				if (bindAddress == null)
					break;
				dataListener = new ServerSocket(0,0, bindAddress);
			} catch (IOException e) {
				EventLogger.logListenException(logger, dataListener, e);
			}
			portBound = true;
		}
		
		if (portBound) {
			// Make sure we are bound to an IPV4 interface
			try {
				Inet4Address ipv4 = (Inet4Address)(dataListener.getInetAddress());
			} catch (ClassCastException e) {
				sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Only IPv4 is supported. Closing connection.");
				alive = false;
				return;
			}
			// TODO hopefully it starts listening in time
			new DataConnectionListener(dataListener, this).listen();
			if (commandData.command == FTPCommand.PASV) {
				sendFTPResponse(FTPResponse.ENTERING_PASV, getPASVString((Inet4Address)dataListener.getInetAddress(), dataListener.getLocalPort()));
			} else {
				sendFTPResponse(FTPResponse.ENTERING_EPSV, getEPSVString((Inet4Address)dataListener.getInetAddress(), dataListener.getLocalPort()));
			}
			dataConnectionType = DataConnectionType.PASSIVE;
		} else {
			sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Failed to bind data port.  Closing connection.");
			alive = false;
		}
	}
	
	private void doPORT(FTPCommandData commandData) {
		String portString = null;
		if (commandData.args.length >= 1) {
			portString = commandData.args[0];
			String[] portSegments = portString.split(",");
			
			if (portSegments.length == 6) {
				activeHostString = String.join(".", Arrays.copyOfRange(portSegments, 0, 4));
				int portUpper = Integer.parseInt(portSegments[4]);
				int portLower = Integer.parseInt(portSegments[5]);
				activePort = 256 * portUpper + portLower;
				dataConnectionType = DataConnectionType.ACTIVE;
				sendFTPResponse(FTPResponse.COMMAND_OK, "Port command accepted.");
				return;
			}
		}
		sendFTPResponse(FTPResponse.BAD_CMD_PARAMETERS, "Invalid port command");
	}
	
	void dataConnectionCallback(Socket connection) {
		if (connection == null) {
			dataConnectionType = null;
			sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Data connection failed.  Closing connection.");
			alive = false;
			return;
		}
		dataConnection = connection;
	}
	
	private Socket getDataSocket() {
		if (dataConnectionType == null)
			return null;
		switch (dataConnectionType) {
		case PASSIVE:
			return dataConnection;
		case ACTIVE:
			// Try to connect to the client's active port
			try {
				return new Socket(activeHostString, activePort);
			} catch (IOException e) {
				EventLogger.logConnectionException(logger, socket, e);
				return null;
			}
		default:
			return null;
		}
	}
	
	
	private String getPASVString(Inet4Address addr, int port) {
		String ipStr = addr.getHostAddress();
		String[] octets = ipStr.split("\\.");
		int portUpper = port / 256;
		int portLower = port % 256;
		return String.format("Entering PASV mode (%s,%d,%d)", String.join(",", octets), portUpper, portLower);
	}
	
	public String getEPSVString(Inet4Address addr, int port) {
		return String.format("Entering Extended Passive mode (|||%d|)", port);
	}
	
	private Inet4Address getLocalInterface() {
		Enumeration<NetworkInterface> ifaceenum;
		try {
			ifaceenum = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			return null;
		}
		while (ifaceenum.hasMoreElements())
		{
			NetworkInterface ni = ifaceenum.nextElement();
			Enumeration<InetAddress> addrenum = ni.getInetAddresses();
			while (addrenum.hasMoreElements())
			{
				InetAddress thisaddr = addrenum.nextElement();
				
				if (!thisaddr.isLoopbackAddress()) {
					try {
						Inet4Address ipv4 = (Inet4Address)thisaddr;
						return ipv4;
					} catch (ClassCastException e) {
						continue;
					}
				}
			}
		}
		
		return null;
	}
	
	private void doCWD(FTPCommandData data) {
		if (data.args.length < 1) {
			sendFTPResponse(FTPResponse.BAD_CMD_PARAMETERS, null);
			return;
		}
		try {
			// Join arguments
			String path = String.join(" ", data.args);
			filesystem.cd(path);
			sendFTPResponse(FTPResponse.FILE_ACTION_COMPLETED, String.format("CWD is now: %s", filesystem.pwd()));
		} catch (FileNotFoundException e) {
			sendFTPResponse(FTPResponse.FILE_UNAVAIL, e.getMessage());
		}
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
	void sendFTPResponse(FTPResponse response, String message) {
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
		String result;
		try {
			result = socketIn.readLine();
			EventLogger.logNetworkDataReceived(logger, socket, result);
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			result = null;
		}
		return result;
	}
	
	/**
	 * Writes the message to the socket, appending the Telnet EOL delimiter
	 * @param message The message to send
	 */
	private synchronized void writeFTPPDU(String message) {
		try {
			socketOut.write((message + TELNET_EOL));
			socketOut.flush();
			EventLogger.logNetworkDataSent(logger, socket, message);
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			alive = false;
		}
	}

}
