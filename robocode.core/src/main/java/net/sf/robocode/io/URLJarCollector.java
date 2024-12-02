/*
 * Copyright (c) 2001-2023 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://robocode.sourceforge.io/license/epl-v10.html
 */
package net.sf.robocode.io;

import java.net.URLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.InetAddress;
import java.lang.reflect.Field;
import java.util.*;
import java.util.jar.JarFile;
import java.io.File;
import java.io.IOException;

/**
 * This class helps with closing of robot .jar files when used with URL, URLConnection, and useCaches=true.
 * It is designed to close JarFiles opened and cached in SUN's JarFileFactory.
 * 
 * @author Pavel Savara
 * @author Flemming N. Larsen
 */
public class URLJarCollector {

    private static HashMap<?, ?> fileCache;
    private static HashMap<?, ?> urlCache;
    private static Field jarFileURL;
    private static final boolean sunJVM;
    private static boolean enabled;
    private static final Set<URL> urlsToClean;

    static {
        boolean localSunJVM = false;

        try {
            final Class<?> jarFactory = ClassLoader.getSystemClassLoader().loadClass(
                    "sun.net.www.protocol.jar.JarFileFactory");
            final Field fileCacheF = jarFactory.getDeclaredField("fileCache");

            fileCacheF.setAccessible(true);
            fileCache = (HashMap<?, ?>) fileCacheF.get(null);

            final Field urlCacheF = jarFactory.getDeclaredField("urlCache");

            urlCacheF.setAccessible(true);
            urlCache = (HashMap<?, ?>) urlCacheF.get(null);

            final Class<?> jarURLConnection = ClassLoader.getSystemClassLoader().loadClass(
                    "sun.net.www.protocol.jar.JarURLConnection");

            jarFileURL = jarURLConnection.getDeclaredField("jarFileURL");
            jarFileURL.setAccessible(true);

            localSunJVM = true;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignore) {
        }
        sunJVM = localSunJVM;

        urlsToClean = Collections.newSetFromMap(new WeakHashMap<>());
    }

    public static synchronized URLConnection openConnection(URL url) throws IOException {
        validateURL(url); // Add URL validation to prevent SSRF

        final URLConnection urlConnection = url.openConnection();

        if (sunJVM) {
            registerConnection(urlConnection);
            urlConnection.setUseCaches(true);
        } else {
            urlConnection.setUseCaches(false);
        }
        return urlConnection;
    }

    public static synchronized void enableGc(boolean enabled) {
        URLJarCollector.enabled = enabled;
    }

    public static synchronized void gc() {
        if (sunJVM) {
            if (enabled) {
                synchronized (urlsToClean) {
                    for (URL url : urlsToClean) {
                        closeJarURLConnection(url);
                    }
                    urlsToClean.clear();
                }
            }

            for (Iterator<?> it = fileCache.keySet().iterator(); it.hasNext();) {
                Object urlJarFile = it.next();

                final JarFile jarFile = (JarFile) fileCache.get(urlJarFile);

                String filename = jarFile.getName();
                filename = filename.substring(filename.lastIndexOf(File.separatorChar) + 1).toLowerCase();

                if (filename.startsWith("jar_cache")) {
                    it.remove();
                    synchronized (urlCache) {
                        urlCache.remove(jarFile);
                    }
                }
            }
        }
    }

    private static void registerConnection(URLConnection conn) {
        if (conn != null) {
            final String cl = conn.getClass().getName();

            if (cl.equals("sun.net.www.protocol.jar.JarURLConnection")) {
                try {
                    final URL url = (URL) jarFileURL.get(conn);

                    if (!urlsToClean.contains(url)) {
                        synchronized (urlsToClean) {
                            urlsToClean.add(url);
                        }
                    }
                } catch (IllegalAccessException ignore) {}
            }
        }
    }

    public synchronized static void closeJarURLConnection(URL url) {
        if (url != null) {
            for (Iterator<?> it = fileCache.keySet().iterator(); it.hasNext();) {
                Object urlJarFile = it.next();

                final JarFile jarFile = (JarFile) fileCache.get(urlJarFile);

                String urlPath = url.getPath();

                try {
                    urlPath = URLDecoder.decode(urlPath, "UTF-8");
                } catch (java.io.UnsupportedEncodingException ignore) {}

                File urlFile = new File(urlPath);

                String jarFileName = jarFile.getName();
                String urlFileName = urlFile.getPath();

                if (urlFileName.equals(jarFileName)) {
                    it.remove();
                    synchronized (urlCache) {
                        urlCache.remove(jarFile);
                    }
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        Logger.logError(e);
                    }
                }
            }
        }
    }

    /**
     * Validates a given URL to mitigate SSRF vulnerabilities.
     * @param url The URL to validate.
     * @throws IOException If the URL is invalid or unsafe.
     */
    private static void validateURL(URL url) throws IOException {
        if (url == null) {
            throw new IOException("URL cannot be null");
        }

        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IOException("Unsupported protocol: " + protocol);
        }

        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("URL must have a valid host");
        }

        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")) {
            throw new IOException("Access to localhost is prohibited");
        }

        if (isPrivateIPAddress(host)) {
            throw new IOException("Access to private IP addresses is prohibited");
        }
    }

    /**
     * Checks if a given host is a private IP address.
     * @param host The host to check.
     * @return true if the host is a private IP address; false otherwise.
     */
    private static boolean isPrivateIPAddress(String host) {
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            return inetAddress.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
