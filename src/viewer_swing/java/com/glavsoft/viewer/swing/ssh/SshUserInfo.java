package com.glavsoft.viewer.swing.ssh;

import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.swing.Utils;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
* @author dime at tightvnc.com
*/
class SshUserInfo implements UserInfo, UIKeyboardInteractive {
	private String password;
    private String passphrase;
	private final JFrame containerFrame;

    SshUserInfo(JFrame containerFrame) {
		this.containerFrame = containerFrame;
    }

	@Override
	public String getPassphrase() {
		return passphrase;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public boolean promptPassword(String message) {
		final JTextField passwordField = new JPasswordField(20);
		Object[] ob = {message, passwordField};
		JOptionPane pane =
				new JOptionPane(ob, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		final JDialog dialog = pane.createDialog(containerFrame, "SSH Authentication");
		Utils.decorateDialog(dialog);
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                passwordField.requestFocusInWindow();
            }
        });
		dialog.setVisible(true);
		int result = pane.getValue() != null ? (Integer)pane.getValue() : JOptionPane.CLOSED_OPTION;
        if (JOptionPane.OK_OPTION == result) {
            password = passwordField.getText();
        }
        dialog.dispose();
        return JOptionPane.OK_OPTION == result;
	}

	@Override
	public boolean promptPassphrase(String message) {
        JTextField passphraseField = new JPasswordField(20);
		Object[] ob = {message, passphraseField};
		JOptionPane pane =
				new JOptionPane(ob, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		final JDialog dialog = pane.createDialog(containerFrame, "SSH Authentication");
		Utils.decorateDialog(dialog);
		dialog.setVisible(true);
		int result = pane.getValue() != null ? (Integer)pane.getValue() : JOptionPane.CLOSED_OPTION;
        if (JOptionPane.OK_OPTION == result) {
            passphrase = passphraseField.getText();
        }
        dialog.dispose();
        return JOptionPane.OK_OPTION == result;
	}

	@Override
	public boolean promptYesNo(String message) {
		JOptionPane pane =
				new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
		final JDialog dialog = pane.createDialog(containerFrame, "SSH: Warning");
		Utils.decorateDialog(dialog);
		dialog.setVisible(true);
		int result = pane.getValue() != null ? (Integer)pane.getValue() : JOptionPane.CLOSED_OPTION;
        dialog.dispose();
        return JOptionPane.YES_OPTION == result;
	}

	@Override
	public void showMessage(String message) {
		JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION);
		final JDialog dialog = pane.createDialog(containerFrame, "SSH");
		Utils.decorateDialog(dialog);
		dialog.setVisible(true);
		dialog.dispose();
	}

	@Override
	public String[] promptKeyboardInteractive(String destination,
			String name,
			String instruction,
			String[] prompt,
			boolean[] echo) {
		Container panel = new JPanel();
		panel.setLayout(new GridBagLayout());

		final GridBagConstraints gbc =
				new GridBagConstraints(0, 0, 1, 1, 1, 1,
						GridBagConstraints.NORTHWEST,
						GridBagConstraints.NONE,
						new Insets(0, 0, 0, 0), 0, 0);
		gbc.weightx = 1.0;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.gridx = 0;
		panel.add(new JLabel(instruction), gbc);
		gbc.gridy++;
		gbc.gridwidth = GridBagConstraints.RELATIVE;
		JTextField[] texts = new JTextField[prompt.length];
		for (int i = 0; i < prompt.length; i++) {
			gbc.fill = GridBagConstraints.NONE;
			gbc.gridx = 0;
			gbc.weightx = 1;
			panel.add(new JLabel(prompt[i]), gbc);
			gbc.gridx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weighty = 1;
			if (echo[i]) {
				texts[i] = new JTextField(20);
			} else {
				texts[i] = new JPasswordField(20);
			}
			panel.add(texts[i], gbc);
			gbc.gridy++;
		}

		final String title = "SSH authentication for " + destination + (Strings.isTrimmedEmpty(name) ? "" : (": " + name));
		final JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		final JDialog dialog = pane.createDialog(containerFrame, title);
		Utils.decorateDialog(dialog);
		dialog.setVisible(true);
		int result = pane.getValue() != null ? (Integer)pane.getValue() : JOptionPane.CLOSED_OPTION;
        String[] response = null;
        if (JOptionPane.OK_OPTION == result) {
            response = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) {
				response[i] = texts[i].getText();
			}
		}
        dialog.dispose();
		return response;
	}
}
