package com.musicdownloader.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Resolves bundled tools first, then falls back to tools installed by the user. */
public final class ToolLocator {
    private ToolLocator() {
    }

    public static String python() {
        String configured = configuredExecutable("NEKULOADER_PYTHON");
        if (configured != null) {
            return configured;
        }
        String bundled = findBundled("python", "python.exe");
        if (bundled != null) {
            return bundled;
        }
        String located = locateExecutable(isWindows() ? "python" : "python3");
        return located != null ? located : (isWindows() ? "python" : "python3");
    }

    public static String ytDlp() {
        String configured = configuredExecutable("NEKULOADER_YT_DLP");
        if (configured != null) {
            return configured;
        }
        String bundled = findBundled("yt-dlp", "yt-dlp.exe");
        if (bundled != null) {
            return bundled;
        }
        String located = locateExecutable(executableName("yt-dlp"));
        return located != null ? located : "yt-dlp";
    }

    public static List<String> ytDlpCommand() {
        String configured = configuredExecutable("NEKULOADER_YT_DLP");
        if (configured != null) {
            return List.of(configured);
        }
        String bundled = findBundled("yt-dlp", "yt-dlp.exe");
        if (bundled != null) {
            return List.of(bundled);
        }
        String executable = locateExecutable(executableName("yt-dlp"));
        if (executable != null) {
            return List.of(executable);
        }
        for (String candidate : pythonCandidates()) {
            if (canRun(candidate, "-m", "yt_dlp", "--version")) {
                return List.of(candidate, "-m", "yt_dlp");
            }
        }
        return List.of(python(), "-m", "yt_dlp");
    }

    public static String spotdl() {
        String configured = configuredExecutable("NEKULOADER_SPOTDL");
        if (configured != null) {
            return configured;
        }
        String located = locateExecutable(executableName("spotdl"));
        return located != null ? located : "spotdl";
    }

    public static String pip() {
        return python();
    }

    public static String ffmpegLocation() {
        String configured = configuredExecutable("NEKULOADER_FFMPEG");
        if (configured != null) {
            return Path.of(configured).getParent().toString();
        }
        for (Path base : applicationDirectories()) {
            Path bundledDirectory = base.resolve("ffmpeg").resolve("bin");
            if (Files.isRegularFile(bundledDirectory.resolve(executableName("ffmpeg")))) {
                return bundledDirectory.toAbsolutePath().toString();
            }
        }
        String ffmpeg = locateExecutable(executableName("ffmpeg"));
        if (ffmpeg != null && Path.of(ffmpeg).isAbsolute()) {
            return Path.of(ffmpeg).getParent().toString();
        }
        return null;
    }

    public static String ffmpeg() {
        return mediaTool("ffmpeg");
    }

    public static String ffprobe() {
        return mediaTool("ffprobe");
    }

    public static String adb() {
        String configured = configuredExecutable("NEKULOADER_ADB");
        if (configured != null) {
            return configured;
        }
        String executable = executableName("adb");
        for (Path base : applicationDirectories()) {
            Path[] candidates = new Path[]{
                base.resolve("platform-tools").resolve(executable),
                base.resolve("android").resolve("platform-tools").resolve(executable),
                base.resolve(executable)
            };
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate) && (isWindows() || Files.isExecutable(candidate))) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        String located = locateExecutable(executable);
        return located != null ? located : "adb";
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static String jsRuntime() {
        for (String runtime : List.of("deno", "node")) {
            String located = locateExecutable(executableName(runtime));
            if (located != null) {
                return Path.of(located).isAbsolute() ? runtime + ":" + located : runtime;
            }
        }
        return null;
    }

    private static String findBundled(String name, String windowsName) {
        String executable = isWindows() ? windowsName : name;
        List<Path> candidates = new ArrayList<>();
        for (Path base : applicationDirectories()) {
            if ("python".equals(name)) {
                candidates.add(base.resolve("python").resolve(executable));
            } else if ("yt-dlp".equals(name)) {
                candidates.add(base.resolve("python").resolve("Scripts").resolve(executable));
                candidates.add(base.resolve(executable));
            }
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && (isWindows() || Files.isExecutable(candidate))) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return null;
    }

    private static String executableName(String name) {
        return isWindows() ? name + ".exe" : name;
    }

    private static String mediaTool(String name) {
        String configured = configuredExecutable("NEKULOADER_" + name.toUpperCase());
        if (configured != null) {
            return configured;
        }
        String executable = executableName(name);
        for (Path base : applicationDirectories()) {
            Path candidate = base.resolve("ffmpeg").resolve("bin").resolve(executable);
            if (Files.isRegularFile(candidate) && (isWindows() || Files.isExecutable(candidate))) {
                return candidate.toAbsolutePath().toString();
            }
        }
        String located = locateExecutable(executable);
        return located != null ? located : name;
    }

    private static String configuredExecutable(String environmentVariable) {
        String configured = System.getenv(environmentVariable);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path = Path.of(configured);
        return isRunnableFile(path) ? path.toAbsolutePath().toString() : null;
    }

    private static String locateExecutable(String executable) {
        String versionArgument = executable.startsWith("ffmpeg") || executable.startsWith("ffprobe")
                ? "-version" : "--version";
        if (canRun(executable, versionArgument)) {
            return executable;
        }
        if (isWindows()) {
            return null;
        }
        for (Path directory : executableDirectories()) {
            Path candidate = directory.resolve(executable);
            if (isRunnableFile(candidate) && canRun(candidate.toString(), versionArgument)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return null;
    }

    private static List<String> pythonCandidates() {
        List<String> candidates = new ArrayList<>();
        String configured = configuredExecutable("NEKULOADER_PYTHON");
        if (configured != null) {
            candidates.add(configured);
        }
        String bundled = findBundled("python", "python.exe");
        if (bundled != null) {
            candidates.add(bundled);
        }
        candidates.add(isWindows() ? "python" : "python3");
        if (!isWindows()) {
            for (Path directory : executableDirectories()) {
                candidates.add(directory.resolve("python3").toString());
            }
        }
        return candidates;
    }

    private static List<Path> executableDirectories() {
        List<Path> directories = new ArrayList<>(List.of(
                Path.of("/opt/homebrew/bin"),
                Path.of("/usr/local/bin"),
                Path.of("/Library/Frameworks/Python.framework/Versions/Current/bin")));

        // python.org installations do not always create the Versions/Current symlink.
        // Finder-launched apps also receive a restricted PATH, so discover each installed
        // framework version explicitly instead of falling back to Apple's /usr/bin/python3.
        Path frameworkVersions = Path.of("/Library/Frameworks/Python.framework/Versions");
        if (Files.isDirectory(frameworkVersions)) {
            try (Stream<Path> versions = Files.list(frameworkVersions)) {
                versions.filter(Files::isDirectory)
                        .map(path -> path.resolve("bin"))
                        .filter(Files::isDirectory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(directories::add);
            }
            catch (Exception ignored) {
                // Keep the fixed common locations above when the directory cannot be read.
            }
        }
        return directories;
    }

    private static boolean isRunnableFile(Path path) {
        return Files.isRegularFile(path) && (isWindows() || Files.isExecutable(path));
    }

    private static boolean canRun(String executable, String... arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private static List<Path> applicationDirectories() {
        List<Path> directories = new ArrayList<>();
        try {
            Path codeSource = Path.of(ToolLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codeSource)) {
                codeSource = codeSource.getParent();
            }
            directories.add(codeSource.toAbsolutePath());
            if (codeSource.getParent() != null) {
                directories.add(codeSource.getParent().toAbsolutePath());
            }
        }
        catch (URISyntaxException | SecurityException ignored) {
            // Fall back to the current working directory below.
        }
        directories.add(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath());
        return directories;
    }
}
