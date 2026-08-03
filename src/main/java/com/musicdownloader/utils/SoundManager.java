/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javazoom.jl.player.Player
 */
package com.musicdownloader.utils;

import com.musicdownloader.utils.Settings;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import javazoom.jl.player.Player;

public class SoundManager {
    private Settings settings;
    private static final String SOUNDS_FOLDER = "Sounds";
    private static final String SUCCESS_SOUND = "success.mp3";
    private static final String ERROR_SOUND = "error.mp3";
    private static final String DOWNLOADED_SOUND = "downloaded.mp3";

    public SoundManager(Settings settings) {
        this.settings = settings;
    }

    public void playSuccessSound() {
        if (!this.isSoundEnabled()) {
            return;
        }
        this.playAudioFile(DOWNLOADED_SOUND);
    }

    public void playErrorSound() {
        if (!this.isSoundEnabled()) {
            return;
        }
        this.playAudioFile(ERROR_SOUND);
    }

    public void playSuccessMelody() {
        if (!this.isSoundEnabled()) {
            return;
        }
        this.playAudioFile(DOWNLOADED_SOUND);
    }

    public void playErrorMelody() {
        if (!this.isSoundEnabled()) {
            return;
        }
        this.playAudioFile(ERROR_SOUND);
    }

    public void playUISuccessSound() {
        if (!this.isSoundEnabled()) {
            return;
        }
        this.playAudioFile(SUCCESS_SOUND);
    }

    private void playAudioFile(String fileName) {
        new Thread(() -> {
            FileInputStream fis = null;
            try {
                File soundFile = new File(SOUNDS_FOLDER, fileName);
                if (!soundFile.exists()) {
                    System.err.println("Sound file not found: " + soundFile.getAbsolutePath());
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }
                fis = new FileInputStream(soundFile);
                Player player = new Player((InputStream)fis);
                player.play();
            }
            catch (Exception e) {
                System.err.println("Error playing sound file: " + fileName + " - " + e.getMessage());
                Toolkit.getDefaultToolkit().beep();
            }
            finally {
                if (fis != null) {
                    try {
                        fis.close();
                    }
                    catch (Exception exception) {}
                }
            }
        }).start();
    }

    private boolean isSoundEnabled() {
        return this.settings != null && this.settings.isSoundEnabled();
    }
}
