package edu.dasizeman.jftpserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens and parses a file with lines of the form "key"="value"
 * into a string,string map
 * @author Dave Sizer <dave@sizetron.net>
 *
 */
public class KVPFile {
	private BufferedReader reader;
	private File file;

	public KVPFile(String filepath) throws FileNotFoundException {
		init(filepath);
	}
	
	public ConcurrentHashMap<String,String> parse() throws IOException, LineFormatException {
		String line = null;
		ConcurrentHashMap<String,String> kvMap = new ConcurrentHashMap<String,String>();
		int lineIdx = 1;
		while ((line = reader.readLine()) != null) {
			line = line.replace(" ", "");
			if (isCommentLine(line) || line.equals("")) 
				continue;
			
			Pattern lineFormatPattern = Pattern.compile(getLinePattern());
			Matcher lineFormatMatcher = lineFormatPattern.matcher(line);
			
			if (!lineFormatMatcher.find())
				throw new LineFormatException(String.format("%s: Failed to parse line %d", file.getName(), lineIdx));
			
			String[] tokens = line.split("=");
			
			kvMap.put(tokens[0], tokens[1]);
				
			lineIdx++;
		}
		
		return kvMap;
	}
	
	private void init(String filepath) throws FileNotFoundException {
		file = new File(filepath);
		reader = new BufferedReader(new FileReader(file));
	}
	
	private boolean isCommentLine(String line) {
		return line.startsWith(getCommentDelimiter());
	}
	
	protected String getCommentDelimiter() {
		return "#";
	}
	
	protected String getLinePattern() {
		return "^(.+)=(.+)$";
	}
}
