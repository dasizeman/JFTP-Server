package edu.dasizeman.jftpserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Handles navigating the file system, reading/writing files,
 * and jailing the user to a virtual root directory
 * @author Dave Sizer <dave@sizetron.net>
 *
 */
public class FilesystemManager {
	private static final Logger logger = Logger.getGlobal();
	private Path rootPath;
	private Path currentPath;
	
	/**
	 * Create a new filesystem manager
	 * @param root The virtual root oath that the user must stay in
	 */
	public FilesystemManager(String rootStr) {
		if (rootStr.equals(""))
			rootStr = ".";
		
		try {
			rootPath = Paths.get(rootStr).toAbsolutePath().normalize().toRealPath();
		} catch (IOException e) {
			// Assuming . is always a valid path
			rootPath = Paths.get(".").toAbsolutePath().normalize();
		}
		
		currentPath = rootPath.relativize(rootPath);
	}
	
	/**
	 * Get the current working directory
	 * @return A string representing the cwd
	 */
	public String pwd() {
		Path relativeTo;
		relativeTo = rootPath;
		String output = relativeTo.relativize(currentPath.toAbsolutePath()).normalize().toString().replaceAll("\\\\", "/");
		return String.format("\"%s/\"", output);
	}
	
	/**
	 * List the files in the current directory.
	 * TODO make this a fancier unix-style listing 
	 * @return A string containing the directory listing
	 */
	public String ls() {
		StringBuffer result = new StringBuffer();
		File currentDirectory = currentPath.toAbsolutePath().toFile();
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
		if (pathStr.startsWith("/")) {
			currentPath = rootPath.relativize(rootPath);
		}
		if (currentPath.toString().equals(""))
			currentPath = Paths.get(".");
		Path path = Paths.get(pathStr);
		if (!path.isAbsolute()) {
			pathStr = String.join("/", currentPath.toString(), path.toString());
		}
		Path realPath = getRealPath(pathStr);
		if (realPath == null || !realPath.toFile().isDirectory())
			throw new FileNotFoundException(String.format("Invalid path: %s", Paths.get(pathStr).toString().replaceAll("\\\\", "/")));
		currentPath = rootPath.relativize(realPath);
			
	}
	
	/**
	 * Check if the current directory has the given file, and return a stream to read it
	 * @param filename The file to open
	 * @return The stream to the file, or null if it is not valid
	 */
	public FileInputStream getFileStream(String filename) {
		File currentDirectory = currentPath.toAbsolutePath().toFile();
		for (File file : currentDirectory.listFiles()) {
			if (file.isFile() && file.getName().equals(filename)) {
				try {
					return new FileInputStream(file);
				} catch (FileNotFoundException e) {
					EventLogger.logGeneralException(logger,"File stream open" ,e);
					break;
				}
			}
		}
		return null;
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
	private Path getRealPath(String path) {
		Path real;
		try {
			real = Paths.get(path).toAbsolutePath().normalize().toRealPath();
		} catch (IOException e) {
			return null;
		}
		Path parentCheck = real;
		if (parentCheck.equals(rootPath))
			return real;
		
		while (parentCheck.getParent() != null) {
			if (parentCheck.getParent().equals(rootPath))
				return real;
			parentCheck = parentCheck.getParent();
		}
		
		return null;
	}
	
	

}
