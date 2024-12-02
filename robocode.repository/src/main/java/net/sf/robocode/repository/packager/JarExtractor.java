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

    // Method to extract individual file entries
    public static void extractFile(File dest, JarInputStream jarIS, JarEntry entry) throws IOException {
        // Validate and sanitize the entry name
        String entryName = entry.getName();

        // Reject absolute paths or traversal attempts
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw new IOException("Invalid path detected: " + entryName);
        }

        // Resolve and normalize the output path
        Path destPath = dest.toPath().toRealPath();
        Path outPath = destPath.resolve(entryName).normalize();

        if (!outPath.startsWith(destPath)) {
            throw new IOException("Path traversal attempt detected: " + entryName);
        }

        File out = outPath.toFile();

        // Use the helper method to create the parent directory
        ensureParentDirectoryExists(out);

        // Write the file content
        try (FileOutputStream fos = new FileOutputStream(out);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buf = new byte[2048];
            int num;
            while ((num = jarIS.read(buf, 0, buf.length)) != -1) {
                bos.write(buf, 0, num);
            }
        }
    }
}
