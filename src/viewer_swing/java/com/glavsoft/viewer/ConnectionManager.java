// Copyright (C) 2010, 2011, 2012 GlavSoft LLC.
// All rights reserved.
//
//-------------------------------------------------------------------------
// This file is part of the TightVNC software.  Please visit our Web site:
//
//                       http://www.tightvnc.com/
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//-------------------------------------------------------------------------
//

package com.glavsoft.viewer;

import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.viewer.swing.ConnectionParams;
import com.glavsoft.viewer.swing.Utils;
import com.glavsoft.viewer.swing.gui.ConnectionDialog;
import com.glavsoft.viewer.swing.ssh.SshConnectionManager;

import javax.swing.*;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;


public class ConnectionManager {
	private final WindowListener appWindowListener;
	private volatile boolean forceConnectionDialog;
	private JFrame containerFrame;
	private final boolean isApplet;

	public ConnectionManager(WindowListener appWindowListener, boolean isApplet) {
		this.appWindowListener = appWindowListener;
		this.isApplet = isApplet;
	}

	protected void showReconnectDialog(String title, String message) {
		JOptionPane reconnectPane = new JOptionPane(message + "\nTry another connection?",
				JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
		final JDialog reconnectDialog = reconnectPane.createDialog(containerFrame, title);
		Utils.decorateDialog(reconnectDialog);
		reconnectDialog.setVisible(true);
		if (reconnectPane.getValue() == null ||
				(Integer)reconnectPane.getValue() == JOptionPane.NO_OPTION) {
			appWindowListener.windowClosing(null);
		} else {
			forceConnectionDialog = !isApplet;
		}
	}

	Socket connectToHost(final ConnectionParams connectionParams, ProtocolSettings settings) {
		Socket socket = null;
		ConnectionDialog connectionDialog = null;
		boolean wasError = false;
		int port = 0;
		boolean hasJsch;
		SshConnectionManager sshConnectionManager = null;
		try {
			Class.forName("com.jcraft.jsch.JSch");
			sshConnectionManager = new SshConnectionManager(containerFrame);
			hasJsch = true;
		} catch (ClassNotFoundException e) {
			hasJsch = false;
		}
		do {
			if (forceConnectionDialog || wasError ||
					connectionParams.isHostNameEmpty()) {
				forceConnectionDialog = false;
				if (null == connectionDialog) {
					connectionDialog = new ConnectionDialog(containerFrame,
							appWindowListener,
							connectionParams, settings, hasJsch);
				}
                connectionDialog.setVisible(true);
            }
			String host;
			if (hasJsch && connectionParams.useSsh()) {
				Viewer.logger.info("Creating SSH tunnel to " + connectionParams.sshHostName + ":" + connectionParams.getSshPortNumber());
				port = sshConnectionManager.connect(connectionParams);
				if (sshConnectionManager.isConnected() ) {
					host = "127.0.0.1";
					Viewer.logger.info("SSH tunnel established: " + host + ":" + port);
				} else {
					Viewer.logger.severe("Could not create SSH tunnel: " + sshConnectionManager.getErrorMessage());
					showConnectionErrorDialog("Could not create SSH tunnel: " + sshConnectionManager.getErrorMessage());
					wasError = true;
					continue;
				}
			} else {
				host = connectionParams.hostName;
				port = connectionParams.getPortNumber();
			}
			Viewer.logger.info("Connecting to host " + host + ":" + port + (connectionParams.useSsh()? " (tunneled)" : ""));
			try {
				socket = new Socket(host, port);
				wasError = false;
			} catch (UnknownHostException e) {
				Viewer.logger.severe("Unknown host: " + connectionParams.hostName);
				showConnectionErrorDialog("Unknown host: '" + connectionParams.hostName + "'");
				wasError = true;
			} catch (IOException e) {
				Viewer.logger.severe("Couldn't connect to: " +
						connectionParams.hostName + ":" + connectionParams.getPortNumber() +
						": " + e.getMessage());
				showConnectionErrorDialog("Couldn't connect to: '" + connectionParams.hostName +
                        ":" + connectionParams.getPortNumber() + "'\n" + e.getMessage());
				wasError = true;
			}
		} while (!isApplet && (connectionParams.isHostNameEmpty() || wasError));
		if (connectionDialog != null) {
			connectionDialog.dispose();
		}
		return socket;
	}

	public void showConnectionErrorDialog(final String message) {
		JOptionPane errorPane = new JOptionPane(message, JOptionPane.ERROR_MESSAGE);
		final JDialog errorDialog = errorPane.createDialog(containerFrame, "Connection error");
		Utils.decorateDialog(errorDialog);
        errorDialog.setVisible(true);
	}

	public void setContainerFrame(JFrame containerFrame) {
        if (this.containerFrame != null && this.containerFrame != containerFrame) {
            this.containerFrame.dispose();
        }
		this.containerFrame = containerFrame;
	}

}