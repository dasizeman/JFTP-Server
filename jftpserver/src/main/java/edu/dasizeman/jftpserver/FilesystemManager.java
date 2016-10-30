package edu.dasizeman.jftpserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles navigating the file system, reading/writing files,
 * and jailing the user to a virtual root directory
 * @author Dave Sizer <dave@sizetron.net>
 *
 */
public class FilesystemManager {
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
	
	public String pwd() {
		Path relativeTo;
		//if (rootPath.getParent() == null)
			relativeTo = rootPath;
		//else
			//relativeTo = rootPath.getParent();
		String output = relativeTo.relativize(currentPath.toAbsolutePath()).normalize().toString().replaceAll("\\\\", "/");
		return String.format("\"%s/\"", output);
	}
	
	public String ls() {
		StringBuffer result = new StringBuffer();
		File currentDirectory = currentPath.toAbsolutePath().toFile();
		for (File file : currentDirectory.listFiles()) {
			result.append(String.format("%s\r\n",printFile(file)));
			
		}
		
		return result.toString();
	}
	
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
	
	private String printFile(File file) {
		String res = file.getName();
		if (file.isDirectory())
			res += "/";
		return res;
	}
	
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
