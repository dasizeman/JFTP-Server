package edu.dasizeman.jftpserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * Handles navigating the file system, reading/writing files,
 * and jailing the user to a virtual root directory
 * @author Dave Sizer <dave@sizetron.net>
 *
 */
public class FilesystemManager {
	private static final Logger logger = Logger.getGlobal();
	
	// These are always absolute paths
	private String rootPath;
	private String currentPath;
	
	/**
	 * Create a new filesystem manager
	 * @param root The virtual root oath that the user must stay in
	 */
	public FilesystemManager(String rootStr) {
		if (rootStr.equals(""))
			rootStr = ".";
		
		try {
			//rootPath = Paths.get(rootStr).toAbsolutePath().normalize().toRealPath();
			
			File rootDir = FileUtils.getFile(rootStr);
			if (!rootDir.exists() || !rootDir.isDirectory())
				throw new FileNotFoundException(String.format("Could not open root directory: %s", rootStr));
			rootPath = rootDir.getAbsolutePath();
			
		} catch (IOException e) {
			// Assuming . is always a valid path
			//rootPath = Paths.get(".").toAbsolutePath().normalize();
			EventLogger.logGeneralException(logger, "Filesystem setup", e);
			System.exit(1);
		}
		
		currentPath = rootPath;
	}
	
	/**
	 * Get the current working directory
	 * @return A string representing the cwd
	 */
	public String pwd() {
		return String.format("%s/",
				Paths.get(rootPath).relativize(Paths.get(currentPath)).toString()
				.replaceAll("\\\\", "/"));
	}
	
	/**
	 * List the files in the current directory.
	 * TODO make this a fancier unix-style listing 
	 * @return A string containing the directory listing
	 */
	public String ls() {
		StringBuffer result = new StringBuffer();
		File currentDirectory = FileUtils.getFile(currentPath);
		for (File file : currentDirectory.listFiles()) {
			result.append(String.format("%s\r\n",printFile(file)));
		}
		
		return result.toString();
	}
	
	/**
	 * Change the current working directory, checking the candidate path's validity
	 * @param pathStr The path to change to
	 * @throws FileNotFoundException If the directory does not exist
	 */
	public void cd(String pathStr) throws FileNotFoundException {
		Path path = Paths.get(pathStr);
		if (!path.isAbsolute()) {
			String prefix;
			if (pathStr.startsWith("/")) {
				pathStr = pathStr.substring(1);
				prefix = rootPath;
			}
			else
				prefix = currentPath;
			String normalized = FilenameUtils.normalize(FilenameUtils.concat(prefix, pathStr));
			path = Paths.get(normalized);
		}
		
		if (pathExists(path.toString()) && isPathInRoot(path)) {
			currentPath = path.toString();
		} else {
			throw new FileNotFoundException("Invalid path.");
		}
	}
	
	public boolean pathExists(String path) {
		File directory = FileUtils.getFile(path);
		return (directory.exists() && directory.isDirectory());
	}
	
	/**
	 * Check if the current directory has the given file, and return a stream to read it
	 * @param filename The file to open
	 * @return The stream to the file, or null if it is not valid
	 */
	public FileInputStream getFileStream(String filename) {
		File currentDirectory = FileUtils.getFile(currentPath);
		File targetFile = FileUtils.getFile(currentDirectory, filename);
		try {
			FileInputStream result = FileUtils.openInputStream(targetFile);
			return result;
		} catch (IOException e) {
			EventLogger.logGeneralException(logger, "Opening file", e);
			return null;
		}
	}
	
	/**
	 * Append the directory / to directories
	 * @param file The File object to print
	 * @return The string to be printed
	 */
	private String printFile(File file) {
		String res = file.getName();
		if (file.isDirectory())
			res += "/";
		return res;
	}
	
	/**
	 * Verify that the given path exists and is within the virtual
	 * root filesystem, and return a Path object for it
	 * @param path The path to validate
	 * @return The Path object representing the valid path, or null if it isn't valid
	 */
	private boolean isPathInRoot(Path path) {
		Path parentCheck = path;
		Path root = Paths.get(rootPath);
		if (parentCheck.equals(root))
			return true;
		
		while (parentCheck.getParent() != null) {
			if (parentCheck.getParent().equals(root))
				return true;
			parentCheck = parentCheck.getParent();
		}
		
		return false;
	}
	
	

}
