package edu.dasizeman.jftpserver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.concurrent.ConcurrentHashMap;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonValue.ValueType;

/**
 * A very stupid credential manager that loads credentials from
 * a plaintext JSON file, as basic String:String KVPs.  Currently
 * looks for 'creds.json' in default directory, and supports
 * anons
 * @author Dave Sizer <dave@sizetron.net>
 * @since 10/28/16
 */
public class JSONCredentialManager {
	public static final String CRED_FILE_PATH = "creds.json";
	public static final String ANON_USERNAME = "anonymous";
	private static JSONCredentialManager instance = null;
	
	private ConcurrentHashMap<String, String> credentialMap;
	private boolean allowAnonymous = true;
	
	public static JSONCredentialManager getInstance() {
		if (instance == null)
			instance = new JSONCredentialManager();
		
		return instance;
	}
	
	public JSONCredentialManager() {
		credentialMap = new ConcurrentHashMap<String,String>();
	}
	
	public void loadCredentialFile(String path) {
		try {
			JsonReader jsonReader = Json.createReader(new FileInputStream(path));
			JsonObject credentialObject = jsonReader.readObject();
			
			for (String user : credentialObject.keySet()) {
				JsonValue potentialPass = credentialObject.get(user);
				if (potentialPass.getValueType() != ValueType.STRING)
					continue;
				String pass = credentialObject.getString(user);
				credentialMap.put(user, pass);
			}
		} catch (FileNotFoundException e) {
			System.out.println(e);
			System.exit(1);
		}
		
	}
	
	public void writeCredentialFile() throws FileNotFoundException {
		JsonWriter jsonWriter = Json.createWriter(new FileOutputStream(CRED_FILE_PATH));
		JsonObjectBuilder builder = Json.createObjectBuilder();
		for (String name : credentialMap.keySet()) {
			builder.add(name, credentialMap.get(name));
		}
		jsonWriter.writeObject(builder.build());
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
