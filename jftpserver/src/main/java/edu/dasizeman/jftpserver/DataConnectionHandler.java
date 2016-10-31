package edu.dasizeman.jftpserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * A thread for handling an FTP data connection.  Usually only alive for one transfer
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public class DataConnectionHandler extends ConnectionHandler {
	private static final Logger logger = Logger.getGlobal();
	
	// We have a reference to the control handler for sending responses when
	// the data transfer is done or fails
	private ControlConnectionHandler controlHandler;
	
	// The stream we are going to send over the socket
	private InputStream sendStream;
	
	// Note used yet, but used so this thread can handle both sending and receving 
	// and its designed badly
	private enum mode {
		SND,
		RCV
	}
	private mode connectionMode;

	@Override
	public void handle(Socket socket) {
		switch (connectionMode) {
		case SND:
			trySendData(sendStream, socket);
			break;
		case RCV:
			break;
		}
		
	}
	
	/**
	 * Like I said, bad design.  Just don't call this lol
	 */
	@Override
	public void start(Socket socket){}
	
	/**
	 * Start an outgoing data transfer
	 * @param socket The socket to send on
	 * @param dataStream The stream to send
	 * @param controlHandler The control connection handler to 
	 * call back to
	 */
	public void startSend(Socket socket, InputStream dataStream, ControlConnectionHandler controlHandler) {
		connectionMode = mode.SND;
		this.controlHandler = controlHandler;
		this.socket = socket;
		this.sendStream = dataStream;
		new Thread(this).start();
	}
	
	/**
	 * Wrapper for attempting to send a stream of data over a socket, that handles
	 * necessary control responses
	 * @param from The stream to send
	 * @param to The socket to send over
	 */
	private void trySendData(InputStream from, Socket to) {
		try {
			streamCopy(from, to.getOutputStream());
			controlHandler.sendFTPResponse(FTPResponse.CLOSING_DATA_CONN_SUCC, "Transfer complete, data connection closing");
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, to, e);
			controlHandler.sendFTPResponse(FTPResponse.CLOSING_DATA_CONN_ABRT, "Transfer failed, data connection closing");
		}
		// Clean up the connection
		try {
			from.close();
			to.getInputStream().close();
			to.getOutputStream().close();
			to.close();
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, to, e);
		}
	}
	
	/**
	 * Fully copy one stream to another
	 * @param from The source stream
	 * @param to The destination stream
	 */
	private void streamCopy(InputStream from, OutputStream to) {
		try {
			int count = 0;
			byte[] buffer = new byte[16*1024];
			while ((count = from.read(buffer)) > 0) {
				to.write(buffer, 0, count);
			}
		} catch (IOException e) {
			EventLogger.logConnectionException(logger, socket, e);
		}
	}

}
