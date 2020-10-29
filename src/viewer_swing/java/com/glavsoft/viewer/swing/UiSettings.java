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

import com.glavsoft.core.SettingsChangedEvent;
import com.glavsoft.rfb.IChangeSettingsListener;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * @author dime at tightvnc.com
 */
public class UiSettings {
    public static final int MIN_SCALE_PERCENT = 10;
	public static final int MAX_SCALE_PERCENT = 500;
    private static final int SCALE_PERCENT_ZOOMING_STEP = 10;
	
	public static final int CHANGED_SCALE_FACTOR = 1 << 0;
	public static final int CHANGED_SYSTEM_CURSOR = 1 << 1;

	private int changedSettingsMask = 0;

	private double scalePercent = 100;
	private final List<IChangeSettingsListener> listeners = new LinkedList<IChangeSettingsListener>();

	public UiSettings() {
		scalePercent = 100;
		changedSettingsMask = 0;
	}

	private UiSettings(UiSettings uiSettings) {
		this.scalePercent = uiSettings.scalePercent;
		this.changedSettingsMask = uiSettings.changedSettingsMask;
	}

	public double getScaleFactor() {
		return scalePercent / 100.;
	}

	public void setScalePercent(double scalePercent) {
		this.scalePercent = scalePercent;
		changedSettingsMask |= CHANGED_SCALE_FACTOR;
	}

	public void addListener(IChangeSettingsListener listener) {
		listeners.add(listener);
	}

	public void fireListeners() {
		final SettingsChangedEvent event = new SettingsChangedEvent(new UiSettings(this));
		changedSettingsMask = 0;
		for (IChangeSettingsListener listener : listeners) {
			listener.settingsChanged(event);
		}
	}

    public void zoomOut() {
	    double oldScaleFactor = scalePercent;
	    double scaleFactor = (int)(this.scalePercent / SCALE_PERCENT_ZOOMING_STEP) * SCALE_PERCENT_ZOOMING_STEP;
	    if (scaleFactor == oldScaleFactor) {
		    scaleFactor -= SCALE_PERCENT_ZOOMING_STEP;
	    }
	    if (scaleFactor < MIN_SCALE_PERCENT) {
		    scaleFactor = MIN_SCALE_PERCENT;
	    }
	    setScalePercent(scaleFactor);
	    fireListeners();
    }

    public void zoomIn() {
	    double scaleFactor = (int)(this.scalePercent / SCALE_PERCENT_ZOOMING_STEP) * SCALE_PERCENT_ZOOMING_STEP + SCALE_PERCENT_ZOOMING_STEP;
	    if (scaleFactor > MAX_SCALE_PERCENT) {
		    scaleFactor = MAX_SCALE_PERCENT;
	    }
	    setScalePercent(scaleFactor);
	    fireListeners();
    }

    public void zoomAsIs() {
	    setScalePercent(100);
	    fireListeners();
    }

	public void zoomToFit(int containerWidth, int containerHeight, int fbWidth, int fbHeight) {
		int scalePromille = Math.min(1000 * containerWidth / fbWidth,
				1000 * containerHeight / fbHeight);
		while (fbWidth * scalePromille / 1000. > containerWidth ||
				fbHeight * scalePromille / 1000. > containerHeight) {
			scalePromille -= 1;
		}
		setScalePercent(scalePromille / 10.);
		fireListeners();
	}

	public boolean isChangedScaleFactor() {
		return (changedSettingsMask & CHANGED_SCALE_FACTOR) == CHANGED_SCALE_FACTOR;
	}

	public boolean isChangedSystemCursor() {
		return (changedSettingsMask & CHANGED_SYSTEM_CURSOR) == CHANGED_SYSTEM_CURSOR;
	}

	public static boolean isUiSettingsChangedFired(SettingsChangedEvent event) {
		return event.getSource() instanceof UiSettings;
	}

	public double getScalePercent() {
		return scalePercent;
	}

	public String getScalePercentFormatted() {
		NumberFormat numberFormat = new DecimalFormat("###.#");
		return numberFormat.format(scalePercent);
	}
}
