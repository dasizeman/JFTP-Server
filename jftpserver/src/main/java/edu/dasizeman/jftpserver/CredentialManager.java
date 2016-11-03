package edu.dasizeman.jftpserver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A very stupid credential manager that loads credentials from
 * a plaintext JSON file, as basic String:String KVPs.  Currently
 * looks for 'creds.json' in default directory, and supports
 * anons
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 */
public class CredentialManager {
	public static final String CRED_FILE_PATH = "creds.conf";
	public static final String ANON_USERNAME = "anonymous";
	public static final Logger logger = Logger.getGlobal();
	private static CredentialManager instance = null;
	
	private ConcurrentHashMap<String, String> credentialMap;
	private boolean allowAnonymous = true;
	
	public static CredentialManager getInstance() {
		if (instance == null)
			instance = new CredentialManager();
		
		return instance;
	}
	
	public CredentialManager() {
		credentialMap = new ConcurrentHashMap<String,String>();
	}
	
	
	public void loadCredentialFile(String path) {
		try {
			KVPFile credFile = new KVPFile(path);
			credentialMap = credFile.parse();
		} catch (LineFormatException | IOException e) {
			EventLogger.logGeneralException(logger, e);
			System.exit(1);
		}
		
	}
	
	
	public boolean checkCredential(String username, String password) {
		if (username.equals(ANON_USERNAME) && allowAnonymous) {
			return true;
		}
		String correctPassValue = credentialMap.get(username);
		return (password.equals(correctPassValue))?true:false;
	}
	
	public void addCredential(String username, String password) {
		credentialMap.put(username, password);
	}
	
	

}
