package edu.dasizeman.jftpserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigurationFile extends KVPFile {
	private static ConfigurationFile instance = null;
	private static final String CONF_FILE_NAME = "jftpd.conf";
	
	private static final String LOG_DIR_DEFAULT = "/var/log/jftpd";
	private static final int LOG_NUM_DEFAULT=5;
	
	private static HashMap<String,String> configDefaults;
	private static ConcurrentHashMap<String,String> config;
	
	static {
		configDefaults = new HashMap<String,String>();
		configDefaults.put("logdirectory", LOG_DIR_DEFAULT);
		configDefaults.put("numlogfiles", Integer.toString(LOG_NUM_DEFAULT));
		
	}
	
	public static ConfigurationFile getInstance() throws LineFormatException, IOException {
		if (instance == null) {
			instance = new ConfigurationFile(CONF_FILE_NAME);
			config = instance.parse();
		}
		return instance;
		
	}

	public ConfigurationFile(String filepath) throws FileNotFoundException {
		super(filepath);
	}
	
	
	public ConcurrentHashMap<String,String> parse() throws IOException, LineFormatException {
		ConcurrentHashMap<String,String> entries = super.parse();
		
		// Determine what set of configuration values is not set and needs the default
		Set<String> defaultConfigurations = configDefaults.keySet();
		Set<String> specifiedConfigurations = entries.keySet();
		
		defaultConfigurations.removeAll(specifiedConfigurations);
		
		for (String config : defaultConfigurations) {
			entries.put(config, configDefaults.get(config));
		}
		
		return entries;
		
	}
	
	public String getConfigValue(String key) {
		return config.get(key);
	}

}
