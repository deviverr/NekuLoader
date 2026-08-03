/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import javax.swing.Timer;

public class AnimatedCat
extends JPanel
implements ActionListener {
    private Timer animationTimer;
    private int frame = 0;
    private boolean isWalking = false;
    private int catX = 50;
    private int catY = 20;

    public AnimatedCat() {
        this.setOpaque(false);
        this.setPreferredSize(new Dimension(800, 80));
        this.animationTimer = new Timer(500, this);
        this.animationTimer.start();
    }

    public void setWalking(boolean walking) {
        this.isWalking = walking;
        if (!walking) {
            this.catX = 50;
        }
    }

    public void cleanup() {
        if (this.animationTimer != null && this.animationTimer.isRunning()) {
            this.animationTimer.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (this.isWalking) {
            this.drawWalkingCat(g2d, this.catX, this.catY, this.frame % 2);
            this.catX += 2;
            if (this.catX > this.getWidth()) {
                this.catX = -40;
            }
        } else {
            this.drawIdleCat(g2d, 50, this.catY, this.frame % 2);
        }
    }

    private void drawIdleCat(Graphics2D g, int x, int y, int animFrame) {
        Color catColor = new Color(255, 150, 100);
        Color darkColor = new Color(200, 100, 50);
        Color eyeColor = new Color(100, 200, 255);
        g.setColor(catColor);
        g.fillRect(x + 4, y + 8, 24, 16);
        g.fillRect(x, y + 16, 8, 12);
        g.fillRect(x + 24, y + 16, 8, 12);
        g.fillRect(x + 8, y, 16, 12);
        g.fillRect(x + 6, y - 4, 6, 6);
        g.fillRect(x + 20, y - 4, 6, 6);
        if (animFrame == 0) {
            g.setColor(eyeColor);
            g.fillRect(x + 10, y + 4, 3, 3);
            g.fillRect(x + 19, y + 4, 3, 3);
        } else {
            g.setColor(darkColor);
            g.fillRect(x + 10, y + 5, 3, 1);
            g.fillRect(x + 19, y + 5, 3, 1);
        }
        g.setColor(darkColor);
        g.drawRect(x + 4, y + 8, 24, 16);
        g.drawRect(x + 8, y, 16, 12);
        g.fillRect(x + 28, y + 8, 4, 8);
        g.fillRect(x + 32, y + 4, 4, 8);
    }

    private void drawWalkingCat(Graphics2D g, int x, int y, int animFrame) {
        Color catColor = new Color(255, 150, 100);
        Color darkColor = new Color(200, 100, 50);
        Color eyeColor = new Color(100, 200, 255);
        g.setColor(catColor);
        g.fillRect(x + 4, y + 8, 24, 16);
        if (animFrame == 0) {
            g.fillRect(x, y + 16, 8, 12);
            g.fillRect(x + 24, y + 18, 8, 10);
        } else {
            g.fillRect(x, y + 18, 8, 10);
            g.fillRect(x + 24, y + 16, 8, 12);
        }
        g.fillRect(x + 8, y, 16, 12);
        g.fillRect(x + 6, y - 4, 6, 6);
        g.fillRect(x + 20, y - 4, 6, 6);
        g.setColor(eyeColor);
        g.fillRect(x + 10, y + 4, 3, 3);
        g.fillRect(x + 19, y + 4, 3, 3);
        g.setColor(darkColor);
        g.drawRect(x + 4, y + 8, 24, 16);
        if (animFrame == 0) {
            g.fillRect(x + 28, y + 8, 4, 8);
            g.fillRect(x + 32, y + 4, 4, 8);
        } else {
            g.fillRect(x + 28, y + 10, 4, 8);
            g.fillRect(x + 32, y + 8, 4, 6);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ++this.frame;
        this.repaint();
    }
}
