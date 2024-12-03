
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import java.util.jar.JarEntry;
import java.net.URLConnection;
import java.net.URL;

public class JarExtractor {

    // Helper method to create parent directories securely
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

    // Method to extract JAR content to a destination directory
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
                Path destPath = dest.toPath().toRealPath();
                Path entryPath = destPath.resolve(entry.getName()).normalize();

                if (!entryPath.startsWith(destPath)) {
                    throw new IOException("Path traversal attempt detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    extractFile(destPath.toFile(), jarIS, entry); // Process files securely
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
