package edu.dasizeman.jftpserver;

import javax.json.JsonObject;

public class JSONCredentialManager {
	private static final String CRED_FILE_PATH = "creds.json";
	private static JSONCredentialManager instance = null;
	
	private JsonObject credentialMap;
	
	public static JSONCredentialManager getInstance() {
		if (instance == null)
			instance = new JSONCredentialManager();
		
		return instance;
	}
	
	public JSONCredentialManager() {
	}
	
	

}
