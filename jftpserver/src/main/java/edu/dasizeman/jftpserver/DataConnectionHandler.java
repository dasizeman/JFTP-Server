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
	private ControlConnectionHandler controlHandler;
	private InputStream sendStream;
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
	
	@Override
	public void start(Socket socket){}
	
	public void startSend(Socket socket, InputStream dataStream, ControlConnectionHandler controlHandler) {
		connectionMode = mode.SND;
		this.controlHandler = controlHandler;
		this.socket = socket;
		this.sendStream = dataStream;
		new Thread(this).start();
	}
	
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
