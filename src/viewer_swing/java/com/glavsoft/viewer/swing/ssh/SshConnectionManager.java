package com.glavsoft.viewer.swing.ssh;

import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.swing.ConnectionParams;
import com.glavsoft.viewer.swing.Utils;
import com.jcraft.jsch.*;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class SshConnectionManager implements SshKnownHostsManager {

    public static final String SSH_NODE = "com/glavsoft/viewer/ssh";
    public static final String KNOWN_HOSTS = "known_hosts";
    private Session session;
	private boolean isConnected;
	private String errorMessage;
	private final JFrame containerFrame;
    private JSch jsch;

    public SshConnectionManager(JFrame containerFrame) {
		this.containerFrame = containerFrame;
	}

	public int connect(ConnectionParams connectionParams) {
        if (Strings.isTrimmedEmpty(connectionParams.sshUserName)) {
            connectionParams.sshUserName = getInteractivelySshUserName();
        }

        isConnected = false;
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
        jsch = new JSch();
        try {
            jsch.setKnownHosts(getKnownHostsStream());
        } catch (JSchException e) {
            Logger.getLogger(this.getClass().getName()).severe("Cannot set JSCH known hosts: " + e.getMessage());
        }
        int port = 0;
		try {
			session = jsch.getSession(
                    connectionParams.sshUserName, connectionParams.sshHostName, connectionParams.getSshPortNumber());
			UserInfo ui = new SshUserInfo(containerFrame);
			session.setUserInfo(ui);
			session.connect();
            sync();
			port = session.setPortForwardingL(0, connectionParams.hostName, connectionParams.getPortNumber());
			isConnected = true;
		} catch (JSchException e) {
			if (port != 0) {
				try {
					session.delPortForwardingL(port);
				} catch (JSchException e1) {
					// ignore
				}
			}
			session.disconnect();
			errorMessage = e.getMessage();
		}
		return port;
	}

    private String getInteractivelySshUserName() {
        JOptionPane pane = new JOptionPane("Please enter the user name for SSH connection:",
                JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        pane.setWantsInput(true);
        final JDialog dialog = pane.createDialog(containerFrame, "SSH User Name");
        Utils.decorateDialog(dialog);
        dialog.setVisible(true);
        String res = pane.getInputValue() != null ? (String) pane.getInputValue() : "";
        dialog.dispose();
        return res;
    }

    public boolean isConnected() {
		return isConnected;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

    private InputStream getKnownHostsStream() {
        Preferences sshNode = Preferences.userRoot().node(SSH_NODE);
        return new ByteArrayInputStream(sshNode.getByteArray(KNOWN_HOSTS, new byte[0]));
    }

    @Override
    public void sync() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HostKeyRepository repository = jsch.getHostKeyRepository();
        try {
            HostKey[] hostKey = repository.getHostKey();
            if (null == hostKey) return;
            for (HostKey hk : hostKey) {
                String host = hk.getHost();
                    String type = hk.getType();
                    if (type.equals("UNKNOWN")) {
                        write(out, host);
                        write(out, "\n");
                        continue;
                    }
                    write(out, host);
                    write(out, " ");
                    write(out, type);
                    write(out, " ");
                    write(out, hk.getKey());
                    write(out, "\n");
            }
        } catch (IOException e) {
            Logger.getLogger(this.getClass().getName()).severe("Cannot sync JSCH known hosts: " + e.getMessage());
        }
        Preferences sshNode = Preferences.userRoot().node(SSH_NODE);
        sshNode.putByteArray(KNOWN_HOSTS, out.toByteArray());
    }

    private void write(ByteArrayOutputStream out, String str) throws IOException {
        try {
            out.write(str.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            out.write(str.getBytes());
        }
    }

}