public static void extractFile(File dest, JarInputStream jarIS, JarEntry entry) throws IOException {
	// Get the destination directory path
	File out = new File(dest, entry.getName());

	// Prevent path traversal by sanitizing the file path
	// Normalize the file path to avoid directory traversal (e.g., ../ or absolute paths)
	if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath())) {
		Logger.logError("Blocked path traversal attempt: " + entry.getName());
		throw new IOException("Path traversal detected: " + entry.getName());
	}

	// Ensure the parent directory exist
	ensureParentDirectoryExists(out)

	// Open streams to write the file contents
	try (FileOutputStream fos = new FileOutputStream(out);
		 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

		byte[] buf = new byte[2048];
		int num;
		while ((num = jarIS.read(buf, 0, 2048)) != -1) {
			bos.write(buf, 0, num);
		}
	}
}
