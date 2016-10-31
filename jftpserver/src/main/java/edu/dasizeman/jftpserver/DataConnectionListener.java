package edu.dasizeman.jftpserver;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * A thread to listen for data connections
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public class DataConnectionListener implements Runnable {
	private ServerSocket listenSocket;
	private ControlConnectionHandler controlHandler;
	
	public DataConnectionListener(ServerSocket listenSocket, ControlConnectionHandler controlHandler) {
		this.listenSocket = listenSocket;
		this.controlHandler = controlHandler;
	}

	@Override
	public void run() {
		try {
			controlHandler.dataConnectionCallback(listenSocket.accept());
		} catch (IOException e) {
			controlHandler.dataConnectionCallback(null);
		}
	}
	
	public void listen() {
		new Thread(this).start();
	}

}
