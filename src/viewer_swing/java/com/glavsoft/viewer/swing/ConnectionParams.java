package com.glavsoft.viewer.swing;

import com.glavsoft.utils.Strings;

/**
 * @author dime at tightvnc.com
 */
public class ConnectionParams {
    public static final int DEFAULT_SSH_PORT = 22;
    private static final int DEFAULT_RFB_PORT = 5900;

    public String hostName;
    private int portNumber;
    public String sshUserName;
    public String sshHostName;
    private int sshPortNumber;

    private boolean useSsh;

    public ConnectionParams(String hostName, int portNumber) {
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.sshUserName = "";
        this.sshHostName = "";
        this.sshPortNumber = 22;
        this.useSsh = false;
    }

    public ConnectionParams(String hostName, int portNumber, boolean useSsh,
                            String sshHostName, int sshPortNumber, String sshUserName) {
        this(hostName, portNumber);
        /*this.hostName = hostName;
        this.portNumber = portNumber;
        this.sshUserName = sshUserName;
        this.sshHostName = sshHostName;
        this.sshPortNumber = sshPortNumber;
        this.useSsh = useSsh;*/
    }

    public ConnectionParams(ConnectionParams cp) {
        this.hostName = cp.hostName;
        this.portNumber = cp.portNumber;
        this.sshUserName = cp.sshUserName;
        this.sshHostName = cp.sshHostName;
        this.sshPortNumber = cp.sshPortNumber;
        this.useSsh = cp.useSsh;
    }

    public ConnectionParams() {
        hostName = "";
        sshUserName = "";
        sshHostName = "";
    }

    public boolean isHostNameEmpty() {
        return Strings.isTrimmedEmpty(hostName);
    }

    public void parseRfbPortNumber(String port) {
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException e) { /*nop*/ }
    }

    public void parseSshPortNumber(String port) {
        try {
            sshPortNumber = Integer.parseInt(port);
        } catch (NumberFormatException e) { /*nop*/ }
    }

    public boolean useSsh() {
        return useSsh && !Strings.isTrimmedEmpty(sshHostName);
    }

    public void setUseSsh(boolean useSsh) {
        this.useSsh = useSsh;
    }

    public int getPortNumber() {
        return 0 == portNumber ? DEFAULT_RFB_PORT : portNumber;
    }

    public int getSshPortNumber() {
        return 0 == sshPortNumber ? DEFAULT_SSH_PORT : sshPortNumber;
    }

    public void completeEmptyFieldsFrom(ConnectionParams from) {
        if (null == from) {
            return;
        }
        if (Strings.isTrimmedEmpty(hostName) && !Strings.isTrimmedEmpty(from.hostName)) {
            hostName = from.hostName;
        }
        if (0 == portNumber && from.portNumber != 0) {
            portNumber = from.portNumber;
        }
        if (Strings.isTrimmedEmpty(sshUserName) && !Strings.isTrimmedEmpty(from.sshUserName)) {
            sshUserName = from.sshUserName;
        }
        if (Strings.isTrimmedEmpty(sshHostName) && !Strings.isTrimmedEmpty(from.sshHostName)) {
            sshHostName = from.sshHostName;
        }
        if (0 == sshPortNumber && from.sshPortNumber != 0) {
            sshPortNumber = from.sshPortNumber;
        }
        useSsh |= from.useSsh;
    }

    @Override
    public String toString() {
        return hostName != null ? hostName : "";
//        return (hostName != null ? hostName : "") + ":" + portNumber + " " + useSsh + " " + sshUserName + "@" + sshHostName + ":" + sshPortNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || !(obj instanceof ConnectionParams)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        ConnectionParams o = (ConnectionParams) obj;
        return isEqualsNullable(hostName, o.hostName) && getPortNumber() == o.getPortNumber() &&
                useSsh == o.useSsh && isEqualsNullable(sshHostName, o.sshHostName) &&
                getSshPortNumber() == o.getSshPortNumber() && isEqualsNullable(sshUserName, o.sshUserName);
    }

    private boolean isEqualsNullable(String one, String another) {
        return one == another || (null == one ? "" : one).equals(null == another ? "" : another);
    }

    @Override
    public int hashCode() {
        long hash = (hostName != null ? hostName.hashCode() : 0) +
                portNumber * 17 +
                (useSsh ? 781 : 693) +
                (sshHostName != null ? sshHostName.hashCode() : 0) * 23 +
                (sshUserName != null ? sshUserName.hashCode() : 0) * 37 +
                sshPortNumber * 41;
        return (int) hash;
    }
}
