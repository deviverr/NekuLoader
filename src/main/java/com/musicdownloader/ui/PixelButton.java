/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.UIManager;

public class PixelButton
extends JButton {
    private static volatile boolean modernStyle = false;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public PixelButton(String text) {
        super(text);
        this.setFocusPainted(false);
        this.setBorderPainted(false);
        this.setContentAreaFilled(false);
        this.setFont(new Font("Monospaced", 1, 11));
        this.setForeground(Color.WHITE);
        this.setCursor(new Cursor(12));
        this.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseEntered(MouseEvent e) {
                if (PixelButton.this.isEnabled()) {
                    PixelButton.this.isHovered = true;
                    PixelButton.this.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                PixelButton.this.isHovered = false;
                PixelButton.this.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (PixelButton.this.isEnabled()) {
                    PixelButton.this.isPressed = true;
                    PixelButton.this.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                PixelButton.this.isPressed = false;
                PixelButton.this.repaint();
            }
        });
    }

    public static void setModernStyle(boolean modern) {
        modernStyle = modern;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;
        if (modernStyle) {
            this.paintModern(g2d);
            return;
        }
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Color bgColor = new Color(100, 50, 150);
        Color borderColor = new Color(180, 120, 255);
        if (!this.isEnabled()) {
            bgColor = new Color(60, 60, 60);
            borderColor = new Color(100, 100, 100);
            this.setForeground(new Color(150, 150, 150));
        } else if (this.isPressed) {
            bgColor = new Color(70, 35, 100);
            borderColor = new Color(150, 100, 200);
            this.setForeground(Color.WHITE);
        } else if (this.isHovered) {
            bgColor = new Color(130, 70, 180);
            borderColor = new Color(200, 150, 255);
            this.setForeground(Color.WHITE);
        } else {
            this.setForeground(Color.WHITE);
        }
        g2d.setColor(bgColor);
        g2d.fillRect(2, 2, this.getWidth() - 4, this.getHeight() - 4);
        g2d.setColor(borderColor);
        g2d.fillRect(0, 0, this.getWidth(), 2);
        g2d.fillRect(0, this.getHeight() - 2, this.getWidth(), 2);
        g2d.fillRect(0, 0, 2, this.getHeight());
        g2d.fillRect(this.getWidth() - 2, 0, 2, this.getHeight());
        FontMetrics fm = g2d.getFontMetrics();
        int textX = (this.getWidth() - fm.stringWidth(this.getText())) / 2;
        int textY = (this.getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        if (this.isPressed) {
            ++textY;
        }
        g2d.setColor(this.getForeground());
        g2d.drawString(this.getText(), textX, textY);
    }

    @Override
    public Dimension getPreferredSize() {
        return modernStyle ? new Dimension(128, 36) : new Dimension(120, 32);
    }

    private void paintModern(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color background = UIManager.getColor("Button.background");
        Color foreground = UIManager.getColor("Button.foreground");
        if (background == null) {
            background = new Color(48, 61, 72);
        }
        if (foreground == null) {
            foreground = Color.WHITE;
        }
        if (!this.isEnabled()) {
            background = new Color(120, 125, 130);
            foreground = new Color(210, 210, 210);
        } else if (this.isPressed) {
            background = background.darker();
        } else if (this.isHovered) {
            background = background.brighter();
        }
        int arc = 8;
        g2d.setColor(background);
        g2d.fillRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, arc, arc);
        g2d.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 90));
        g2d.drawRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, arc, arc);
        FontMetrics fm = g2d.getFontMetrics();
        int textX = (this.getWidth() - fm.stringWidth(this.getText())) / 2;
        int textY = (this.getHeight() + fm.getAscent() - fm.getDescent()) / 2 + (this.isPressed ? 1 : 0);
        g2d.setColor(foreground);
        g2d.drawString(this.getText(), textX, textY);
    }
}
