package com.beust.kobalt.wrapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {
    public static void main(String[] argv) throws IOException, InterruptedException {
        new Main().installAndLaunchMain(argv);
    }

    private static final String KOBALT_PROPERTIES = "kobalt.properties";
    private static final String KOBALTW = "kobaltw";
    private static final String KOBALT_WRAPPER_PROPERTIES = "kobalt-wrapper.properties";
    private static final String PROPERTY_VERSION = "kobalt.version";
    private static final String URL = "https://dl.bintray.com/cbeust/generic/";
    private static final String FILE_NAME = "kobalt";
    private static final String DISTRIBUTIONS_DIR =
            System.getProperty("user.home") + "/.kobalt/wrapper/dist";

    private final Properties properties = new Properties();

    private static int logLevel = 1;

    private void installAndLaunchMain(String[] argv) throws IOException, InterruptedException {
        for (int i = 0; i < argv.length; i++) {
            switch(argv[i]) {
                case "--log":
                    logLevel = Integer.parseInt(argv[i + 1]);
                    i++;
                    break;
            }
        }
        Path kobaltJarFile = installJarFile();
        launchMain(kobaltJarFile, argv);
    }

    private void readProperties(Properties properties, InputStream ins) throws IOException {
        properties.load(ins);
        ins.close();
    }

    private Properties maybeCreateProperties() throws IOException {
        Properties result = new Properties();
        URL url = getClass().getClassLoader().getResource(KOBALT_PROPERTIES);
        if (url != null) {
            readProperties(result, url.openConnection().getInputStream());
        } else {
            throw new IllegalArgumentException("Couldn't find " + KOBALT_PROPERTIES);
        }
        return result;
    }

    private File getWrapperDir() {
        return new File("kobalt", "wrapper");
    }

    private void initWrapperFile(String version) throws IOException {
        File config = new File(getWrapperDir(), KOBALT_WRAPPER_PROPERTIES);
        if (! config.exists()) {
            saveFile(config, PROPERTY_VERSION + "=" + version);
        }
        properties.load(new FileReader(config));
    }

    private String getWrapperVersion() {
        return properties.getProperty(PROPERTY_VERSION);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    private Path installJarFile() throws IOException {
        Properties properties = maybeCreateProperties();
        String version = properties.getProperty(PROPERTY_VERSION);
        initWrapperFile(version);

        log(2, "Wrapper version: " + getWrapperVersion());

        String fileName = FILE_NAME + "-" + getWrapperVersion() + ".zip";
        new File(DISTRIBUTIONS_DIR).mkdirs();
        Path localZipFile = Paths.get(DISTRIBUTIONS_DIR, fileName);
        String zipOutputDir = DISTRIBUTIONS_DIR + "/" + getWrapperVersion();
        Path kobaltJarFile = Paths.get(zipOutputDir,
                getWrapperDir().getPath() + "/" + FILE_NAME + "-" + getWrapperVersion() + ".jar");
        if (! Files.exists(localZipFile) || ! Files.exists(kobaltJarFile)) {
            if (!Files.exists(localZipFile)) {
                String fullUrl = URL + "/" + fileName;
                download(fullUrl, localZipFile.toFile());
                if (!Files.exists(localZipFile)) {
                    log(2, localZipFile + " downloaded, extracting it");
                } else {
                    log(2, localZipFile + " already exists, extracting it");
                }
            }

            //
            // Extract all the zip files
            //
            ZipFile zipFile = new ZipFile(localZipFile.toFile());
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            File outputDirectory = new File(DISTRIBUTIONS_DIR);
            outputDirectory.mkdirs();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    Path dest = Paths.get(zipOutputDir, entryFile.getPath());
                    log(2, "  Writing " + entry.getName() + " to " + dest);
                    Files.createDirectories(dest.getParent());
                    Files.copy(zipFile.getInputStream(entry), dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        //
        // Copy the wrapper files in the current kobalt/wrapper directory
        //
        log(2, "Copying the wrapper files");
        for (String file : FILES) {
            Path from = Paths.get(zipOutputDir, file);
            Path to = Paths.get(new File(".").getAbsolutePath(), file);
            try {
                if (isWindows() && to.toFile().exists()) {
                    log(1, "Windows detected, not overwriting " + to);
                } else {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch(IOException ex) {
                log(1, "Couldn't copy " + from + " to " + to + ": " + ex.getMessage());
            }
        }
        new File(KOBALTW).setExecutable(true);
        return kobaltJarFile;
    }

    private static final String[] FILES = new String[] { KOBALTW, "kobalt/wrapper/" + FILE_NAME + "-wrapper.jar" };

    private void download(String fileUrl, File file) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    fileName = disposition.substring(index + 10,
                            disposition.length() - 1);
                }
            } else {
                // extracts file name from URL
                fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1,
                        fileUrl.length());
            }

            log(2, "Content-Type = " + contentType);
            log(2, "Content-Disposition = " + disposition);
            log(2, "Content-Length = " + contentLength);
            log(2, "fileName = " + fileName);

            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();

            // opens an output stream to save into file
            FileOutputStream outputStream = new FileOutputStream(file);

            int bytesRead = -1;
            long bytesSoFar = 0;
            byte[] buffer = new byte[100_000];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesSoFar += bytesRead;
                if (bytesRead > 0) {
                    if (contentLength > 0) {
                        float percent = bytesSoFar * 100 / contentLength;
                        log2(1, "\rDownloading " + url + " " + percent + "%");
                    } else {
                        log2(1, ".");
                    }
                }
            }
            log2(1, "\n");

            outputStream.close();
            inputStream.close();

            log(1, "Downloaded " + fileUrl);
        } else {
            error("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }

    private void saveFile(File file, String text) throws IOException {
        file.getAbsoluteFile().getParentFile().mkdirs();
        file.delete();
        log(2, "Wrote " + file);
        Files.write(Paths.get(file.toURI()), text.getBytes());
    }

    private static void log2(int level, String s) {
        p(level, s, false);
    }

    private static void log(int level, String s) {
        p(level, "[Wrapper] " + s, true);
    }

    private static void p(int level, String s, boolean newLine) {
        if (level <= logLevel) {
            if (newLine) System.out.println(s);
            else System.out.print(s);
        }
    }

    private void error(String s) {
        System.out.println("[Wrapper error] *** " + s);
    }

    private static final String KOBALT_MAIN_CLASS = "com.beust.kobalt.KobaltPackage";

    private void launchMain(Path kobaltJarFile, String[] argv) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-jar");
        args.add(kobaltJarFile.toFile().getAbsolutePath());
        for (String arg : argv) {
            args.add(arg);
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
        log(2, "Launching " + args);
        Process process = pb.start();
        process.waitFor();

    }

}