package org.velja.app;

import javax.swing.*;
import java.awt.*;

public class SkillLabelHelper {

    // Adds the skill label to the provided panel, checking for value changes and adjusting colors
    public static void addSkillLabel(String name, double value, double change, JPanel skillPanel) {
        String valueName = formatSkill( value, name);
        JLabel label = new JLabel(valueName);

        if (change > 0) {
            label.setText(valueName + " [+" + change + "] " + value + " " + name);
            label.setForeground(Color.BLUE);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        } else if (change < 0) {
            label.setText(valueName + " [" + change + "] " + value + " " + name);
            label.setForeground(Color.RED);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        } else {
            label.setText(valueName + " [" + value + "] " + name);
            label.setForeground(Color.BLACK);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }

        skillPanel.add(label);
    }

    // Converts skill value to a human-readable string based on predefined skill scale
    public static String formatSkill(double value, String skillName) {
        String[] skillScale = {
                "tragic", "hopeless", "unsatisfactory", "poor", "weak", "average", "adequate",
                "good", "solid", "very good", "excellent", "formidable", "outstanding",
                "incredible", "brilliant", "magical", "unearthly", "divine", "superdivine"
        };
        return skillScale[(int) value];
    }

    public static void styleButton(JButton button) {
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBackground(new Color(50, 150, 250));
        button.setForeground(Color.BLUE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 120, 220), 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}