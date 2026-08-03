/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader;

import com.musicdownloader.ui.MainFrame;
import com.musicdownloader.utils.Settings;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class MusicDownloaderApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Settings settings = new Settings();
            try {
                if (settings.isModernAppearance()) {
                    if (Settings.THEME_LIGHT.equals(settings.getTheme()) || Settings.THEME_ABSOLUTE_WHITE.equals(settings.getTheme())) {
                        FlatLightLaf.setup();
                    } else {
                        FlatDarkLaf.setup();
                    }
                    UIManager.put("Component.arc", 7);
                    UIManager.put("Button.arc", 7);
                    UIManager.put("TextComponent.arc", 6);
                } else {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
