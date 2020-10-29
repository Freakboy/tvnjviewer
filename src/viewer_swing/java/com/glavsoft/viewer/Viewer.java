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

import com.glavsoft.core.SettingsChangedEvent;
import com.glavsoft.exceptions.*;
import com.glavsoft.rfb.IChangeSettingsListener;
import com.glavsoft.rfb.IPasswordRetriever;
import com.glavsoft.rfb.IRfbSessionListener;
import com.glavsoft.rfb.client.KeyEventMessage;
import com.glavsoft.rfb.protocol.Protocol;
import com.glavsoft.rfb.protocol.ProtocolContext;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.transport.Reader;
import com.glavsoft.transport.Writer;
import com.glavsoft.utils.Keymap;
import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.cli.Parser;
import com.glavsoft.viewer.swing.*;
import com.glavsoft.viewer.swing.gui.OptionsDialog;
import com.glavsoft.viewer.swing.gui.PasswordDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public class Viewer extends JPanel implements Runnable, IRfbSessionListener, WindowListener, IChangeSettingsListener {

    public static Logger logger = Logger.getLogger("com.glavsoft");
    private boolean isZoomToFitSelected;
    private boolean forceReconnection;
    private String reconnectionReason;
    private ContainerManager containerManager;
    private final ConnectionParams connectionParams;
    private String passwordFromParams;
    private Socket workingSocket;
    private Protocol workingProtocol;
    private JFrame containerFrame;
    // boolean isSeparateFrame = true;
    boolean isSeparateFrame = false;
    // boolean isApplet = true;
    boolean isApplet = false;
    boolean showControls = true;
    private Surface surface;
    private final ProtocolSettings settings;
    private final UiSettings uiSettings;
    private boolean tryAgain;
    private boolean isAppletStopped = false;
    private volatile boolean isStoppingProcess;
    private List<JComponent> kbdButtons;

    protected ViewerCallback callme = null;

    public JComponent getContentPane() {
        return this;
    }

    public Protocol getWorkingProtocol() {
        return workingProtocol;
    }

    public boolean isZoomToFitSelected() {
        return isZoomToFitSelected;
    }

    public Surface getSurface() {
        return surface;
    }

    public UiSettings getUiSettings() {
        return uiSettings;
    }

    public void setZoomToFitSelected(boolean zoomToFitSelected) {
        isZoomToFitSelected = zoomToFitSelected;
    }

    public static void main(String[] args) {
        Parser parser = new Parser();
        ParametersHandler.completeParserOptions(parser);

        parser.parse(args);
        if (parser.isSet(ParametersHandler.ARG_HELP)) {
            printUsage(parser.optionsUsage());
            // System.exit(0);
        }
        Viewer viewer = new Viewer(parser);
        SwingUtilities.invokeLater(viewer);
    }

    public static void printUsage(String additional) {
        System.out.println("Usage: java -jar (progfilename) [hostname [port_number]] [Options]\n" +
                "    or\n" +
                " java -jar (progfilename) [Options]\n" +
                "    or\n java -jar (progfilename) -help\n    to view this help\n\n" +
                "Where Options are:\n" + additional +
                "\nOptions format: -optionName=optionValue. Ex. -host=localhost -port=5900 -viewonly=yes\n" +
                "Both option name and option value are case insensitive.");
    }

    public Viewer() {
        connectionParams = new ConnectionParams();
        settings = ProtocolSettings.getDefaultSettings();
        uiSettings = new UiSettings();
    }

    public Viewer(String host, int port, boolean highq, ViewerCallback listener) {
        this.connectionParams = new ConnectionParams(host, port);
        this.settings = highq ? ProtocolSettings.getHighQualitySettings() : ProtocolSettings.getLowQualitySettings();
        this.uiSettings = new UiSettings();
        this.init();
        this.callme = listener;
    }

    private Viewer(Parser parser) {
        this();
        ParametersHandler.completeSettingsFromCLI(parser, connectionParams, settings, uiSettings);
        showControls = ParametersHandler.showControls;
        passwordFromParams = parser.getValueFor(ParametersHandler.ARG_PASSWORD);
        logger.info("TightVNC Viewer version " + ver());
        isApplet = false;
    }


    @Override
    public void rfbSessionStopped(final String reason) {
        cleanUpUISessionAndConnection();
        Messages.print_error("died: " + reason);
        /*if (isStoppingProcess)
            return;
        cleanUpUISessionAndConnection();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                forceReconnection = true;
                reconnectionReason = reason;
            }
        });
        // start new session
        SwingUtilities.invokeLater(this);*/
    }

    private synchronized void cleanUpUISessionAndConnection() {
        isStoppingProcess = true;
        if (workingSocket != null && workingSocket.isConnected()) {
            try {
                workingSocket.close();
            } catch (IOException e) { /*nop*/ }
        }
        if (containerFrame != null) {
            containerFrame.dispose();
            containerFrame = null;
        }
        isStoppingProcess = false;
    }

    @Override
    public void windowClosing(WindowEvent e) {
        if (e != null && e.getComponent() != null) {
            e.getWindow().setVisible(false);
        }
        closeApp();
    }

    /**
     * Closes App(lication) or stops App(let).
     */
    // private void closeApp() {
    public void closeApp() {
        if (workingProtocol != null) {
            workingProtocol.cleanUpSession();
        }
        cleanUpUISessionAndConnection();
        isAppletStopped = true;
        repaint();
        tryAgain = false;
        /*if (isApplet) {
            logger.severe("Applet is stopped.");
            isAppletStopped = true;
            repaint();
        } else {
            System.exit(0);
        }*/
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        /*if (!isAppletStopped) {
            super.paint(g);
        } else {
            getContentPane().removeAll();
            g.clearRect(0, 0, getWidth(), getHeight());
            g.drawString("Disconnected", 10, 20);
        }*/
    }

    // @Override
    public void destroy() {
        closeApp();
        // super.destroy();
    }

    // @Override
    public void init() {
        this.showControls = true;
        this.isSeparateFrame = false;
        this.passwordFromParams = "";
        this.isApplet = false;
        this.repaint();
        new Thread(this).start();
        /*ParametersHandler.completeSettingsFromApplet(this, connectionParams, settings, uiSettings);
        showControls = ParametersHandler.showControls;
        isSeparateFrame = ParametersHandler.isSeparateFrame;
        passwordFromParams = getParameter(ParametersHandler.ARG_PASSWORD);
        isApplet = true;

        repaint();
        SwingUtilities.invokeLater(this);*/
    }

    // @Override
    public void start() {
        setSurfaceToHandleKbdFocus();
        // super.start();
    }

    @Override
    public void run() {
        ConnectionManager connectionManager = new ConnectionManager(this, isApplet);
        long start = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - start > 60000L) {
                Messages.print_error("Unable to connect after 60s of trying.");
                JOptionPane.showMessageDialog(null, "Could not establish a connection to the user's\ndesktop. Make sure you're in a process that has\na desktop session associated with it.", "VNC Error", 0);
                return;
            }
            workingSocket = connectionManager.connectToHost(connectionParams, settings);
            if (null != this.workingSocket) {
                Messages.print_good("I am connected.");
                try {
                    this.setDoubleBuffered(true);
                    Reader reader = new Reader(this.workingSocket.getInputStream());
                    Writer writer = new Writer(this.workingSocket.getOutputStream());
                    this.workingProtocol = new Protocol(reader, writer, new PasswordChooser(this.passwordFromParams, this.connectionParams, this.containerFrame, this), this.settings);
                    try {
                        this.workingSocket.setSoTimeout(30000);
                        this.workingProtocol.handshake();
                        this.workingSocket.setSoTimeout(0);
                    } catch (Exception ex) {
                        Messages.print_error("Connection to VNC server didn't respond. " + ex.getMessage());
                        this.workingSocket.close();
                        JOptionPane.showMessageDialog(null, "VNC server connection is not responding.\nTry launching VNC again!", "VNC Error", 0);
                        return;
                    }
                    ClipboardControllerImpl clipboardController = new ClipboardControllerImpl(this.workingProtocol, this.settings.getRemoteCharsetName());
                    clipboardController.setEnabled(this.settings.isAllowClipboardTransfer());
                    this.settings.addListener(clipboardController);
                    this.surface = new Surface(this.workingProtocol, this, this.uiSettings.getScaleFactor());
                    this.settings.addListener(this);
                    this.uiSettings.addListener(this.surface);
                    this.containerFrame = this.createContainer();
                    connectionManager.setContainerFrame(this.containerFrame);
                    this.updateFrameTitle();
                    this.workingProtocol.startNormalHandling(this, this.surface, clipboardController);
                    if (this.callme != null) {
                        this.callme.connected(this);
                    }
                    return;
                } catch (Exception ex) {
                    Messages.print_error("Error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            try {
                Thread.sleep(5000L);
            } catch (Exception ex) {
            }
        }
    }
    /*@Override
    public void run() {
        ConnectionManager connectionManager = new ConnectionManager(this, isApplet);

        if (forceReconnection) {
            connectionManager.showReconnectDialog("Connection lost", reconnectionReason);
            forceReconnection = false;
        }
        tryAgain = true;
        while (tryAgain) {
            workingSocket = connectionManager.connectToHost(connectionParams, settings);
            if (null == workingSocket) {
                closeApp();
                break;
            }
            logger.info("Connected");

            try {
                workingSocket.setTcpNoDelay(true); // disable Nagle algorithm
                Reader reader = new Reader(workingSocket.getInputStream());
                Writer writer = new Writer(workingSocket.getOutputStream());

                workingProtocol = new Protocol(reader, writer,
                        new PasswordChooser(passwordFromParams, connectionParams, containerFrame, this),
                        settings);
                workingProtocol.handshake();

                ClipboardControllerImpl clipboardController =
                        new ClipboardControllerImpl(workingProtocol, settings.getRemoteCharsetName());
                clipboardController.setEnabled(settings.isAllowClipboardTransfer());
                settings.addListener(clipboardController);

                surface = new Surface(workingProtocol, this, uiSettings.getScaleFactor());
                settings.addListener(this);
                uiSettings.addListener(surface);
                containerFrame = createContainer();
                connectionManager.setContainerFrame(containerFrame);
                updateFrameTitle();

                workingProtocol.startNormalHandling(this, surface, clipboardController);
                tryAgain = false;
            } catch (UnsupportedProtocolVersionException e) {
                connectionManager.showReconnectDialog("Unsupported Protocol Version", e.getMessage());
                logger.severe(e.getMessage());
            } catch (UnsupportedSecurityTypeException e) {
                connectionManager.showReconnectDialog("Unsupported Security Type", e.getMessage());
                logger.severe(e.getMessage());
            } catch (AuthenticationFailedException e) {
                passwordFromParams = null;
                connectionManager.showReconnectDialog("Authentication Failed", e.getMessage());
                logger.severe(e.getMessage());
            } catch (TransportException e) {
                if (!isAppletStopped) {
                    connectionManager.showReconnectDialog("Connection Error", "Connection Error" + ": " + e.getMessage());
                    logger.severe(e.getMessage());
                }
            } catch (IOException e) {
                connectionManager.showReconnectDialog("Connection Error", "Connection Error" + ": " + e.getMessage());
                logger.severe(e.getMessage());
            } catch (FatalException e) {
                connectionManager.showReconnectDialog("Connection Error", "Connection Error" + ": " + e.getMessage());
                logger.severe(e.getMessage());
            }
        }
    }*/

    private JFrame createContainer() {
        containerManager = new ContainerManager(this);
        Container container = containerManager.createContainer(surface, isSeparateFrame, isApplet);

        if (showControls) {
            createButtonsPanel(workingProtocol, containerManager);
            containerManager.registerResizeListener(container);
            containerManager.updateZoomButtonsState();
        }
        setSurfaceToHandleKbdFocus();
        return isSeparateFrame ? (JFrame) container : null;
    }

    public void packContainer() {
        containerManager.pack();
    }

    @Override
    public void validate() {
        super.validate();
        packContainer();
    }

    protected void createButtonsPanel(final ProtocolContext context, ContainerManager containerManager) {
        final ContainerManager.ButtonsBar buttonsBar = containerManager.createButtonsBar();

        addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent ev) {
                setSurfaceToHandleKbdFocus();
                ev.consume();
            }

            @Override
            public void keyPressed(KeyEvent ev) {
                setSurfaceToHandleKbdFocus();
                ev.consume();
            }

            @Override
            public void keyReleased(KeyEvent ev) {
                setSurfaceToHandleKbdFocus();
                ev.consume();
            }
        });
        buttonsBar.createButton("refresh", "Refresh screen", new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                context.sendRefreshMessage();
                setSurfaceToHandleKbdFocus();
                packContainer();
            }
        });

        JToggleButton viewButton = buttonsBar.createToggleButton("view", "View Only", new ItemListener() {

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    getSurface().setViewOnly(true);
                    ((JComponent) e.getSource()).setBackground(Color.RED);
                } else {
                    getSurface().setViewOnly(false);
                    setSurfaceToHandleKbdFocus();
                    ((JComponent) e.getSource()).setBackground(null);
                }
            }
        });
        viewButton.setSelected(true);
        containerManager.addZoomButtons();
        kbdButtons = new LinkedList<JComponent>();
        buttonsBar.createStrut();
        JButton winButton = buttonsBar.createButton("win", "Send 'Win' key as 'Ctrl-Esc'", new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                sendWinKey(context);
                setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(winButton);
        final JToggleButton ctrlButton = buttonsBar.createToggleButton("ctrl", "Ctrl Lock", new ItemListener() {

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
                    ((JComponent) e.getSource()).setBackground(Color.RED);
                } else {
                    context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
                    ((JComponent) e.getSource()).setBackground(null);
                }
                setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(ctrlButton);
        final JToggleButton altButton = buttonsBar.createToggleButton("alt", "Alt Lock", new ItemListener() {

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    context.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, true));
                    ((JComponent) e.getSource()).setBackground(Color.RED);
                } else {
                    context.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, false));
                    ((JComponent) e.getSource()).setBackground(null);
                }
                Viewer.this.setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(altButton);
        ModifierButtonEventListener modifierButtonListener = new ModifierButtonEventListener();
        modifierButtonListener.addButton(KeyEvent.VK_CONTROL, ctrlButton);
        modifierButtonListener.addButton(KeyEvent.VK_ALT, altButton);
        surface.addModifierListener(modifierButtonListener);
        containerManager.setButtonsBarVisible(true);
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentShown(ComponentEvent ev) {
                setSurfaceToHandleKbdFocus();
            }

            @Override
            public void componentHidden(ComponentEvent ev) {
                ctrlButton.setSelected(false);
                altButton.setSelected(false);
            }
        });


        /*buttonsBar.createButton("options", "Set Options", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOptionsDialog();
                setSurfaceToHandleKbdFocus();
            }
        });

        buttonsBar.createButton("info", "Show connection info", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showConnectionInfoMessage(context.getRemoteDesktopName());
                setSurfaceToHandleKbdFocus();
            }
        });

        buttonsBar.createStrut();

        buttonsBar.createButton("refresh", "Refresh screen", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                context.sendRefreshMessage();
                setSurfaceToHandleKbdFocus();
            }
        });

        containerManager.addZoomButtons();

        kbdButtons = new LinkedList<JComponent>();

        buttonsBar.createStrut();

        JButton ctrlAltDelButton = buttonsBar.createButton("ctrl-alt-del", "Send 'Ctrl-Alt-Del'", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendCtrlAltDel(context);
                setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(ctrlAltDelButton);

        JButton winButton = buttonsBar.createButton("win", "Send 'Win' key as 'Ctrl-Esc'", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendWinKey(context);
                setSurfaceToHandleKbdFocus();
            }
        });
        kbdButtons.add(winButton);

        JToggleButton ctrlButton = buttonsBar.createToggleButton("ctrl", "Ctrl Lock",
                new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
                        } else {
                            context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
                        }
                        setSurfaceToHandleKbdFocus();
                    }
                });
        kbdButtons.add(ctrlButton);

        JToggleButton altButton = buttonsBar.createToggleButton("alt", "Alt Lock",
                new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            context.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, true));
                        } else {
                            context.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, false));
                        }
                        setSurfaceToHandleKbdFocus();
                    }
                });
        kbdButtons.add(altButton);

        ModifierButtonEventListener modifierButtonListener = new ModifierButtonEventListener();
        modifierButtonListener.addButton(KeyEvent.VK_CONTROL, ctrlButton);
        modifierButtonListener.addButton(KeyEvent.VK_ALT, altButton);
        surface.addModifierListener(modifierButtonListener);

//		JButton fileTransferButton = new JButton(Utils.getButtonIcon("file-transfer"));
//		fileTransferButton.setMargin(buttonsMargin);
//		buttonBar.add(fileTransferButton);

        buttonsBar.createStrut();

        buttonsBar.createButton("close", isApplet ? "Disconnect" : "Close", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeApp();
            }
        }).setAlignmentX(RIGHT_ALIGNMENT);

        containerManager.setButtonsBarVisible(true);*/

    }

    void updateFrameTitle() {
        if (containerFrame != null) {
            containerFrame.setTitle(
                    workingProtocol.getRemoteDesktopName() + " [zoom: " + uiSettings.getScalePercentFormatted() + "%]");
        }
    }

    protected void setSurfaceToHandleKbdFocus() {
        if (surface != null && !surface.requestFocusInWindow()) {
            surface.requestFocus();
        }
    }

    @Override
    public void settingsChanged(SettingsChangedEvent e) {
        ProtocolSettings settings = (ProtocolSettings) e.getSource();
        setEnabledKbdButtons(!settings.isViewOnly());
    }

    private void setEnabledKbdButtons(boolean enabled) {
        if (kbdButtons != null) {
            for (JComponent b : kbdButtons) {
                b.setEnabled(enabled);
            }
        }
    }

    private void showOptionsDialog() {
        OptionsDialog optionsDialog = new OptionsDialog(containerFrame);
        optionsDialog.initControlsFromSettings(settings, false);
        optionsDialog.setVisible(true);
    }

    private void showConnectionInfoMessage(final String title) {
        StringBuilder message = new StringBuilder();
        message.append("Connected to: ").append(title).append("\n");
        message.append("Host: ").append(connectionParams.hostName)
                .append(" Port: ").append(connectionParams.getPortNumber()).append("\n\n");

        message.append("Desktop geometry: ")
                .append(String.valueOf(surface.getWidth()))
                .append(" \u00D7 ") // multiplication sign
                .append(String.valueOf(surface.getHeight())).append("\n");
        message.append("Color format: ")
                .append(String.valueOf(Math.round(Math.pow(2, workingProtocol.getPixelFormat().depth))))
                .append(" colors (")
                .append(String.valueOf(workingProtocol.getPixelFormat().depth))
                .append(" bits)\n");
        message.append("Current protocol version: ")
                .append(workingProtocol.getProtocolVersion());
        if (workingProtocol.isTight()) {
            message.append("tight");
        }
        message.append("\n");

        JOptionPane infoPane = new JOptionPane(message.toString(), JOptionPane.INFORMATION_MESSAGE);
        final JDialog infoDialog = infoPane.createDialog(containerFrame, "VNC connection info");
        infoDialog.setModalityType(ModalityType.MODELESS);
        infoDialog.setVisible(true);
    }

    private void sendCtrlAltDel(ProtocolContext context) {
        context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
        context.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, true));
        context.sendMessage(new KeyEventMessage(Keymap.K_DELETE, true));
        context.sendMessage(new KeyEventMessage(Keymap.K_DELETE, false));
        context.sendMessage(new KeyEventMessage(Keymap.K_ALT_LEFT, false));
        context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
    }

    private void sendWinKey(ProtocolContext context) {
        context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, true));
        context.sendMessage(new KeyEventMessage(Keymap.K_ESCAPE, true));
        context.sendMessage(new KeyEventMessage(Keymap.K_ESCAPE, false));
        context.sendMessage(new KeyEventMessage(Keymap.K_CTRL_LEFT, false));
    }

    @Override
    public void windowOpened(WindowEvent e) { /* nop */ }

    @Override
    public void windowClosed(WindowEvent e) { /* nop */ }

    @Override
    public void windowIconified(WindowEvent e) { /* nop */ }

    @Override
    public void windowDeiconified(WindowEvent e) { /* nop */ }

    @Override
    public void windowActivated(WindowEvent e) { /* nop */ }

    @Override
    public void windowDeactivated(WindowEvent e) { /* nop */ }

    public static interface ViewerCallback {
        void connected(Viewer viewer);
    }

    /**
     * Ask user for password if needed
     */
    private class PasswordChooser implements IPasswordRetriever {
        private final String passwordPredefined;
        private final ConnectionParams connectionParams;
        PasswordDialog passwordDialog;
        private final JFrame owner;
        private final WindowListener onClose;

        private PasswordChooser(String passwordPredefined, ConnectionParams connectionParams,
                                JFrame owner, WindowListener onClose) {
            this.passwordPredefined = passwordPredefined;
            this.connectionParams = connectionParams;
            this.owner = owner;
            this.onClose = onClose;
        }

        @Override
        public String getPassword() {
            return Strings.isTrimmedEmpty(passwordPredefined) ?
                    getPasswordFromGUI() : passwordPredefined;
        }

        private String getPasswordFromGUI() {
            if (null == passwordDialog) {
                passwordDialog = new PasswordDialog(owner, onClose);
            }
            passwordDialog.setServerHostName(connectionParams.hostName + ":" + connectionParams.getPortNumber());
            passwordDialog.toFront();
            passwordDialog.setVisible(true);
            return passwordDialog.getPassword();
        }
    }

    private static String ver() {
        final InputStream mfStream = Viewer.class.getClassLoader().getResourceAsStream(
                "META-INF/MANIFEST.MF");
        if (null == mfStream) {
            System.out.println("No Manifest file found.");
            return "-1";
        }
        try {
            Manifest mf = new Manifest();
            mf.read(mfStream);
            Attributes atts = mf.getMainAttributes();
            return atts.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        } catch (IOException e) {
            return "-2";
        }
    }

}
