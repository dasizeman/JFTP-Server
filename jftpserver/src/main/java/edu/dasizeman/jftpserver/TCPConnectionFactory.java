package edu.dasizeman.jftpserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Utility class to create inbound and outbound TCP sockets with connection
 * targets in the form <hostname>:<port>
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public class TCPConnectionFactory {
	
	/**
	 * Container to return parsed host data internally in this class
	 * @author dasizeman
	 *
	 */
	private static class ParsedHost {
		public String host;
		public int port;
		
		public ParsedHost(String host, int port) {
			this.host = host;
			this.port = port;
		}
	}
	
	/**
	 * Parse a host specification of the form <hostname>:<port>
	 * @param hostString The target host/port string
 	 * @return A container class with the hostname string and port number
	 * @throws NumberFormatException When the input format was not correct
	 */
	private static ParsedHost parseHostString(String hostString) throws NumberFormatException {
		if (!hostString.contains(":")) {
			throw new NumberFormatException("Bad host string");
		}
		
		String[] tokens = hostString.split(":");
		if (tokens.length <= 2) {
			throw new NumberFormatException("Bad host string");
		}
		
		ParsedHost parsed = new ParsedHost(tokens[0], Integer.parseInt(tokens[1]));
		
		return parsed;
	}
	
	/**
	 * Get a socket for an outbound connection
	 * @param host The host to connect to, in format <hostname>:<port>
	 * @return The connected socket
	 * @throws NumberFormatException If the host string could not be parsed
	 * @throws UnknownHostException If the hostname could not be resolved
	 * @throws IOException If something goes wrong with the connection
	 */
	public static Socket getConnectSocket(String host) throws NumberFormatException, UnknownHostException, IOException {
		ParsedHost parsedHost = parseHostString(host);
		
		Socket socket = new Socket(parsedHost.host, parsedHost.port);
		return socket;
		
	}
	
	/**
	 * Get a socket for an inbound connection.  Blocks waiting for this connection
	 * @param host The host to bind to, in format <hostname>:<port>
	 * @return The connected socket
	 * @throws NumberFormatException If the host string could not be parsed
	 * @throws UnknownHostException If the hostname could not be resolved
	 * @throws IOException If something goes wrong with the connection
	 */
	public static Socket getListenSocket(String host) throws IOException {
		ParsedHost parsedHost = parseHostString(host);
		
		InetAddress ipAddr = InetAddress.getByName(parsedHost.host); 
		ServerSocket serverSocket = new ServerSocket(parsedHost.port, 0, ipAddr);
		
		Socket socket  = serverSocket.accept();
		serverSocket.close();
		return socket;
		
	}
	
	/**
	 * Get a socket for an inbound connection.  Blocks waiting for this connection
	 * @param port The port to bind to on the default interface
	 * @return The connected socket
	 * @throws IOException If something goes wrong with binding or connection
	 */
	public static Socket getListenSocket(int port) throws IOException {
		ServerSocket serverSocket = new ServerSocket(port);
		Socket socket = serverSocket.accept();
		serverSocket.close();
		return socket;
	}
	
	/***
	 * Start an outgoing connection and start a thread with the specified handler
	 * @param host The host to connect to in format <host>:<port>
	 * @param handler The handler to handle the connection
	 * @throws NumberFormatException
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public static void connectAndHandle(String host, ConnectionHandler handler) throws NumberFormatException, UnknownHostException, IOException {
		Socket socket = getConnectSocket(host);
		handler.start(socket);
	}
	
	/***
	 * Start an incoming connection and start a thread with the specified handler
	 * @param host The interface to bind to in the format <host>:<port>
	 * @param handler The handler to handle the connection
	 * @throws NumberFormatException
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public static void listenAndHandle(String host, ConnectionHandler handler) throws IOException {
		Socket socket = getListenSocket(host); 
		handler.start(socket);
	}
	
	/***
	 * Start an incoming connection and start a thread with the specified handler
	 * @param serverSocket the server socket to listen to the connections on
	 * @param handler The handler to handle the connection
	 * @throws NumberFormatException
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public static void listenAndHandle(ServerSocket serverSocket, ConnectionHandler handler) throws IOException {
		handler.start(serverSocket.accept());
	}
	
	

}
