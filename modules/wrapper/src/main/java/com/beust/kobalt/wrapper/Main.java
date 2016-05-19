package com.beust.kobalt.wrapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

//@com.beust.apt.processor.Version("1.3")
public class Main {
    public static void main(String[] argv) throws IOException, InterruptedException {
        int exitCode = new Main().installAndLaunchMain(argv);
        System.exit(exitCode);
    }

    private static final boolean DEV = false;
    private static final int DEV_VERSION_INT = 662;
    private static final String DEV_VERSION = "0." + DEV_VERSION_INT;
    private static final String DEV_ZIP = "/Users/beust/kotlin/kobalt/kobaltBuild/libs/kobalt-" + DEV_VERSION + ".zip";

    private static final String KOBALT_PROPERTIES = "kobalt.properties";
    private static final String KOBALTW = "kobaltw";
    private static final String KOBALT_WRAPPER_PROPERTIES = "kobalt-wrapper.properties";
    private static final String PROPERTY_VERSION = "kobalt.version";
    private static final String PROPERTY_DOWNLOAD_URL = "kobalt.downloadUrl";
    private static final String FILE_NAME = "kobalt";
    private static final String DISTRIBUTIONS_DIR =
            System.getProperty("user.home") + "/.kobalt/wrapper/dist";
    private static final File VERSION_TXT = new File(".kobalt", "wrapperVersion.txt");

    private final Properties wrapperProperties = new Properties();

    private static int logLevel = 1;
    private boolean noOverwrite = false;

    private String getVersion() throws IOException {
        Properties properties = maybeCreateProperties();
        return properties.getProperty(PROPERTY_VERSION);
    }

    private int installAndLaunchMain(String[] argv) throws IOException, InterruptedException {
        String version = getVersion();
        initWrapperFile(version);

        List<String> kobaltArgv = new ArrayList<>();
        boolean noLaunch = false;
        boolean exit = false;
        for (int i = 0; i < argv.length; i++) {
            boolean passToKobalt = true;
            switch(argv[i]) {
                case "--version":
                    System.out.println("Kobalt " + version + ", Wrapper " + getWrapperVersion());
                    exit = true;
                    break;
                case "--noOverwrite":
                    noOverwrite = true;
                    passToKobalt = false;
                    break;
                case "--noLaunch":
                    noLaunch = true;
                    break;
                case "--log":
                    logLevel = Integer.parseInt(argv[i + 1]);
                    kobaltArgv.add(argv[i]);
                    i++;
                    break;
            }
            if (passToKobalt) {
                kobaltArgv.add(argv[i]);
            }
        }
        int result = 0;
        if (! exit) {
            Path kobaltJarFile = installDistribution();
            if (!noLaunch) {
                result = launchMain(kobaltJarFile, kobaltArgv.toArray(new String[kobaltArgv.size()]));
            }
        }
        return result;
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
            File file = new File("src/main/resources/kobalt.properties");
            if (file.exists()) {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(file);
                    readProperties(result, fis);
                } finally {
                    if (fis != null) fis.close();
                }
            } else {
                throw new IllegalArgumentException("Couldn't find " + KOBALT_PROPERTIES);
            }
        }
        return result;
    }

    private File getWrapperDir() {
        return new File("kobalt", "wrapper");
    }

    private static String downloadUrl(String version) {
        return "http://beust.com/kobalt/kobalt-" + version + ".zip";
    }

    private void initWrapperFile(String version) throws IOException {
        File config = new File(getWrapperDir(), KOBALT_WRAPPER_PROPERTIES);
        if (! config.exists()) {
            saveFile(config,
                    PROPERTY_VERSION + "=" + version + "\n"
//                    + PROPERTY_DOWNLOAD_URL + "=" + downloadUrl(version) + "\n"
            );
        }
        wrapperProperties.load(new FileReader(config));
    }

    private String getWrapperVersion() {
        return wrapperProperties.getProperty(PROPERTY_VERSION);
    }

    private String getWrapperDownloadUrl(String version) {
        String result = wrapperProperties.getProperty(PROPERTY_DOWNLOAD_URL);
        if (result == null) {
            result = downloadUrl(version);
        }
        return result;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    private static final String[] FILES = new String[] { KOBALTW, "kobalt/wrapper/" + FILE_NAME + "-wrapper.jar" };

    private Path installDistribution() throws IOException {
        String wrapperVersion;
        String version;
        Path localZipFile;
        if (DEV) {
            version = DEV_VERSION;
            wrapperVersion = DEV_VERSION;
            localZipFile = Paths.get(DEV_ZIP);
        } else {
            version = getVersion();
            wrapperVersion = getWrapperVersion();
            String fileName = FILE_NAME + "-" + wrapperVersion + ".zip";
            Files.createDirectories(Paths.get(DISTRIBUTIONS_DIR));
            localZipFile = Paths.get(DISTRIBUTIONS_DIR, fileName);
        }

        log(2, "Wrapper version: " + wrapperVersion);

        boolean isNew = Float.parseFloat(version) * 1000 >= 650;

        String toZipOutputDir = DISTRIBUTIONS_DIR;
        Path kobaltJarFile = Paths.get(toZipOutputDir,
                isNew ? "kobalt-" + wrapperVersion : "",
                getWrapperDir().getPath() + "/" + FILE_NAME + "-" + wrapperVersion + ".jar");
        boolean downloadedZipFile = false;
        if (! Files.exists(localZipFile) || ! Files.exists(kobaltJarFile)) {
            download(localZipFile.toFile(), wrapperVersion);
            downloadedZipFile = true;
        }

        //
        // Extract all the zip files
        //
        if (! noOverwrite && downloadedZipFile) {
            int retries = 0;
            while (retries < 2) {
                try {
                    extractZipFile(localZipFile, toZipOutputDir);
                    break;
                } catch (ZipException e) {
                    retries++;
                    error("Couldn't open zip file " + localZipFile + ": " + e.getMessage());
                    error("The file is probably corrupt, downloading it again");
                    Files.delete(localZipFile);
                    download(localZipFile.toFile(), wrapperVersion);
                }
            }
        }

        //
        // If the user didn't specify --noOverwrite, compare the installed wrapper to the one
        // we're trying to install and if they are the same, no need to overwrite
        if (! noOverwrite) {
            if (VERSION_TXT.exists()) {
                try (FileReader fw = new FileReader(VERSION_TXT)) {
                    try (BufferedReader bw = new BufferedReader(fw)) {
                        String installedVersion = bw.readLine();
                        if (!wrapperVersion.equals(installedVersion)) {
                            log(2, "  Trying to install a different version "
                                    + wrapperVersion + " != " + installedVersion
                                    + ", overwriting the installed wrapper");
                            installWrapperFiles(version, wrapperVersion);
                        } else {
                            log(2, "  Trying to install the same version " + wrapperVersion + " == " + installedVersion
                                    + ", not overwriting the installed wrapper");
                        }
                    }
                }
            } else {
                log(2, "  Couldn't find $VERSION_TEXT, overwriting the installed wrapper");
                installWrapperFiles(version, wrapperVersion);
            }
        }

        return kobaltJarFile;
    }

    private void installWrapperFiles(String version, String wrapperVersion) throws IOException {
        String fromZipOutputDir = DISTRIBUTIONS_DIR + File.separator + "kobalt-" + wrapperVersion;

        for (String file : FILES) {
            Path to = Paths.get(file);
            to.toFile().getAbsoluteFile().getParentFile().mkdirs();

            if (file.endsWith(KOBALTW)) {
                generateKobaltW(Paths.get(KOBALTW));
            } else {
                Path from = Paths.get(fromZipOutputDir, file);
                try {
                    if (isWindows() && to.toFile().exists()) {
                        log(2, "  Windows detected, not overwriting " + to);
                    } else {
                        log(2, "  Copying " + from + " to " + to);
                        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    log(1, "  Couldn't copy " + from + " to " + to + ": " + ex.getMessage());
                }
            }
        }

        //
        // Save wrapperVersion.txt
        //
        log(2, "  Writing " + VERSION_TXT);
        File parentDir = VERSION_TXT.getParentFile();
        parentDir.mkdirs();
        if (parentDir.exists()) {
            try (FileWriter fw = new FileWriter(VERSION_TXT)) {
                try (BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(wrapperVersion);
                }
            }
        } else {
            log(1, "Warning: could not create the directory " + parentDir.getAbsolutePath()
                    + ", can't write " + VERSION_TXT);
        }
    }

    private void generateKobaltW(Path filePath) throws IOException {
        //
        // For kobaltw: try to generate it with the correct env shebang. If this fails,
        // we'll generate it without the env shebang
        //
        File envFile = new File("/bin/env");
        if (!envFile.exists()) {
            envFile = new File("/usr/bin/env");
        }

        String content = "";

        if (envFile.exists()) {
            content = "#!" + envFile.getAbsolutePath() + " bash\n";
        }
        log(2, "  Generating " + KOBALTW + (envFile.exists() ? " with shebang" : "") + ".");

        content += "java -jar $(dirname $0)/kobalt/wrapper/kobalt-wrapper.jar $*\n";

        Files.write(filePath, content.getBytes());

        if (!new File(KOBALTW).setExecutable(true)) {
            if (!isWindows()) {
                log(1, "Couldn't make " + KOBALTW + " executable");
            }
        }
    }

    /**
     * Extract the zip file in ~/.kobalt/wrapper/dist/$version
     */
    private void extractZipFile(Path localZipFile, String zipOutputDir) throws IOException {
        log(2, "Extracting " + localZipFile);
        try (ZipFile zipFile = new ZipFile(localZipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = Paths.get(zipOutputDir, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    log(2, "  Writing " + entry.getName() + " to " + entryPath);
                    try {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zipFile.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (FileSystemException ex) {
                        log(2, "Couldn't copy to " + entryPath);
                    }
                }
            }
        }
    }

    private void download(File file, String version) throws IOException {
        for (int attempt = 0; attempt < 3; ++attempt) {
            try {
                downloadImpl(file, version);
            } catch (IOException e) {
                error("Failed to download file " + file + " due to I/O issue: " + e.getMessage());
                Files.deleteIfExists(file.toPath());

                if (attempt == 2) {
                    throw e;
                }
            }

            if (file.exists()) {
                break;
            }
        }
    }

    private void downloadImpl(File file, String version) throws IOException {
        String fileUrl = getWrapperDownloadUrl(version);

        log(2, "Downloading " + fileUrl);

        boolean done = false;
        HttpURLConnection httpConn = null;
        try {
            int responseCode = 0;
            URL url = null;
            while (!done) {
                url = new URL(fileUrl);
                httpConn = (HttpURLConnection) url.openConnection();
                responseCode = httpConn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    fileUrl = httpConn.getHeaderField("Location");
                } else {
                    done = true;
                }
            }

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
                        fileName = disposition.substring(index + 9, disposition.length());
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
                try (InputStream inputStream = httpConn.getInputStream()) {
                    // opens an output stream to save into file
                    try (FileOutputStream outputStream = new FileOutputStream(file)) {
                        copyToStreamWithProgress(inputStream, outputStream, contentLength, url.toString());
                    }
                }

                log(1, "Downloaded " + fileUrl);
            } else {
                error("No file to download. Server replied HTTP code: " + responseCode);
            }
        } catch(IOException ex) {
            log(1, "Warning: couldn't download " + fileUrl);
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    private void copyToStreamWithProgress(InputStream inputStream, OutputStream outputStream, long contentLength,
            String url) throws IOException {
        int bytesRead;
        long bytesSoFar = 0;
        byte[] buffer = new byte[100_000];
        boolean hasTerminal = System.console() != null;
        if (! hasTerminal) {
            log2(1, "\rDownloading " + url);
        }
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            bytesSoFar += bytesRead;
            if (hasTerminal) {
                if (bytesRead > 0) {
                    if (contentLength > 0) {
                        float percent = bytesSoFar * 100 / contentLength;
                        log2(1, "\rDownloading " + url + " " + percent + "%");
                    } else {
                        log2(1, ".");
                    }
                }
            }
        }
        log2(1, "\n");
    }

    private void saveFile(File file, String text) throws IOException {
        Files.createDirectories(file.getAbsoluteFile().toPath().getParent());
        Files.deleteIfExists(file.toPath());
        log(2, "Wrote " + file);
        Files.write(Paths.get(file.toURI()), text.getBytes());
    }

    static void log2(int level, String s) {
        p(level, s, false);
    }

    static void log(int level, String s) {
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

    private int launchMain(Path kobaltJarFile, String[] argv) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Dfile.encoding=" + Charset.defaultCharset().name());
        args.add("-jar");
        args.add(kobaltJarFile.toFile().getAbsolutePath());
        Collections.addAll(args, argv);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
        log(2, "Launching " + args);
        Process process = pb.start();
        return process.waitFor();
    }

}