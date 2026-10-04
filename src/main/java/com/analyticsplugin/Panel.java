package com.analyticsplugin;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import org.jspecify.annotations.NullMarked;

@NullMarked
class Panel extends PluginPanel {
    Panel() {
        super(false);

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout(0, 8));

        add(buildHeader(), BorderLayout.NORTH);
    }

    private JPanel buildHeader() {
        final JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        final JLabel title = new JLabel("Analytics");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(8));

        return header;
    }
}
