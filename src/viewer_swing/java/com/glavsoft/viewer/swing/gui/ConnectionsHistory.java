package com.glavsoft.viewer.swing.gui;

import com.glavsoft.core.SettingsChangedEvent;
import com.glavsoft.rfb.IChangeSettingsListener;
import com.glavsoft.rfb.protocol.ProtocolSettings;
import com.glavsoft.utils.Strings;
import com.glavsoft.viewer.swing.ConnectionParams;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author dime at tightvnc.com
 */
public class ConnectionsHistory implements IChangeSettingsListener {
	private static int MAX_ITEMS = 32;
    public static final String CONNECTIONS_HISTORY_ROOT_NODE = "com/glavsoft/viewer/connectionsHistory";
    public static final String NODE_HOST_NAME = "hostName";
    public static final String NODE_PORT_NUMBER = "portNumber";
    public static final String NODE_SSH_USER_NAME = "sshUserName";
    public static final String NODE_SSH_HOST_NAME = "sshHostName";
    public static final String NODE_SSH_PORT_NUMBER = "sshPortNumber";
    public static final String NODE_USE_SSH = "useSsh";
    public static final String NODE_PROTOCOL_SETTINGS = "protocolSettings";

    private Map<ConnectionParams, ProtocolSettings> settingsMap;
    LinkedList<ConnectionParams> connections;
    private ConnectionParams workingConnectionParams;

    public ConnectionsHistory(ConnectionParams workingConnectionParams) {
        this.workingConnectionParams = workingConnectionParams;
        settingsMap = new HashMap<ConnectionParams, ProtocolSettings>();
        connections = new LinkedList<ConnectionParams>();
        retrieve();
	}

	private void retrieve() {
		Preferences root = Preferences.userRoot();
		Preferences connectionsHistoryNode = root.node(CONNECTIONS_HISTORY_ROOT_NODE);
		try {
            byte[] emptyByteArray = new byte[0];
            final String[] orderNums;
			orderNums = connectionsHistoryNode.childrenNames();
			SortedMap<Integer, ConnectionParams> conns = new TreeMap<Integer, ConnectionParams>();
            HashSet<ConnectionParams> uniques = new HashSet<ConnectionParams>();
			for (String orderNum : orderNums) {
                int num = 0;
                try {
                    num = Integer.parseInt(orderNum);
                } catch (NumberFormatException skip) {
                    //nop
                }
				Preferences node = connectionsHistoryNode.node(orderNum);
                String hostName = node.get(NODE_HOST_NAME, null);
                if (null == hostName) continue; // skip entries without hostName field
                ConnectionParams cp = new ConnectionParams(hostName, node.getInt(NODE_PORT_NUMBER, 0),
                        node.getBoolean(NODE_USE_SSH, false), node.get(NODE_SSH_HOST_NAME, ""), node.getInt(NODE_SSH_PORT_NUMBER, 0), node.get(NODE_SSH_USER_NAME, "")
                );
                if (uniques.contains(cp)) continue; // skip duplicates
                uniques.add(cp);
                conns.put(num, cp);
                byte[] bytes = node.getByteArray(NODE_PROTOCOL_SETTINGS, emptyByteArray);
                if (bytes.length != 0) {
                    try {
                        ProtocolSettings settings = (ProtocolSettings) (new ObjectInputStream(
                                new ByteArrayInputStream(bytes))).readObject();
                        settings.refine();
                        settingsMap.put(cp, settings);
                    } catch (IOException e) {
                        Logger.getLogger(this.getClass().getName()).fine("Cannot deserialize ProtocolSettings: " + e.getMessage());
                    } catch (ClassNotFoundException e) {
                        Logger.getLogger(this.getClass().getName()).severe("Cannot deserialize ProtocolSettings : " + e.getMessage());
                    }
                }
			}
			int itemsCount = 0;
			for (ConnectionParams cp : conns.values()) {
				if (itemsCount < MAX_ITEMS) {
                    connections.add(cp);
				} else {
					connectionsHistoryNode.node(cp.hostName).removeNode();
				}
				++itemsCount;
			}
		} catch (BackingStoreException e) {
            Logger.getLogger(this.getClass().getName()).severe("Cannot retrieve connections history info: " + e.getMessage());
		}
	}

	public LinkedList<ConnectionParams> getConnectionsList() {
		return connections;
	}

    public ProtocolSettings getSettings(ConnectionParams cp) {
        return settingsMap.get(cp);
    }

    public void save() {
		Preferences root = Preferences.userRoot();
		Preferences connectionsHistoryNode = root.node(CONNECTIONS_HISTORY_ROOT_NODE);
		final String[] hosts;
		try {
			hosts = connectionsHistoryNode.childrenNames();
			for (String host : hosts) {
				connectionsHistoryNode.node(host).removeNode();
			}
		} catch (BackingStoreException e) {
            Logger.getLogger(this.getClass().getName()).severe("Cannot remove node: " + e.getMessage());
		}
		int num = 0;
		for (ConnectionParams cp : connections) {
			if (num >= MAX_ITEMS) break;
			if ( ! Strings.isTrimmedEmpty(cp.hostName)) {
					addNode(cp, connectionsHistoryNode, num++);
			}
		}
	}

    private void addNode(ConnectionParams connectionParams, Preferences connectionsHistoryNode, int orderNum) {
        ProtocolSettings settings = settingsMap.get(connectionParams);
        final Preferences node = connectionsHistoryNode.node(String.valueOf(orderNum));
        node.put(NODE_HOST_NAME, connectionParams.hostName);
		node.putInt(NODE_PORT_NUMBER, connectionParams.getPortNumber());
        if (connectionParams.useSsh()) {
            node.putBoolean(NODE_USE_SSH, connectionParams.useSsh());
            node.put(NODE_SSH_USER_NAME, connectionParams.sshUserName != null ? connectionParams.sshUserName: "");
		    node.put(NODE_SSH_HOST_NAME, connectionParams.sshHostName != null ? connectionParams.sshHostName: "");
		    node.putInt(NODE_SSH_PORT_NUMBER, connectionParams.getSshPortNumber());
        }
        if (settings != null) {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(settings);
                node.putByteArray(NODE_PROTOCOL_SETTINGS, byteArrayOutputStream.toByteArray());
            } catch (IOException e) {
                Logger.getLogger(this.getClass().getName()).severe("Cannot serialize ProtocolSettings: " + e.getMessage());
            }
        }
		try {
			node.flush();
		} catch (BackingStoreException e) {
            Logger.getLogger(this.getClass().getName()).severe("Cannot retrieve connections history info: " + e.getMessage());
		}
	}

    void reorderConnectionsList(ConnectionParams connectionParams, ProtocolSettings settings) {
        while (connections.remove(connectionParams)) {/*empty - remove all occurrence*/}
		LinkedList<ConnectionParams> cpList = new LinkedList<ConnectionParams>();
		cpList.addAll(connections);

		connections.clear();
		connections.add(new ConnectionParams(connectionParams));
		connections.addAll(cpList);
        storeSettings(connectionParams, settings);
    }

    private void storeSettings(ConnectionParams connectionParams, ProtocolSettings settings) {
        ProtocolSettings savedSettings = settingsMap.get(connectionParams);
        if (savedSettings != null) {
            savedSettings.copySerializedFieldsFrom(settings);
        } else {
            settingsMap.put(new ConnectionParams(connectionParams), new ProtocolSettings(settings));
        }
    }

    public ConnectionParams getMostSuitableConnection(ConnectionParams orig) {
        ConnectionParams res = connections.isEmpty()? orig: connections.get(0);
        if (null == orig || null == orig.hostName) return res;
        for (ConnectionParams cp : connections) {
            if (orig.equals(cp)) return cp;
            if (compareTextFields(orig.hostName, res.hostName, cp.hostName)) {
                res = cp;
                continue;
            }
            if (orig.hostName.equals(cp.hostName) &&
                    comparePorts(orig.getPortNumber(), res.getPortNumber(), cp.getPortNumber())) {
                res = cp;
                continue;
            }
            if (orig.hostName.equals(cp.hostName) &&
                orig.getPortNumber() == cp.getPortNumber() &&
                    orig.useSsh() == cp.useSsh() && orig.useSsh() != res.useSsh()) {
                res = cp;
                continue;
            }
            if (orig.hostName.equals(cp.hostName) &&
                    orig.getPortNumber() == cp.getPortNumber() &&
                    orig.useSsh() && cp.useSsh() &&
                    compareTextFields(orig.sshHostName, res.sshHostName, cp.sshHostName)) {
                res = cp;
                continue;
            }
            if (orig.hostName.equals(cp.hostName) &&
                    orig.getPortNumber() == cp.getPortNumber() &&
                    orig.useSsh() && cp.useSsh() &&
                    orig.sshHostName != null && orig.sshHostName.equals(cp.hostName) &&
                    comparePorts(orig.getSshPortNumber(), res.getSshPortNumber(), cp.getSshPortNumber())) {
                res = cp;
                continue;
            }
            if (orig.hostName.equals(cp.hostName) &&
                    orig.getPortNumber() == cp.getPortNumber() &&
                    orig.useSsh() && cp.useSsh() &&
                    orig.sshHostName != null && orig.sshHostName.equals(cp.hostName) &&
                    orig.getSshPortNumber() == cp.getSshPortNumber() &&
                    compareTextFields(orig.sshUserName, res.sshUserName, cp.sshUserName)) {
                res = cp;
            }
        }
        return res;
    }

    private boolean comparePorts(int orig, int res, int test) {
        return orig == test && orig != res;
    }

    private boolean compareTextFields(String orig, String res, String test) {
        return (orig != null && test != null && res != null) &&
                orig.equals(test) && ! orig.equals(res);
    }

    @Override
    public void settingsChanged(SettingsChangedEvent event) {
        storeSettings(workingConnectionParams, (ProtocolSettings) event.getSource());
        save();
    }
}
