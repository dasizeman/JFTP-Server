package edu.dasizeman.jftpserver;

import java.net.Socket;

/**
 * Abstract handler for a TCP connection.  Needs to be redesigned since the 
 * Data connection handler kind of hacks it
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public abstract class ConnectionHandler implements Runnable {
	protected Socket socket;
	
	/**
	 * Start the handler thread with the given connection socket
	 * @param socket The socket with an established TCP connection to handle
	 */
	public void start(Socket socket) {
		this.socket = socket;
		new Thread(this).start();
	}
	
	@Override
	public void run() {
		handle(socket);
	}

	/**
	 * Logic to handle the connection
	 * @param socket The socket with an established TCP connection to handle
	 */
	public abstract void handle(Socket socket);
}
