package edu.dasizeman.jftpserver;

import java.util.HashMap;
import java.util.Map;

public enum FTPCommand {
	USER,
	PASS,
	CWD,
	CDUP,
	QUIT,
	PASV,
	EPSV,
	PORT,
	EPRT,
	RETR,
	PWD,
	LIST, 
	HELP,
	TYPE,
	NOOP;
	
	private static final Map<String, FTPCommand> commandMap; 
	static {
		commandMap = new HashMap<String, FTPCommand>();
		for (FTPCommand command : FTPCommand.values()) {
			commandMap.put(command.name(), command);
		}
	}
	
	public static FTPCommand getByName(String name) {
		return commandMap.get(name.toUpperCase());
	}
}
