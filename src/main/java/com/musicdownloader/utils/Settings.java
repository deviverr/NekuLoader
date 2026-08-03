/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.utils;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Settings {
    private static final String SETTINGS_FILE = System.getProperty("user.home") + File.separator + ".pixel-music-downloader" + File.separator + "settings.properties";
    private Properties properties = new Properties();
    public static final String THEME_CLASSIC = "Classic";
    public static final String THEME_DARK = "Dark";
    public static final String THEME_LIGHT = "Light";
    public static final String THEME_SYSTEM = "System";
    public static final String THEME_CYBERPUNK = "Cyberpunk";
    public static final String THEME_NEON = "Neon";
    public static final String THEME_CUSTOM = "Custom";
    public static final String THEME_ABSOLUTE_BLACK = "Absolute Black and White";
    public static final String THEME_ABSOLUTE_WHITE = "Absolute White and Black";
    public static final String LANG_ENGLISH = "English";
    public static final String LANG_UKRAINIAN = "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430";
    public static final String LANG_SPANISH = "Espa\u00f1ol";
    public static final String LANG_FRENCH = "Fran\u00e7ais";
    public static final String LANG_GERMAN = "Deutsch";
    public static final String LANG_RUSSIAN = "\u0420\u0443\u0441\u0441\u043a\u0438\u0439";
    public static final String APPEARANCE_LEGACY = "Legacy";
    public static final String APPEARANCE_MODERN = "Modern";

    public Settings() {
        this.loadSettings();
    }

    private void loadSettings() {
        File settingsFile = new File(SETTINGS_FILE);
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile);){
                this.properties.load(fis);
            }
            catch (IOException e) {
                this.setDefaults();
            }
        } else {
            this.setDefaults();
        }
    }

    private void setDefaults() {
        this.properties.setProperty("theme", THEME_CLASSIC);
        this.properties.setProperty("language", LANG_ENGLISH);
        this.properties.setProperty("customFolderIcons", "true");
        this.properties.setProperty("animateBackground", "true");
        this.properties.setProperty("outputColor", "green");
        this.properties.setProperty("outputFolder", System.getProperty("user.home") + File.separator + "Music" + File.separator + "Downloads");
        this.saveSettings();
    }

    public void saveSettings() {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            settingsFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(settingsFile);){
                this.properties.store(fos, "NekuLoader Settings");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getTheme() {
        return this.properties.getProperty("theme", THEME_CLASSIC);
    }

    public String getAppearance() {
        String appearance = this.properties.getProperty("appearance", APPEARANCE_LEGACY);
        return APPEARANCE_MODERN.equals(appearance) ? APPEARANCE_MODERN : APPEARANCE_LEGACY;
    }

    public boolean isModernAppearance() {
        return APPEARANCE_MODERN.equals(this.getAppearance());
    }

    public void setAppearance(String appearance) {
        this.properties.setProperty("appearance", APPEARANCE_MODERN.equals(appearance) ? APPEARANCE_MODERN : APPEARANCE_LEGACY);
    }

    public void setTheme(String theme) {
        if (theme == null || theme.trim().isEmpty()) {
            theme = THEME_CLASSIC;
        }
        this.properties.setProperty("theme", theme);
    }

    public String getLanguage() {
        return this.properties.getProperty("language", LANG_ENGLISH);
    }

    public void setLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            language = LANG_ENGLISH;
        }
        this.properties.setProperty("language", language);
    }

    public boolean isCustomFolderIconsEnabled() {
        return Boolean.parseBoolean(this.properties.getProperty("customFolderIcons", "true"));
    }

    public void setCustomFolderIcons(boolean enabled) {
        this.properties.setProperty("customFolderIcons", String.valueOf(enabled));
    }

    public String getOutputFolder() {
        return this.properties.getProperty("outputFolder", System.getProperty("user.home") + File.separator + "Music" + File.separator + "Downloads");
    }

    public void setOutputFolder(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder cannot be null or empty");
        }
        File folderFile = new File(folder);
        String absolutePath = folderFile.getAbsolutePath();
        this.properties.setProperty("outputFolder", absolutePath);
    }

    public boolean isAnimateBackground() {
        return Boolean.parseBoolean(this.properties.getProperty("animateBackground", "true"));
    }

    public void setAnimateBackground(boolean enabled) {
        this.properties.setProperty("animateBackground", String.valueOf(enabled));
    }

    public String getOutputColor() {
        return this.properties.getProperty("outputColor", "green");
    }

    public void setOutputColor(String color) {
        if (color == null || color.trim().isEmpty()) {
            color = "green";
        }
        this.properties.setProperty("outputColor", color.toLowerCase());
    }

    public String getCustomBgStart() {
        return this.properties.getProperty("customBgStart", "30,20,40");
    }

    public void setCustomBgStart(String rgb) {
        this.properties.setProperty("customBgStart", rgb);
    }

    public String getCustomBgEnd() {
        return this.properties.getProperty("customBgEnd", "60,40,80");
    }

    public void setCustomBgEnd(String rgb) {
        this.properties.setProperty("customBgEnd", rgb);
    }

    public String getCustomTitleColor() {
        return this.properties.getProperty("customTitleColor", "255,200,255");
    }

    public void setCustomTitleColor(String rgb) {
        this.properties.setProperty("customTitleColor", rgb);
    }

    public String getCustomTextColor() {
        return this.properties.getProperty("customTextColor", "255,255,255");
    }

    public void setCustomTextColor(String rgb) {
        this.properties.setProperty("customTextColor", rgb);
    }

    public String getCustomStarColor() {
        return this.properties.getProperty("customStarColor", "255,255,200");
    }

    public void setCustomStarColor(String rgb) {
        this.properties.setProperty("customStarColor", rgb);
    }

    public String getCustomMenuBarColor() {
        return this.properties.getProperty("customMenuBarColor", "40,30,60");
    }

    public void setCustomMenuBarColor(String rgb) {
        this.properties.setProperty("customMenuBarColor", rgb);
    }

    public String getCustomButtonColor() {
        return this.properties.getProperty("customButtonColor", "100,60,140");
    }

    public void setCustomButtonColor(String rgb) {
        this.properties.setProperty("customButtonColor", rgb);
    }

    public int getFrameTransparency() {
        return this.getIntProperty("frameTransparency", 10, 0, 100);
    }

    public void setFrameTransparency(int transparency) {
        this.properties.setProperty("frameTransparency", String.valueOf(Math.max(0, Math.min(100, transparency))));
        this.saveSettings();
    }

    public int getSnowSpeed() {
        return this.getIntProperty("snowSpeed", 5, 1, 10);
    }

    public void setSnowSpeed(int speed) {
        this.properties.setProperty("snowSpeed", String.valueOf(Math.max(1, Math.min(10, speed))));
        this.saveSettings();
    }

    public int getSnowBoldness() {
        return this.getIntProperty("snowBoldness", 2, 1, 5);
    }

    public void setSnowBoldness(int boldness) {
        this.properties.setProperty("snowBoldness", String.valueOf(Math.max(1, Math.min(5, boldness))));
        this.saveSettings();
    }

    public int getSnowTransparency() {
        return this.getIntProperty("snowTransparency", 100, 0, 100);
    }

    public void setSnowTransparency(int transparency) {
        this.properties.setProperty("snowTransparency", String.valueOf(Math.max(0, Math.min(100, transparency))));
        this.saveSettings();
    }

    public String getFrameBackgroundColor() {
        return this.properties.getProperty("frameBackgroundColor", "40,30,50");
    }

    public void setFrameBackgroundColor(String rgb) {
        if (rgb == null || rgb.trim().isEmpty()) {
            rgb = "40,30,50";
        }
        this.properties.setProperty("frameBackgroundColor", rgb);
        this.saveSettings();
    }

    public boolean saveCustomTheme(String name) {
        try {
            this.properties.setProperty("savedTheme_" + name + "_bgStart", this.getCustomBgStart());
            this.properties.setProperty("savedTheme_" + name + "_bgEnd", this.getCustomBgEnd());
            this.properties.setProperty("savedTheme_" + name + "_titleColor", this.getCustomTitleColor());
            this.properties.setProperty("savedTheme_" + name + "_textColor", this.getCustomTextColor());
            this.properties.setProperty("savedTheme_" + name + "_starColor", this.getCustomStarColor());
            this.properties.setProperty("savedTheme_" + name + "_menuBarColor", this.getCustomMenuBarColor());
            this.properties.setProperty("savedTheme_" + name + "_buttonColor", this.getCustomButtonColor());
            this.saveSettings();
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean loadCustomTheme(String name) {
        String bgStart = this.properties.getProperty("savedTheme_" + name + "_bgStart");
        if (bgStart == null) {
            return false;
        }
        this.setCustomBgStart(bgStart);
        this.setCustomBgEnd(this.properties.getProperty("savedTheme_" + name + "_bgEnd", "60,40,80"));
        this.setCustomTitleColor(this.properties.getProperty("savedTheme_" + name + "_titleColor", "255,200,255"));
        this.setCustomTextColor(this.properties.getProperty("savedTheme_" + name + "_textColor", "255,255,255"));
        this.setCustomStarColor(this.properties.getProperty("savedTheme_" + name + "_starColor", "255,255,200"));
        this.setCustomMenuBarColor(this.properties.getProperty("savedTheme_" + name + "_menuBarColor", "40,30,60"));
        this.setCustomButtonColor(this.properties.getProperty("savedTheme_" + name + "_buttonColor", "100,60,140"));
        return true;
    }

    public List<String> getSavedThemeNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (String key : this.properties.stringPropertyNames()) {
            if (!key.startsWith("savedTheme_") || !key.endsWith("_bgStart")) continue;
            String name = key.substring(11, key.length() - 8);
            names.add(name);
        }
        return names;
    }

    public Color parseColor(String rgb) {
        try {
            String[] parts = rgb.split(",");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return new Color(40, 30, 60);
    }

    public String getBackgroundImagePath() {
        return this.properties.getProperty("backgroundImage", "");
    }

    public void setBackgroundImagePath(String path) {
        if (path == null) {
            path = "";
        }
        this.properties.setProperty("backgroundImage", path);
        if (!path.isEmpty()) {
            this.addRecentBackgroundImage(path);
        }
        this.saveSettings();
    }

    public void removeBackgroundImage() {
        this.properties.setProperty("backgroundImage", "");
        this.saveSettings();
    }

    public boolean hasBackgroundImage() {
        String path = this.getBackgroundImagePath();
        return path != null && !path.trim().isEmpty() && new File(path).exists();
    }

    public List<String> getRecentBackgroundImages() {
        ArrayList<String> recent = new ArrayList<String>();
        for (int i = 1; i <= 5; ++i) {
            String path = this.properties.getProperty("recentBg" + i, "");
            if (path.isEmpty() || !new File(path).exists()) continue;
            recent.add(path);
        }
        return recent;
    }

    private void addRecentBackgroundImage(String path) {
        int i;
        if (path == null || path.isEmpty()) {
            return;
        }
        List<String> recent = new ArrayList<String>();
        for (i = 1; i <= 5; ++i) {
            String recentPath = this.properties.getProperty("recentBg" + i, "");
            if (recentPath.isEmpty()) continue;
            recent.add(recentPath);
        }
        recent.remove(path);
        recent.add(0, path);
        if (recent.size() > 5) {
            recent = recent.subList(0, 5);
        }
        for (i = 0; i < recent.size(); ++i) {
            this.properties.setProperty("recentBg" + (i + 1), recent.get(i));
        }
        for (i = recent.size() + 1; i <= 5; ++i) {
            this.properties.remove("recentBg" + i);
        }
        this.saveSettings();
    }

    public String getProgressBarColor() {
        return this.properties.getProperty("progressBarColor", "100,200,100");
    }

    private int getIntProperty(String key, int defaultValue, int minimum, int maximum) {
        String value = this.properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(minimum, Math.min(maximum, parsed));
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setProgressBarColor(String rgb) {
        if (rgb == null || rgb.trim().isEmpty()) {
            rgb = "100,200,100";
        }
        this.properties.setProperty("progressBarColor", rgb);
        this.saveSettings();
    }

    public boolean isSoundEnabled() {
        return Boolean.parseBoolean(this.properties.getProperty("soundEnabled", "true"));
    }

    public void setSoundEnabled(boolean enabled) {
        this.properties.setProperty("soundEnabled", String.valueOf(enabled));
        this.saveSettings();
    }

    public boolean isCatModeEnabled() {
        return Boolean.parseBoolean(this.properties.getProperty("catMode", "false"));
    }

    public void setCatMode(boolean enabled) {
        this.properties.setProperty("catMode", String.valueOf(enabled));
        this.saveSettings();
    }

    public String getParseMode() {
        return this.properties.getProperty("parseMode", "dual");
    }

    public void setParseMode(String mode) {
        if (mode == null || !mode.equals("spotify") && !mode.equals("youtube-music") && !mode.equals("dual")) {
            mode = "dual";
        }
        this.properties.setProperty("parseMode", mode);
        this.saveSettings();
    }
}
