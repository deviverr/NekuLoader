/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public class FolderIconManager {
    public static void setFolderIcon(String folderPath, String imageUrl) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            System.err.println("Failed to set folder icon: folderPath is null or empty");
            return;
        }
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            System.err.println("Failed to set folder icon: imageUrl is null or empty");
            return;
        }
        try {
            BufferedImage albumCover = FolderIconManager.downloadImage(imageUrl);
            if (albumCover == null) {
                System.err.println("Failed to set folder icon: Could not download image");
                return;
            }
            File folderJpg = new File(folderPath, "folder.jpg");
            if (!ImageIO.write((RenderedImage)albumCover, "jpg", folderJpg)) {
                System.err.println("Failed to write folder.jpg");
                return;
            }
            Path path = folderJpg.toPath();
            try {
                Files.setAttribute(path, "dos:hidden", true, new LinkOption[0]);
            }
            catch (IOException | UnsupportedOperationException e) {
                System.err.println("Could not hide folder.jpg: " + e.getMessage());
            }
            FolderIconManager.createDesktopIni(folderPath);
        }
        catch (Exception e) {
            System.err.println("Failed to set folder icon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static BufferedImage downloadImage(String imageUrl) {
        try {
            URI uri = new URI(imageUrl);
            BufferedImage image = ImageIO.read(uri.toURL());
            if (image == null) {
                return null;
            }
            BufferedImage resized = new BufferedImage(256, 256, 1);
            Graphics2D g = resized.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, 256, 256, null);
                return resized;
            }
            finally {
                g.dispose();
            }
        }
        catch (Exception e) {
            System.err.println("Failed to download image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static void createDesktopIni(String folderPath) {
        try {
            File desktopIni = new File(folderPath, "desktop.ini");
            try (PrintWriter writer = new PrintWriter(new FileWriter(desktopIni));){
                writer.println("[.ShellClassInfo]");
                writer.println("IconResource=folder.jpg,0");
                writer.println("[ViewState]");
                writer.println("Mode=");
                writer.println("Vid=");
                writer.println("FolderType=Pictures");
            }
            Path path = desktopIni.toPath();
            try {
                Files.setAttribute(path, "dos:hidden", true, new LinkOption[0]);
                Files.setAttribute(path, "dos:system", true, new LinkOption[0]);
                Path folderPathObj = Paths.get(folderPath, new String[0]);
                Files.setAttribute(folderPathObj, "dos:system", true, new LinkOption[0]);
            }
            catch (IOException | UnsupportedOperationException e) {
                System.err.println("Could not set file attributes: " + e.getMessage());
            }
        }
        catch (Exception e) {
            System.err.println("Failed to create desktop.ini: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void setFolderIconFromFile(String folderPath, File imageFile) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            System.err.println("Failed to set folder icon: folderPath is null or empty");
            return;
        }
        if (imageFile == null || !imageFile.exists() || !imageFile.isFile()) {
            System.err.println("Failed to set folder icon: imageFile is null or does not exist");
            return;
        }
        Graphics2D g = null;
        try {
            BufferedImage albumCover = ImageIO.read(imageFile);
            if (albumCover == null) {
                System.err.println("Failed to read image file: " + imageFile.getAbsolutePath());
                return;
            }
            BufferedImage resized = new BufferedImage(256, 256, 1);
            g = resized.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(albumCover, 0, 0, 256, 256, null);
            }
            finally {
                g.dispose();
            }
            File folderJpg = new File(folderPath, "folder.jpg");
            if (!ImageIO.write((RenderedImage)resized, "jpg", folderJpg)) {
                System.err.println("Failed to write folder.jpg");
                return;
            }
            Path path = folderJpg.toPath();
            try {
                Files.setAttribute(path, "dos:hidden", true, new LinkOption[0]);
            }
            catch (IOException | UnsupportedOperationException e) {
                System.err.println("Could not hide folder.jpg: " + e.getMessage());
            }
            FolderIconManager.createDesktopIni(folderPath);
        }
        catch (Exception e) {
            System.err.println("Failed to set folder icon from file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
