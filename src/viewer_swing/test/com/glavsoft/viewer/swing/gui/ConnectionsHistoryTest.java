package com.glavsoft.viewer.swing.gui;

import com.glavsoft.viewer.swing.ConnectionParams;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author dime at tightvnc.com
 */
public class ConnectionsHistoryTest {

    private ConnectionsHistory history;

    @Before
    public void setUpConnectionsList() throws Exception {
        history = new ConnectionsHistory(null);
        history.connections = new LinkedList<ConnectionParams>();

    }

    @Test
    public void testGetMostSuitableConnection() throws Exception {
        ConnectionParams cp1t = new ConnectionParams("host1", 1, true, "sshHost1", 101, "sshUser1");
        history.connections.add(new ConnectionParams(cp1t));
        ConnectionParams cp1f = new ConnectionParams("host1", 1, false, "sshHost1", 101, "sshUser1");
        history.connections.add(new ConnectionParams(cp1f));
        ConnectionParams cp2 = new ConnectionParams("host2", 1, true, "sshHost1", 101, "sshUser1");
        history.connections.add(new ConnectionParams(cp2));

        ConnectionParams cp22 = new ConnectionParams("host2", 2, false, "sshHost1", 101, "sshUser1");
        history.connections.add(cp22);
        ConnectionParams cp222 = new ConnectionParams("host2", 2, true, "sshHost1", 101, "sshUser1");
        history.connections.add(cp222);
        ConnectionParams cp2222 = new ConnectionParams("host2", 2, true, "sshHost2", 101, "sshUser1");
        history.connections.add(cp2222);
        ConnectionParams cp22222 = new ConnectionParams("host2", 2, true, "sshHost2", 102, "sshUser2");
        history.connections.add(cp22222);
        ConnectionParams cp0t = new ConnectionParams("host0", 0, true, "sshHost0", 100, "sshUser0");
        history.connections.add(cp0t);
        System.out.println(history.getMostSuitableConnection(cp0t));
        assertTrue(cp0t == history.getMostSuitableConnection(cp0t));
        assertEquals(cp0t, history.getMostSuitableConnection(cp0t));
        assertEquals(cp1f, history.getMostSuitableConnection(cp1f));

        assertEquals("Must be very first", cp1t, history.getMostSuitableConnection(new ConnectionParams()));
        assertEquals("Must be very first", cp1t, history.getMostSuitableConnection(new ConnectionParams("trash", 666, false, "trash", 999, "trash")));
        assertEquals(cp2, history.getMostSuitableConnection(new ConnectionParams("host2", 3, true, "trash", 999, "trash")));
        assertEquals(cp22, history.getMostSuitableConnection(new ConnectionParams("host2", 2, false, "trash", 999, "trash")));
        assertEquals(cp222, history.getMostSuitableConnection(new ConnectionParams("host2", 2, true, "trash", 999, "trash")));
        assertEquals(cp2222, history.getMostSuitableConnection(new ConnectionParams("host2", 2, true, "sshHost2", 999, "trash")));
        assertEquals(cp22222, history.getMostSuitableConnection(new ConnectionParams("host2", 2, true, "sshHost2", 102, "sshUser2")));
        assertEquals(cp22, history.getMostSuitableConnection(new ConnectionParams("host2", 2, false, "sshHost2", 101, "sshUser2")));

    }
}
