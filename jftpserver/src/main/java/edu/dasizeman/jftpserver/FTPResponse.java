package edu.dasizeman.jftpserver;

import java.util.HashMap;
import java.util.Map;

/**
 * Responses specified by FTP RFC 959 and extensions in RFC 2428
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 *
 */
public enum FTPResponse {
	COMMAND_OK(200, "Command okay."),
	UNRECOGNIZED_CMD(500, "Syntax error, command unrecognized."),
	BAD_CMD_PARAMETERS(501, "Syntax error parameters or arguments."),
	POINTLESS_CMD(202, "Command not implemented, superfluous at this site."),
	UNIMPLEMENTED_CMD(502, "Command not implemented."),
	BAD_CMD_SEQUENCE(503, "Bad sequence of commands."),
	UNIMPLEMENTED_PARAM(504, "Command not implemented for that parameter."),
	RESTART_MARKER_REPLY(110, "UNIMPLEMENTED: MARK %4d = %4d"),
	STATUS_REPLY(211, "System status, or system help reply."),
	DIR_STATUS(212, "Directory status."),
	FILE_STATUS(213, "File status."),
	HELP_MESSAGE(214, "Help message."),
	NAME(215, "NAME system type."),
	SERVICE_WAIT(120, "Service ready in %3d minutes."),
	NEW_USER_SERVICE_RDY(220, "Service ready for new user."),
	CLOSING_CTRL_CONN(221, "Service closing control connection."),
	NOT_AVAIL_CLOSING(421, "Service not available, closing control connection."),
	TRANSFER_START_ALRDY_OPEN(125, "Data connection already open; transfer starting."),
	DATA_CONN_OPEN_NO_TRSFR(225, "Data connection open; no transfer in progress."),
	CANT_OPEN_DATA_CONN(425, "Can't open data connection."),
	CLOSING_DATA_CONN_SUCC(226, "Closing data connection; transfer complete."),
	CLOSING_DATA_CONN_ABRT(426, "Connection closed; transfer aborted."),
	ENTERING_PASV(227, "Entering Passive Mode (%d,%d,%d,%d,%d)."),
	LOGIN_OK(230, "User logged in, proceed."),
	NOT_LOGGED_IN(530, "Not logged in."),
	NEED_PASS(331, "User name okay, need password."),
	ACCT_NEEDED(332, "Need account for login."),
	ACCT_NEEDED_TO_STORE(532, "Need account for storing files."),
	ABOUT_TO_OPEN_DATA(150, "File status okay; about to open data connection."),
	FILE_ACTION_COMPLETED(250, "Requested file action okay, completed."),
	PATH_CREATED(257, "\"PATHNAME\" created."),
	FILE_ACTION_PENDING(350, "Requested file action pending further information."),
	FILE_BUSY(450, "Requested file action not taken."),
	SERVER_ERROR(451, "Requested action aborted: local error in processing."),
	INSUFF_STORAGE(452, "Requested action not taken. Insufficient storage space in system."),
	FILE_UNAVAIL(550, "Requested action not taken. File unavailable"),
	ABRT_OUT_OF_SPACE(552, "Requested file action aborted. Exceeded storage allocation"),
	ENTERING_EPSV(229, "Entering extended passive mode."),
	BAD_FILE_NAME(553, "Requested action not taken. File name not allowed.");
	
	public final int code;
	public final String message;
	
	FTPResponse(int code, String message) {
		this.code = code;
		this.message = message;
	}
	
	private static final Map<Integer, FTPResponse> responseMap;
	static {
		responseMap = new HashMap<Integer, FTPResponse>();
		for (FTPResponse r: FTPResponse.values()) {
			responseMap.put(r.code, r);
		}
	}
	
	public static FTPResponse getByCode(int code) {
		return responseMap.get(code);
	}

}
