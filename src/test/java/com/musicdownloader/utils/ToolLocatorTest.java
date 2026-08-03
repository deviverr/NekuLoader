package com.musicdownloader.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ToolLocatorTest {
    private ToolLocatorTest() {
    }

    public static void main(String[] args) throws Exception {
        List<String> ytDlpCommand = ToolLocator.ytDlpCommand();
        requireRuns(ytDlpCommand, "yt-dlp");
        require(!isAppleSystemPythonFallback(ytDlpCommand),
                "yt-dlp discovery does not fall back to Apple's Python when yt-dlp is installed");
        requireRuns(List.of(ToolLocator.ffmpeg()), "-version", "FFmpeg");

        String runtime = ToolLocator.jsRuntime();
        require(runtime != null, "JavaScript runtime is discoverable");
        System.out.println("Tool locator smoke test passed");
    }

    private static boolean isAppleSystemPythonFallback(List<String> command) {
        return command.size() >= 3
                && command.get(0).equals("/Library/Developer/CommandLineTools/usr/bin/python3")
                && command.get(1).equals("-m")
                && command.get(2).equals("yt_dlp");
    }

    private static void requireRuns(List<String> baseCommand, String description) throws Exception {
        requireRuns(baseCommand, "--version", description);
    }

    private static void requireRuns(List<String> baseCommand, String versionArgument, String description) throws Exception {
        ArrayList<String> command = new ArrayList<>(baseCommand);
        command.add(versionArgument);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        require(process.waitFor(10, TimeUnit.SECONDS), description + " version command completed");
        require(process.exitValue() == 0, description + " is runnable: " + String.join(" ", baseCommand));
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}
