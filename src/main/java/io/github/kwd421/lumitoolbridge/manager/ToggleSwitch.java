package io.github.kwd421.lumitoolbridge.manager;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class ToggleSwitch extends JToggleButton {
    private static final long serialVersionUID = 1L;

    ToggleSwitch() {
        setPreferredSize(new Dimension(46, 24));
        setMinimumSize(new Dimension(46, 24));
        setMaximumSize(new Dimension(46, 24));
        setOpaque(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setToolTipText("켜기 / 끄기");
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int inset = 2;
            Color on = new Color(56, 132, 255);
            Color off = new Color(155, 160, 168);
            g.setColor(isSelected() ? on : off);
            g.fillRoundRect(inset, inset, w - inset * 2, h - inset * 2, h, h);
            int knob = h - 8;
            int x = isSelected() ? w - knob - 4 : 4;
            g.setColor(Color.WHITE);
            g.fillOval(x, 4, knob, knob);
        } finally {
            g.dispose();
        }
    }
}
