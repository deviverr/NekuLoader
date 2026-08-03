package com.musicdownloader.player;

import com.musicdownloader.utils.ToolLocator;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public final class AudioPlayer implements AutoCloseable {
    public enum State {
        IDLE,
        PLAYING,
        PAUSED
    }

    public interface Listener {
        default void onStateChanged(State state) {
        }

        default void onCompleted(File file) {
        }

        default void onError(File file, String message) {
        }
    }

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * 2;

    private final Object pauseLock = new Object();
    private volatile Listener listener = new Listener() {
    };
    private volatile Process process;
    private volatile SourceDataLine line;
    private volatile State state = State.IDLE;
    private volatile File currentFile;
    private volatile double startSeconds;
    private volatile long bytesPlayed;
    private volatile float volume = 0.8f;
    private volatile long generation;

    public void setListener(Listener listener) {
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    public synchronized void play(File file) {
        play(file, 0.0);
    }

    public synchronized void play(File file, double positionSeconds) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Audio file does not exist");
        }
        long token = ++this.generation;
        stopResources();
        synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
        }
        this.currentFile = file;
        this.startSeconds = Math.max(0.0, positionSeconds);
        this.bytesPlayed = 0L;
        setState(State.PLAYING);
        Thread playbackThread = new Thread(() -> decode(file, this.startSeconds, token), "nekuloader-audio");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public synchronized void togglePause() {
        if (this.state == State.PLAYING) {
            setState(State.PAUSED);
        } else if (this.state == State.PAUSED) {
            setState(State.PLAYING);
            synchronized (this.pauseLock) {
                this.pauseLock.notifyAll();
            }
        }
    }

    public synchronized void seek(double positionSeconds) {
        if (this.currentFile == null) {
            return;
        }
        boolean remainPaused = this.state == State.PAUSED;
        File file = this.currentFile;
        play(file, positionSeconds);
        if (remainPaused) {
            setState(State.PAUSED);
        }
    }

    public synchronized void stop() {
        ++this.generation;
        stopResources();
        this.currentFile = null;
        this.startSeconds = 0.0;
        this.bytesPlayed = 0L;
        setState(State.IDLE);
        synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
        }
    }

    public State getState() {
        return this.state;
    }

    public File getCurrentFile() {
        return this.currentFile;
    }

    public double getPositionSeconds() {
        return this.startSeconds + (double)this.bytesPlayed / BYTES_PER_SECOND;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    private void decode(File file, double positionSeconds, long token) {
        Process localProcess = null;
        SourceDataLine localLine = null;
        try {
            List<String> command = new ArrayList<>();
            command.add(ToolLocator.ffmpeg());
            command.add("-v");
            command.add("error");
            if (positionSeconds > 0.0) {
                command.add("-ss");
                command.add(String.format(java.util.Locale.ROOT, "%.3f", positionSeconds));
            }
            command.add("-i");
            command.add(file.getAbsolutePath());
            command.add("-vn");
            command.add("-f");
            command.add("s16le");
            command.add("-acodec");
            command.add("pcm_s16le");
            command.add("-ac");
            command.add(String.valueOf(CHANNELS));
            command.add("-ar");
            command.add(String.valueOf(SAMPLE_RATE));
            command.add("-");

            localProcess = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
            localLine = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            localLine.open(format);
            localLine.start();
            synchronized (this) {
                if (token != this.generation) {
                    localProcess.destroyForcibly();
                    localLine.close();
                    return;
                }
                this.process = localProcess;
                this.line = localLine;
            }

            try (InputStream audio = localProcess.getInputStream()) {
                byte[] buffer = new byte[16384];
                int count;
                while (token == this.generation && (count = audio.read(buffer)) >= 0) {
                    synchronized (this.pauseLock) {
                        while (token == this.generation && this.state == State.PAUSED) {
                            this.pauseLock.wait();
                        }
                    }
                    if (token != this.generation) {
                        break;
                    }
                    applyVolume(buffer, count, this.volume);
                    int written = localLine.write(buffer, 0, count);
                    this.bytesPlayed += written;
                }
            }
            if (token == this.generation) {
                localLine.drain();
                synchronized (this) {
                    if (token == this.generation) {
                        this.process = null;
                        this.line = null;
                        this.currentFile = null;
                        setState(State.IDLE);
                    }
                }
                this.listener.onCompleted(file);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            if (token == this.generation) {
                synchronized (this) {
                    this.process = null;
                    this.line = null;
                    this.currentFile = null;
                    setState(State.IDLE);
                }
                this.listener.onError(file, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
        finally {
            if (localLine != null) {
                localLine.close();
            }
            if (localProcess != null && localProcess.isAlive()) {
                localProcess.destroyForcibly();
            }
        }
    }

    private static void applyVolume(byte[] buffer, int length, float volume) {
        if (volume >= 0.999f) {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (buffer[i] & 0xff) | buffer[i + 1] << 8;
            int scaled = Math.round(sample * volume);
            buffer[i] = (byte)(scaled & 0xff);
            buffer[i + 1] = (byte)(scaled >> 8);
        }
    }

    private synchronized void stopResources() {
        Process runningProcess = this.process;
        SourceDataLine runningLine = this.line;
        this.process = null;
        this.line = null;
        if (runningProcess != null) {
            runningProcess.destroy();
        }
        if (runningLine != null) {
            runningLine.stop();
            runningLine.flush();
            runningLine.close();
        }
    }

    private void setState(State state) {
        this.state = state;
        this.listener.onStateChanged(state);
    }

    @Override
    public void close() {
        stop();
    }
}
