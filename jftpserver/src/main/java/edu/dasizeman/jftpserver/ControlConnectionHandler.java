package edu.dasizeman.jftpserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for an FTP control connection
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public class ControlConnectionHandler extends ConnectionHandler {
	
	// Telnet end-of-line for delimiting responses
	private static final String TELNET_EOL = "\r\n";
	
	// Welcome message
	private static final String WELCOME_MSG = "Welcome to JFTP, homie.";
	
	// The configuration option for the accounts file
	private static final String ACCT_FILE_CONFIG_KEY = "usernamefile";
	private static final String ALLOW_ACTIVE_CONFIG_KEY = "port_mode";
	private static final String ALLOW_PASSIVE_CONFIG_KEY = "pasv_mode";
	private static final String FILE_ROOT_CONFIG_KEY = "file_root";
	
	// This is just for sending the help message
	private static final String[] SUPPORTED_CMDS = new String[]{"USER", "PASS", "CWD", "CDUP", "QUIT", "PASV", "EPSV",
			"PORT", "EPRT", "RETR", "PWD", "LIST", "HELP", "TYPE", "NOOP"};
	
	// Logger to log events
	private static final Logger logger = Logger.getGlobal();
	
	// Reference to socket we are handling, for convenience.  This should not be changed during the lifetime
	// of this thread
	private Socket socket;
	
	// Connection input and output streams.  I use readline so its possible you could crash the server with an absurdly long 
	// command pdu
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
	// We use the is to make sure that the user has issued a port or pasv command
	// before doing operations with the data connection
	private DataConnectionType dataConnectionType = null;
	
	// Current data listener, used for the time when the user has called pasv but not connected
	//yet
	private ServerSocket dataListener;
	
	// The actual socket used for data transfers
	private Socket dataConnection;
	
	
	// Port and host for making an active data connection
	private int activePort;
	private String activeHostString;
	
	
	// Filesystem manager 
	private FilesystemManager filesystem;
	
	// For restricting data transfer modes
	boolean allowPassive = false, allowActive = false;
	
	/**
	 * Connection initialization stuff
	 * @return If the initialization was successful
	 */
	private boolean init() {
		try {
			// Get the config file
			ConfigurationFile configFile = ConfigurationFile.getInstance();
			
			// Load the credential file
			CredentialManager.getInstance().loadCredentialFile(configFile.getConfigValue(ACCT_FILE_CONFIG_KEY));
			
			// Check if data transfer modes have been restricted in the config
			if (configFile.getConfigValue(ALLOW_ACTIVE_CONFIG_KEY).toLowerCase().equals("yes")) 
				allowActive = true;
			
			if(configFile.getConfigValue(ALLOW_PASSIVE_CONFIG_KEY).toLowerCase().equals("yes"))
				allowPassive = true;
			
			if (!allowActive && !allowPassive) {
				EventLogger.logGeneralException(logger, "Server init", new Exception("At least one of port_mode and pasv_mode must be enabled in the config"));
				System.exit(1);
			}

			// This is where we would set a different serving root directory if we wanted, for now it just serves from the same
			// directory as the JAR if we don't specify anything
			filesystem = new FilesystemManager(configFile.getConfigValue(FILE_ROOT_CONFIG_KEY));
			
			this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.socketOut = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
			
			// Terminate this connection thread
			return false;
		}
		
		
		return true;
	}
	
	// This closes the control connection.  Only ever called directly from the end of the main handle loop.
	// To kill the connection we can set alive = false from somewhere in this class
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
	 * A lil' wrapper for FTP commands we receive.  Parses
	 * into the actual command enum and a string array of 
	 * space delimited arguments.  command == null if parsing
	 * fails
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
		case EPRT:
			doEPRT(commandData);
			break;
		case PORT:
			doPORT(commandData);
			break;
		case RETR:
			sendFile(commandData);
			break;
		case TYPE:
				// I am convinced that type doesn't matter on the server side, since
				// I just dump the whole file to the socket.  It does seem to matter
				// to a lot of clients, so I'm "supporting" it
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
	
	/**
	 * Handles looking for the file in the current directory, and sending it over the data connection.
	 * TODO: Do ftp servers support retrieving files in directories other than the current, because 
	 * mine doesn't
	 * @param commandData The command info including the file name
	 */
	private void sendFile(FTPCommandData commandData) {
		if (commandData.args.length < 1) {
			sendFTPResponse(FTPResponse.BAD_CMD_PARAMETERS, "Please specify a file");
			return;
		}
		// Join into a single file name argument
		String fileName = String.join(" ", commandData.args);
		
		// Try to get a stream from the file manager
		FileInputStream fileStream = filesystem.getFileStream(fileName);
		if (fileStream == null) {
			sendFTPResponse(FTPResponse.FILE_UNAVAIL, "File not available");
			return;
		}
		// Try to get a valid data connection
		// TODO We should probably wrap this so we don't have to repeat it for every
		// data command
		Socket dataSocket = getDataSocket();
		if (dataSocket == null) {
			sendFTPResponse(FTPResponse.CANT_OPEN_DATA_CONN, "Use PORT or PASV first.");
			dataConnectionType = null;
			return;
		}
		
		// Let the client know we are about to send the file over the data connection, and kick off a thread
		// to do so
		sendFTPResponse(FTPResponse.ABOUT_TO_OPEN_DATA, String.format("%s incoming.", commandData.args[0]));
		new DataConnectionHandler().startSend(dataSocket, fileStream, this);
		
		// Reset the type so they have to enter the connection method again (port or pasv)
		// TODO this is probably another thing that should be wrapped so I don't forget to do it
		// somewhere, as new commands are added
		dataConnectionType = null;
	}
	
	
	/**
	 * Handles the pasv and epsv commands, immediately starts listening on the data port and
	 * sends the appropriate response when this is done
	 * @param commandData To check which one of the commands it is.  Since we are not supporting
	 * IPv6 right now, the only thing that differs is the string we send back
	 */
	private void doPASV(FTPCommandData commandData) {
		if (!allowPassive) {
			sendFTPResponse(FTPResponse.UNIMPLEMENTED_CMD, "Passive transfers are disabled on this server.");
			return;
		}
		// Try to bind the listen port.  There is definitely a cleaner way to do this error handling
		boolean portBound = false;
		try {
			InetAddress bindAddress = getLocalInterface();
			if (bindAddress == null)
				portBound = false;
			else {
				dataListener = new ServerSocket(0,0, bindAddress);
				portBound = true;
			}
		} catch (IOException e) {
			EventLogger.logListenException(logger, dataListener, e);
			portBound = false;
		}
		
		if (portBound) {
			// Make sure we are bound to an IPV4 interface.  I am using janky casting here to do this,
			// there is probably a better way
			try {
				@SuppressWarnings("unused")
				Inet4Address ipv4 = (Inet4Address)(dataListener.getInetAddress());
			} catch (ClassCastException e) {
				sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Only IPv4 is supported. Closing connection.");
				alive = false;
				return;
			}
			// TODO We actually probably need some synchronization here, we are currently just hoping that the  client waits
			// until we send our response to try connecting (which it is supposed to do), and that this delay is long enough for
			// the other thread to start listening.  NOTE: I have seen some cases where it looks like the client tries to issue its data command
			// before we are ready, so this synchronization might actually be an issue sometimes
			dataConnectionType = DataConnectionType.PASSIVE;
			new DataConnectionListener(dataListener, this).listen();
			if (commandData.command == FTPCommand.PASV) {
				sendFTPResponse(FTPResponse.ENTERING_PASV, getPASVString((Inet4Address)dataListener.getInetAddress(), dataListener.getLocalPort()));
			} else {
				sendFTPResponse(FTPResponse.ENTERING_EPSV, getEPSVString((Inet4Address)dataListener.getInetAddress(), dataListener.getLocalPort()));
			}
		} else {
			sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Failed to bind data port.  Closing connection.");
			alive = false;
		}
	}
	
	/**
	 * Set our active connection endpoint based on the port command we received
	 * TODO try and make this logic cleaner
	 * @param commandData
	 */
	private void doPORT(FTPCommandData commandData) {
		if (!allowActive) {
			sendFTPResponse(FTPResponse.UNIMPLEMENTED_CMD, "Active transfers are disabled on this server.");
			return;
		}
		
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
	
	/**
	 * Set our active connection endpoint based on an eprt command.
	 * TODO this is untested because I can't get any client to send me
	 * EPRT
	 * @param commandData
	 */
	private void doEPRT(FTPCommandData commandData) {
		if (!allowActive) {
			sendFTPResponse(FTPResponse.UNIMPLEMENTED_CMD, "Active transfers are disabled on this server.");
			return;
		}
		
		if(commandData.args.length >= 1) {
			Pattern eprtPattern = Pattern.compile("|(\\d)|(\\d+\\.\\d+\\.\\d+\\.\\d+)|(\\d+)|");
			Matcher eprtMatcher = eprtPattern.matcher(commandData.args[0]);
			if(eprtMatcher.find() 
					&& eprtMatcher.group(1) != null
					&& eprtMatcher.group(1).equals("1")) {
				activeHostString = eprtMatcher.group(2);
				activePort = Integer.parseInt(eprtMatcher.group(3));
				dataConnectionType = DataConnectionType.ACTIVE;
				sendFTPResponse(FTPResponse.COMMAND_OK, "Extended Port command accepted.");
				return;
			}
		}
		sendFTPResponse(FTPResponse.BAD_CMD_PARAMETERS, "Invalid extended port command");
	}
	
	/**
	 * This is called back from the thread that listens for a data connection 
	 * for passive mode
	 * @param connection The connected socket for the data connection
	 */
	void dataConnectionCallback(Socket connection) {
		if (connection == null) {
			dataConnectionType = null;
			sendFTPResponse(FTPResponse.NOT_AVAIL_CLOSING, "Data connection failed.  Closing connection.");
			alive = false;
			return;
		}
		dataConnection = connection;
	}
	
	/**
	 * Tries to return a valid data connection socket based
	 * on the current data connection mode
	 * @return The data socket to use or null if there
	 * was a problem
	 */
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
	
	
	/**
	 * Produces the string that we send back on a pasv request 
	 * @param addr The IPv4 address object we are going to bind to
	 * @param port The port we are binding to
	 * @return The string to send in the passive response
	 */
	private String getPASVString(Inet4Address addr, int port) {
		String ipStr = addr.getHostAddress();
		String[] octets = ipStr.split("\\.");
		int portUpper = port / 256;
		int portLower = port % 256;
		return String.format("Entering PASV mode (%s,%d,%d)", String.join(",", octets), portUpper, portLower);
	}
	
	/**
	 * Produces the string that we send back on an epsv request
	 * @param addr The IPv4 address object we are going to bind to
	 * @param port The port we are binding to
	 * @return The string to send in the epsv response
	 */
	public String getEPSVString(Inet4Address addr, int port) {
		return String.format("Entering Extended Passive mode (|||%d|)", port);
	}
	
	/**
	 * Get the first IPV4 interface we can find on this host
	 * @return This address object
	 */
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
	
	/**
	 * Try to change the working directory
	 * @param data The cwd command data
	 */
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
	
	/**
	 * Send the help response
	 */
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
			if (!CredentialManager.getInstance().checkCredential(username, password)) {
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
