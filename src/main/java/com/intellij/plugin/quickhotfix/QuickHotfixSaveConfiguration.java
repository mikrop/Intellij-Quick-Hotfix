package com.intellij.plugin.quickhotfix;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Michal Hájek, <a href="mailto:michalhajek@centrum.cz">michalhajek@centrum.cz</a>
 * @since 8.2.12
 */
public class QuickHotfixSaveConfiguration implements ActionListener {

    private TextFieldWithBrowseButton fileName;
    private JPanel contentPane;

    private void createUIComponents() {
        fileName = new TextFieldWithBrowseButton();
        fileName.addActionListener(this);
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    public void setFileName(final File file) {
        fileName.setText(file.getAbsolutePath());
    }

    public String getFileName() {
        return fileName.getText();
    }

    public void actionPerformed(final ActionEvent actionevent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName.getText()));
        if (chooser.showSaveDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
            fileName.setText(chooser.getSelectedFile().getPath());
        }
    }

}
