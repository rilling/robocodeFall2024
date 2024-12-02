
package net.sf.robocode.repository.packager;

import net.sf.robocode.io.FileUtil;
import net.sf.robocode.io.Logger;
import net.sf.robocode.io.URLJarCollector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.jar.JarInputStream;
import java.util.jar.JarEntry;
import java.net.URLConnection;
import java.net.URL;

public class JarExtractor {

	// Helper method to create parent directories
	private static void ensureParentDirectoryExists(File file) {
		File parentDirectory = new File(file.getParent());
		if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
			Logger.logError("Cannot create parent dir: " + parentDirectory);
		}
	}

	public static void extractJar(URL url) {
		File dest = FileUtil.getRobotsDir();
		InputStream is = null;
		BufferedInputStream bis = null;
		JarInputStream jarIS = null;

		try {
			final URLConnection con = URLJarCollector.openConnection(url);

			is = con.getInputStream();
			bis = new BufferedInputStream(is);
			jarIS = new JarInputStream(bis);

			JarEntry entry = jarIS.getNextJarEntry();

			while (entry != null) {
				if (entry.isDirectory()) {
					File dir = new File(dest, entry.getName());
					// Use the helper method to create the parent directory
					ensureParentDirectoryExists(dir);
				} else {
					extractFile(dest, jarIS, entry); // Process files using the existing extractFile method
				}
				entry = jarIS.getNextJarEntry();
			}
		} catch (IOException e) {
			Logger.logError(e);
		} finally {
			FileUtil.cleanupStream(jarIS);
			FileUtil.cleanupStream(bis);
			FileUtil.cleanupStream(is);
		}
	}

	public static void extractFile(File dest, JarInputStream jarIS, JarEntry entry) throws IOException {
		// Create the file to be written to
		File out = new File(dest, entry.getName());

		// Sanitize the path to prevent directory traversal
		// Ensure the file is within the destination directory
		if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath())) {
			Logger.logError("Blocked path traversal attempt: " + entry.getName());
			throw new IOException("Path traversal detected: " + entry.getName());
		}
		// Ensure the parent directory exists
		ensureParentDirectoryExists(out);

		try (FileOutputStream fos = new FileOutputStream(out);
			 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

			byte[] buf = new byte[2048];
			int num;
			while ((num = jarIS.read(buf, 0, 2048)) != -1) {
				bos.write(buf, 0, num);
			}
		} catch (IOException e) {
			Logger.logError("Error writing file: " + out.getAbsolutePath(), e);
			throw e;
		}
	}

}
