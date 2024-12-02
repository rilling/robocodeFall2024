import net.sf.robocode.io.FileUtil;
import net.sf.robocode.io.Logger;
import net.sf.robocode.io.URLJarCollector;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
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
			if (dest == null || !dest.exists()) {
				Logger.logError("Destination directory is invalid: " + dest);
				return;
			}

			final URLConnection con = URLJarCollector.openConnection(url);
			is = con.getInputStream();
			bis = new BufferedInputStream(is);
			jarIS = new JarInputStream(bis);

			JarEntry entry = jarIS.getNextJarEntry();
			while (entry != null) {
				if (entry.isDirectory()) {
					File dir = new File(dest, entry.getName());
					ensureParentDirectoryExists(dir);
				} else {
					extractFile(dest, jarIS, entry); // Process files using the existing extractFile method
				}
				entry = jarIS.getNextJarEntry();
			}
		} catch (IOException e) {
			Logger.logError("Error while extracting jar: " + e.getMessage());
			e.printStackTrace();
		} finally {
			FileUtil.cleanupStream(jarIS);
			FileUtil.cleanupStream(bis);
			FileUtil.cleanupStream(is);
		}
	}

	public static void extractFile(File dest, JarInputStream jarIS, JarEntry entry) throws IOException {
		// Get the file path from the jar entry
		File out = new File(dest, entry.getName());

		// Normalize and validate the file path to prevent path traversal
		Path destPath = dest.getCanonicalFile().toPath();
		Path outPath = destPath.resolve(entry.getName()).normalize();

		// Ensure that the file is inside the intended destination directory
		if (!outPath.startsWith(destPath)) {
			Logger.logError("Invalid file entry (path traversal attempt): " + entry.getName());
			throw new IOException("Invalid file entry: " + entry.getName());
		}

		// Ensure parent directories exist
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
		} catch (IOException e) {
			Logger.logError("Error writing file: " + e.getMessage());
			throw e;
		} finally {
			FileUtil.cleanupStream(bos);
			FileUtil.cleanupStream(fos);
		}
	}
}
