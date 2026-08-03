/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.utils;

import com.musicdownloader.utils.Settings;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Image;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class ThemeManager {
    private String currentTheme;
    private String outputColorName;
    private Settings settings;
    private Image backgroundImage;
    private ImageIcon backgroundGif;
    private Color bgStart;
    private Color bgEnd;
    private Color titleColor;
    private Color textColor;
    private Color logBgColor;
    private Color logTextColor;
    private Color buttonBgColor;
    private Color buttonTextColor;
    private Color starColor;
    private Color menuBarColor;

    public ThemeManager(String theme) {
        this.currentTheme = theme;
        this.outputColorName = "green";
        this.settings = null;
        this.loadTheme(theme);
    }

    public ThemeManager(String theme, String outputColor) {
        this.currentTheme = theme;
        this.outputColorName = outputColor;
        this.settings = null;
        this.loadTheme(theme);
    }

    public ThemeManager(String theme, String outputColor, Settings settings) {
        this.currentTheme = theme;
        this.outputColorName = outputColor;
        this.settings = settings;
        this.loadTheme(theme);
    }

    public void setOutputColor(String colorName) {
        this.outputColorName = colorName;
    }

    public Color getCustomLogTextColor() {
        switch (this.outputColorName.toLowerCase()) {
            case "green": {
                return new Color(150, 255, 150);
            }
            case "blue": {
                return new Color(150, 200, 255);
            }
            case "cyan": {
                return new Color(0, 255, 255);
            }
            case "yellow": {
                return new Color(255, 255, 100);
            }
            case "orange": {
                return new Color(255, 180, 100);
            }
            case "pink": {
                return new Color(255, 150, 255);
            }
            case "red": {
                return new Color(255, 100, 100);
            }
            case "white": {
                return new Color(255, 255, 255);
            }
        }
        return new Color(150, 255, 150);
    }

    private void loadTheme(String theme) {
        switch (theme) {
            case "Dark": {
                this.bgStart = new Color(20, 20, 25);
                this.bgEnd = new Color(40, 40, 50);
                this.titleColor = new Color(180, 180, 255);
                this.textColor = new Color(220, 220, 220);
                this.logBgColor = new Color(15, 15, 20);
                this.logTextColor = new Color(100, 255, 100);
                this.buttonBgColor = new Color(60, 60, 80);
                this.buttonTextColor = new Color(255, 255, 255);
                this.starColor = new Color(150, 150, 200, 100);
                this.menuBarColor = new Color(30, 30, 35);
                break;
            }
            case "Light": {
                this.bgStart = new Color(240, 240, 245);
                this.bgEnd = new Color(220, 220, 230);
                this.titleColor = new Color(100, 50, 150);
                this.textColor = new Color(40, 40, 40);
                this.logBgColor = new Color(255, 255, 255);
                this.logTextColor = new Color(0, 120, 0);
                this.buttonBgColor = new Color(200, 200, 220);
                this.buttonTextColor = new Color(40, 40, 40);
                this.starColor = new Color(255, 200, 100, 80);
                this.menuBarColor = new Color(230, 230, 240);
                break;
            }
            case "Cyberpunk": {
                this.bgStart = new Color(10, 0, 30);
                this.bgEnd = new Color(50, 0, 80);
                this.titleColor = new Color(0, 255, 255);
                this.textColor = new Color(255, 0, 255);
                this.logBgColor = new Color(5, 0, 20);
                this.logTextColor = new Color(0, 255, 200);
                this.buttonBgColor = new Color(80, 0, 120);
                this.buttonTextColor = new Color(0, 255, 255);
                this.starColor = new Color(0, 255, 255, 120);
                this.menuBarColor = new Color(20, 0, 50);
                break;
            }
            case "Neon": {
                this.bgStart = new Color(0, 0, 0);
                this.bgEnd = new Color(30, 0, 50);
                this.titleColor = new Color(255, 0, 255);
                this.textColor = new Color(0, 255, 255);
                this.logBgColor = new Color(10, 0, 20);
                this.logTextColor = new Color(0, 255, 0);
                this.buttonBgColor = new Color(100, 0, 150);
                this.buttonTextColor = new Color(255, 255, 0);
                this.starColor = new Color(255, 0, 255, 150);
                this.menuBarColor = new Color(20, 0, 30);
                break;
            }
            case "System": {
                this.bgStart = new Color(50, 50, 60);
                this.bgEnd = new Color(80, 80, 90);
                this.titleColor = new Color(200, 200, 255);
                this.textColor = new Color(220, 220, 220);
                this.logBgColor = new Color(30, 30, 40);
                this.logTextColor = new Color(150, 255, 150);
                this.buttonBgColor = new Color(100, 100, 120);
                this.buttonTextColor = new Color(255, 255, 255);
                this.starColor = new Color(200, 200, 255, 100);
                this.menuBarColor = new Color(60, 60, 70);
                break;
            }
            case "Absolute Black and White": {
                this.bgStart = new Color(0, 0, 0);
                this.bgEnd = new Color(0, 0, 0);
                this.titleColor = new Color(255, 255, 255);
                this.textColor = new Color(255, 255, 255);
                this.logBgColor = new Color(0, 0, 0);
                this.logTextColor = new Color(255, 255, 255);
                this.buttonBgColor = new Color(40, 40, 40);
                this.buttonTextColor = new Color(255, 255, 255);
                this.starColor = new Color(255, 255, 255, 180);
                this.menuBarColor = new Color(0, 0, 0);
                break;
            }
            case "Absolute White and Black": {
                this.bgStart = new Color(255, 255, 255);
                this.bgEnd = new Color(255, 255, 255);
                this.titleColor = new Color(0, 0, 0);
                this.textColor = new Color(0, 0, 0);
                this.logBgColor = new Color(255, 255, 255);
                this.logTextColor = new Color(0, 0, 0);
                this.buttonBgColor = new Color(220, 220, 220);
                this.buttonTextColor = new Color(0, 0, 0);
                this.starColor = new Color(0, 0, 0, 180);
                this.menuBarColor = new Color(255, 255, 255);
                break;
            }
            case "Custom": {
                if (this.settings != null) {
                    this.bgStart = this.parseColor(this.settings.getCustomBgStart());
                    this.bgEnd = this.parseColor(this.settings.getCustomBgEnd());
                    this.titleColor = this.parseColor(this.settings.getCustomTitleColor());
                    this.textColor = this.parseColor(this.settings.getCustomTextColor());
                    this.starColor = this.parseColor(this.settings.getCustomStarColor());
                    this.menuBarColor = this.parseColor(this.settings.getCustomMenuBarColor());
                    this.buttonBgColor = this.parseColor(this.settings.getCustomButtonColor());
                    if (this.bgStart == null) {
                        this.bgStart = new Color(30, 20, 40);
                    }
                    if (this.bgEnd == null) {
                        this.bgEnd = new Color(60, 40, 80);
                    }
                    if (this.titleColor == null) {
                        this.titleColor = new Color(255, 200, 255);
                    }
                    if (this.textColor == null) {
                        this.textColor = new Color(255, 255, 255);
                    }
                    if (this.starColor == null) {
                        this.starColor = new Color(255, 255, 200, 150);
                    }
                    if (this.menuBarColor == null) {
                        this.menuBarColor = new Color(40, 30, 60);
                    }
                    if (this.buttonBgColor == null) {
                        this.buttonBgColor = new Color(80, 50, 100);
                    }
                    this.logBgColor = this.darken(this.bgStart, 0.3);
                    this.logTextColor = new Color(150, 255, 150);
                    this.buttonTextColor = this.textColor;
                    break;
                }
                this.loadClassicTheme();
                break;
            }
            default: {
                this.loadClassicTheme();
            }
        }
    }

    private void loadClassicTheme() {
        this.bgStart = new Color(30, 20, 40);
        this.bgEnd = new Color(60, 40, 80);
        this.titleColor = new Color(255, 200, 255);
        this.textColor = new Color(255, 255, 255);
        this.logBgColor = new Color(20, 15, 30);
        this.logTextColor = new Color(150, 255, 150);
        this.buttonBgColor = new Color(80, 50, 100);
        this.buttonTextColor = new Color(255, 255, 255);
        this.starColor = new Color(255, 255, 200, 150);
        this.menuBarColor = new Color(40, 30, 60);
    }

    private Color parseColor(String rgb) {
        if (rgb == null || rgb.trim().isEmpty()) {
            return null;
        }
        try {
            String[] parts = rgb.split(",");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255) {
                    return new Color(r, g, b);
                }
            }
        }
        catch (NumberFormatException e) {
            System.err.println("Invalid color format: " + rgb);
        }
        return null;
    }

    private Color darken(Color color, double factor) {
        if (color == null) {
            return new Color(0, 0, 0);
        }
        factor = Math.max(0.0, Math.min(1.0, factor));
        int r = (int)((double)color.getRed() * factor);
        int g = (int)((double)color.getGreen() * factor);
        int b = (int)((double)color.getBlue() * factor);
        return new Color(Math.max(0, r), Math.max(0, g), Math.max(0, b));
    }

    public GradientPaint getBackgroundGradient(int width, int height) {
        return new GradientPaint(0.0f, 0.0f, this.bgStart, 0.0f, height, this.bgEnd);
    }

    public Color getBgStart() {
        return this.bgStart;
    }

    public Color getBgEnd() {
        return this.bgEnd;
    }

    public Color getTitleColor() {
        return this.titleColor;
    }

    public Color getTextColor() {
        return this.textColor;
    }

    public Color getLogBgColor() {
        return this.logBgColor;
    }

    public Color getLogTextColor() {
        return this.logTextColor;
    }

    public Color getButtonBgColor() {
        return this.buttonBgColor;
    }

    public Color getButtonTextColor() {
        return this.buttonTextColor;
    }

    public Color getStarColor() {
        return this.starColor;
    }

    public Color getMenuBarColor() {
        return this.menuBarColor;
    }

    public String getCurrentTheme() {
        return this.currentTheme;
    }

    public Color getTopPanelTextColor() {
        if (this.currentTheme.equals("Light") || this.currentTheme.equals("Absolute White and Black")) {
            return new Color(40, 40, 40);
        }
        return new Color(255, 255, 255);
    }

    public Color getTopPanelOverlay() {
        if (this.currentTheme.equals("Light")) {
            return new Color(255, 255, 255, 200);
        }
        if (this.currentTheme.equals("Absolute White and Black")) {
            return new Color(255, 255, 255, 230);
        }
        if (this.currentTheme.equals("Absolute Black and White")) {
            return new Color(0, 0, 0, 250);
        }
        if (this.currentTheme.equals("Dark")) {
            return new Color(20, 20, 30, 220);
        }
        if (this.currentTheme.equals("Cyberpunk")) {
            return new Color(20, 0, 40, 230);
        }
        if (this.currentTheme.equals("Neon")) {
            return new Color(10, 0, 20, 240);
        }
        if (this.currentTheme.equals("System")) {
            return new Color(50, 50, 70, 210);
        }
        return new Color(40, 30, 60, 220);
    }

    public void loadBackgroundImage() {
        this.backgroundImage = null;
        this.backgroundGif = null;
        if (this.settings != null && this.settings.hasBackgroundImage()) {
            try {
                String path = this.settings.getBackgroundImagePath();
                File file = new File(path);
                if (path.toLowerCase().endsWith(".gif")) {
                    this.backgroundGif = new ImageIcon(path);
                } else {
                    this.backgroundImage = ImageIO.read(file);
                }
            }
            catch (Exception e) {
                System.err.println("Failed to load background image: " + e.getMessage());
                this.backgroundImage = null;
                this.backgroundGif = null;
            }
        }
    }

    public boolean hasBackgroundImage() {
        return this.backgroundImage != null || this.backgroundGif != null;
    }

    public Image getBackgroundImage() {
        return this.backgroundImage;
    }

    public ImageIcon getBackgroundGif() {
        return this.backgroundGif;
    }

    public Color getProgressBarColor() {
        if (this.settings != null) {
            return this.settings.parseColor(this.settings.getProgressBarColor());
        }
        return new Color(100, 200, 100);
    }
}
