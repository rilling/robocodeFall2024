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
		try {
			File parentDirectory = new File(file.getParentFile().getCanonicalPath());

			File canonicalFile = file.getCanonicalFile();

			if (!parentDirectory.equals(canonicalFile.getParentFile())) {
				throw new SecurityException("Path traversal attempt detected: " + parentDirectory);
			}

			if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
				Logger.logError("Cannot create parent dir: " + parentDirectory);
			}
		} catch (IOException e) {
			Logger.logError("Error validating or creating parent directory: " + e.getMessage());
			throw new RuntimeException(e);
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
		File out = new File(dest, entry.getName());

		// Use the helper method to create the parent directory
		ensureParentDirectoryExists(out);

		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		byte[] buf = new byte[2048];

		try {
			fos = new FileOutputStream(out);
			bos = new BufferedOutputStream(fos);

			int num;
			while ((num = jarIS.read(buf, 0, 2048)) != -1) {
				bos.write(buf, 0, num);
			}
		} finally {
			FileUtil.cleanupStream(bos);
			FileUtil.cleanupStream(fos);
		}
	}
}
