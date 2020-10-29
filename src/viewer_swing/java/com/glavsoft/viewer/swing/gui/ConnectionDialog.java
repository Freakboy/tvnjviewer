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

package com.glavsoft.viewer.swing.gui;

import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.swing.ConnectionParams;
import com.glavsoft.viewer.swing.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Dialog window for connection parameters get from.
 */
@SuppressWarnings("serial")
public class ConnectionDialog extends JDialog {
	private static final int PADDING = 4;
    public static final int COLUMNS_HOST_FIELD = 30;
    public static final int COLUMNS_PORT_USER_FIELD = 13;
    private ConnectionParams connectionParams;
	private final boolean hasJsch;
	private final JTextField serverPortField;
	private JCheckBox useSshTunnelingCheckbox;
	private final JComboBox serverNameCombo;
    private JTextField sshUserField;
    private JTextField sshHostField;
    private JTextField sshPortField;
    private JLabel sshUserLabel;
    private JLabel sshHostLabel;
    private JLabel sshPortLabel;
    private final ConnectionsHistory connectionsHistory;
    private JLabel ssUserWarningLabel;


    public ConnectionDialog(final JFrame owner, final WindowListener appWindowListener,
                            final ConnectionParams connectionParams, final ProtocolSettings settings,
                            final boolean hasJsch) {
		super(owner, "New TightVNC Connection");
		this.connectionParams = connectionParams;
		this.hasJsch = hasJsch;

		JPanel pane = new JPanel(new GridBagLayout());
		add(pane);
		pane.setBorder(new EmptyBorder(PADDING, PADDING, PADDING, PADDING));

		setLayout(new GridBagLayout());

		int gridRow = 0;

        serverNameCombo = new JComboBox();
        connectionsHistory = new ConnectionsHistory(connectionParams);
        initConnectionsHistoryCombo();
        settings.copySerializedFieldsFrom(connectionsHistory.getSettings(connectionParams));
        settings.addListener(connectionsHistory);
        serverNameCombo.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                Object item = serverNameCombo.getSelectedItem();
                if (item instanceof ConnectionParams) {
                    final ConnectionParams cp = (ConnectionParams) item;
                    completeInputFieldsFrom(cp);
                    ProtocolSettings settingsNew = connectionsHistory.getSettings(cp);
                    settings.copySerializedFieldsFrom(settingsNew);
                }
            }
        });

        addFormFieldRow(pane, gridRow, new JLabel("Remote Host:"), serverNameCombo, true);
        ++gridRow;

        serverPortField = new JTextField(COLUMNS_PORT_USER_FIELD);
        addFormFieldRow(pane, gridRow, new JLabel("Port:"), serverPortField, false);
        ++gridRow;


        if (hasJsch) {
			gridRow = createSshOptions(connectionParams, pane, gridRow);
		}

		completeInputFieldsFrom(connectionParams);

		JPanel buttonPanel = new JPanel();
		JButton connectButton = new JButton("Connect");
		buttonPanel.add(connectButton);
		connectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
                Object item = serverNameCombo.getSelectedItem();
                String hostName = item instanceof ConnectionParams ?
                        ((ConnectionParams) item).hostName :
                        (String) item;
                setServerNameString(hostName);
				setPort(serverPortField.getText());
				setSshOptions();
				connectionsHistory.reorderConnectionsList(connectionParams, settings);
				connectionsHistory.save();

				serverNameCombo.removeAllItems();
				completeCombo();
				if (validateFields()) {
					setVisible(false);
				} else {
					serverNameCombo.requestFocusInWindow();
				}
			}
		});

		JButton optionsButton = new JButton("Options...");
		buttonPanel.add(optionsButton);
		optionsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				OptionsDialog od = new OptionsDialog(ConnectionDialog.this);
				od.initControlsFromSettings(settings, true);
				od.setVisible(true);
                toFront();
            }
		});

		JButton closeButton = new JButton("Close");
		buttonPanel.add(closeButton);
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
				appWindowListener.windowClosing(null);
			}
		});

		GridBagConstraints cButtons = new GridBagConstraints();
		cButtons.gridx = 0; cButtons.gridy = gridRow;
		cButtons.weightx = 100; cButtons.weighty = 100;
		cButtons.gridwidth = 2; cButtons.gridheight = 1;
		pane.add(buttonPanel, cButtons);

		getRootPane().setDefaultButton(connectButton);

		addWindowListener(appWindowListener);

		setResizable(false);
        Utils.decorateDialog(this);
		Utils.centerWindow(this);
	}

    private void initConnectionsHistoryCombo() {
        serverNameCombo.setEditable(true);
        new AutoCompletionComboEditorDocument(serverNameCombo); // use autocompletion feature for ComboBox
        connectionParams.completeEmptyFieldsFrom(connectionsHistory.getMostSuitableConnection(connectionParams));
        completeCombo();
        serverNameCombo.setRenderer(new HostnameComboboxRenderer());
    }


    private void completeCombo() {
		if (Strings.isTrimmedEmpty(connectionParams.hostName) && connectionsHistory.getConnectionsList().isEmpty()) {
			connectionParams.hostName = "";
			serverNameCombo.addItem(new ConnectionParams());
			return;
		}
		serverNameCombo.addItem(new ConnectionParams(connectionParams));
		for (ConnectionParams cp : connectionsHistory.getConnectionsList()) {
			if ( ! cp.equals(connectionParams)) {
                serverNameCombo.addItem(cp);
			}
        }
	}

	private void completeInputFieldsFrom(ConnectionParams cp) {
		serverPortField.setText(String.valueOf(cp.getPortNumber()));
		if (hasJsch) completeSshInputFieldsFrom(cp);
	}

	private int createSshOptions(final ConnectionParams connectionParams, JPanel pane, int gridRow) {
		GridBagConstraints cUseSshTunnelLabel = new GridBagConstraints();
		cUseSshTunnelLabel.gridx = 0; cUseSshTunnelLabel.gridy = gridRow;
		cUseSshTunnelLabel.weightx = 100; cUseSshTunnelLabel.weighty = 100;
		cUseSshTunnelLabel.gridwidth = 2; cUseSshTunnelLabel.gridheight = 1;
		cUseSshTunnelLabel.anchor = GridBagConstraints.LINE_START;
		cUseSshTunnelLabel.ipadx = PADDING;
		cUseSshTunnelLabel.ipady = 10;
		useSshTunnelingCheckbox = new JCheckBox("Use SSH tunneling");
		pane.add(useSshTunnelingCheckbox, cUseSshTunnelLabel);
		++gridRow;

        sshHostLabel = new JLabel("SSH Server:");
        sshHostField = new JTextField(COLUMNS_HOST_FIELD);
        addFormFieldRow(pane, gridRow, sshHostLabel, sshHostField, true);
        ++gridRow;

        sshPortLabel = new JLabel("SSH Port:");
        sshPortField = new JTextField(COLUMNS_PORT_USER_FIELD);
        addFormFieldRow(pane, gridRow, sshPortLabel, sshPortField, false);
        ++gridRow;

        sshUserLabel = new JLabel("SSH User:");
        sshUserField = new JTextField(COLUMNS_PORT_USER_FIELD);
        JPanel sshUserFieldPane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sshUserFieldPane.add(sshUserField);
        ssUserWarningLabel = new JLabel(" (will be asked if not specified)");
        sshUserFieldPane.add(ssUserWarningLabel);
        addFormFieldRow(pane, gridRow, sshUserLabel, sshUserFieldPane, false);
        ++gridRow;

        useSshTunnelingCheckbox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				final boolean useSsh = e.getStateChange() == ItemEvent.SELECTED;
				connectionParams.setUseSsh(useSsh);
                sshUserLabel.setEnabled(useSsh);
                sshUserField.setEnabled(useSsh);
                ssUserWarningLabel.setEnabled(useSsh);
                sshHostLabel.setEnabled(useSsh);
                sshHostField.setEnabled(useSsh);
                sshPortLabel.setEnabled(useSsh);
                sshPortField.setEnabled(useSsh);
            }
		});

		completeSshInputFieldsFrom(connectionParams);

        return gridRow;
	}

    private void addFormFieldRow(JPanel pane, int gridRow, JLabel label, JComponent field, boolean fill) {
        GridBagConstraints cLabel = new GridBagConstraints();
        cLabel.gridx = 0; cLabel.gridy = gridRow;
        cLabel.weightx = cLabel.weighty = 100;
        cLabel.gridwidth = cLabel.gridheight = 1;
        cLabel.anchor = GridBagConstraints.LINE_END;
        cLabel.ipadx = PADDING;
        cLabel.ipady = 10;
        pane.add(label, cLabel);

        GridBagConstraints cField = new GridBagConstraints();
        cField.gridx = 1; cField.gridy = gridRow;
        cField.weightx = 0; cField.weighty = 100;
        cField.gridwidth = cField.gridheight = 1;
        cField.anchor = GridBagConstraints.LINE_START;
        if (fill) cField.fill = GridBagConstraints.HORIZONTAL;
        pane.add(field, cField);
    }

    private void completeSshInputFieldsFrom(ConnectionParams connectionParams) {
        boolean useSsh = connectionParams.useSsh();
        useSshTunnelingCheckbox.setSelected(useSsh);
        sshUserLabel.setEnabled(useSsh);
        sshUserField.setEnabled(useSsh);
        ssUserWarningLabel.setEnabled(useSsh);
        sshHostLabel.setEnabled(useSsh);
        sshHostField.setEnabled(useSsh);
        sshPortLabel.setEnabled(useSsh);
        sshPortField.setEnabled(useSsh);
        sshUserField.setText(connectionParams.sshUserName);
        sshHostField.setText(connectionParams.sshHostName);
        sshPortField.setText(String.valueOf(connectionParams.getSshPortNumber()));
	}

	private void setSshOptions() {
		if (hasJsch) {
			connectionParams.sshUserName = sshUserField.getText();
            connectionParams.sshHostName = sshHostField.getText();
            connectionParams.parseSshPortNumber(sshPortField.getText());
            sshPortField.setText(String.valueOf(connectionParams.getSshPortNumber()));
		}
	}

	protected boolean validateFields() {
		return ! Strings.isTrimmedEmpty(connectionParams.hostName);
	}

	protected void setServerNameString(String text) {
		connectionParams.hostName = text;
	}

	public void setPort(String text) {
		connectionParams.parseRfbPortNumber(text);
	}

}
