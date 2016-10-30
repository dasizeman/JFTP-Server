package edu.dasizeman.jftpserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FilesystemManager {
	private Path rootPath;
	private Path currentPath;
	
	public FilesystemManager(String root) {
		if (root.equals(""))
			root = ".";
		
		rootPath = Paths.get(root).toAbsolutePath().normalize();
		currentPath = rootPath.relativize(rootPath);
	}
	
	public String pwd() {
		Path relativeTo;
		if (rootPath.getParent() == null)
			relativeTo = rootPath;
		else
			relativeTo = rootPath.getParent();
		String output = relativeTo.relativize(currentPath.toAbsolutePath()).normalize().toString().replaceAll("\\\\", "/");
		return String.format("%s/", output);
	}
	
	public String ls() {
		StringBuffer result = new StringBuffer();
		File currentDirectory = currentPath.toAbsolutePath().toFile();
		for (File file : currentDirectory.listFiles()) {
			result.append(String.format("%s\n",printFile(file)));
			
		}
		
		return result.toString();
	}
	
	public void cd(String pathStr) throws FileNotFoundException {
		if (currentPath.toString().equals(""))
			currentPath = Paths.get(".");
		if (pathStr.equals("/")) {
			currentPath = rootPath.relativize(rootPath);
			return;
		}
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
