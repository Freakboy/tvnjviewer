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

package com.glavsoft.viewer.swing;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Utils for Swing GUI
 */
public class Utils {
	private static List<Image> icons;

	private static List<Image> getApplicationIcons() {
		if (icons != null) {
			return icons;
		}
		icons = new LinkedList<Image>();
		URL resource = Utils.class.getResource("/com/glavsoft/viewer/images/tightvnc-logo-16x16.png");
		Image image = resource != null ?
				Toolkit.getDefaultToolkit().getImage(resource) :
				null;
		if (image != null) {
			icons.add(image);
		}
		resource = Utils.class.getResource("/com/glavsoft/viewer/images/tightvnc-logo-32x32.png");
		image = resource != null ?
				Toolkit.getDefaultToolkit().getImage(resource) :
				null;
		if (image != null) {
			icons.add(image);
		}
		return icons;
	}

	public static ImageIcon getButtonIcon(String name) {
		URL resource = Utils.class.getResource("/com/glavsoft/viewer/images/button-"+name+".png");
		return resource != null ? new ImageIcon(resource) : null;
	}

	public static void decorateDialog(JDialog dialog) {
        try {
            dialog.setAlwaysOnTop(true);
        } catch (SecurityException e) {
            // nop
        }
		dialog.pack();
		dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.toFront();
		Utils.setApplicationIconsForWindow(dialog);
	}

	public static void setApplicationIconsForWindow(Window window) {
		List<Image> icons = getApplicationIcons();
		if (icons.size() != 0) {
			window.setIconImages(icons);
		}
	}

	public static void centerWindow(Window window) {
        Point locationPoint = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
		Rectangle bounds = window.getBounds();
		locationPoint.setLocation(locationPoint.x - bounds.width/2, locationPoint.y - bounds.height/2);
		window.setLocation(locationPoint);
	}
}
