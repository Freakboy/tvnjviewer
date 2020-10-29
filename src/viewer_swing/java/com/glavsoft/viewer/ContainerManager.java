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

import com.glavsoft.viewer.swing.Surface;
import com.glavsoft.viewer.swing.UiSettings;
import com.glavsoft.viewer.swing.Utils;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ContainerManager {
	public static final int FS_SCROLLING_ACTIVE_BORDER = 20;
	private final Viewer viewer;
	private JToggleButton zoomFitButton;
	private JToggleButton zoomFullScreenButton;
	private JButton zoomInButton;
	private JButton zoomOutButton;
	private JButton zoomAsIsButton;
	private JPanel outerPanel;
	private JScrollPane scroller;
	private Container container;
	private boolean forceResizable = true;
	private ButtonsBar buttonsBar;
	private Surface surface;
	private boolean isSeparateFrame;
	private Rectangle oldContainerBounds;
	private volatile boolean isFullScreen;
	private Border oldScrollerBorder;
	private JLayeredPane lpane;
	private EmptyButtonsBarMouseAdapter buttonsBarMouseAdapter;

	public ContainerManager(Viewer viewer) {
		this.viewer = viewer;
	}

	public Container createContainer(final Surface surface, boolean isSeparateFrame, boolean isApplet) {
		this.surface = surface;
		this.isSeparateFrame = isSeparateFrame;
		outerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
			@Override
			public Dimension getSize() {
				return surface.getPreferredSize();
			}
			@Override
			public Dimension getPreferredSize() {
				return surface.getPreferredSize();
			}
		};
		lpane = new JLayeredPane() {
			@Override
			public Dimension getSize() {
				return surface.getPreferredSize();
			}
			@Override
			public Dimension getPreferredSize() {
				return surface.getPreferredSize();
			}
		};
		lpane.setPreferredSize(surface.getPreferredSize());
		lpane.add(surface, JLayeredPane.DEFAULT_LAYER, 0);
		outerPanel.add(lpane);

		JFrame frame;
		scroller = new JScrollPane(outerPanel);
		if (isSeparateFrame) {
			frame = new JFrame();
			if ( ! isApplet) {
				frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			}
            frame.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
			Utils.setApplicationIconsForWindow(frame);
			container = frame;
		} else {
			container = viewer;
		}
		container.setLayout(new BorderLayout(0, 0));
		container.add(scroller, BorderLayout.CENTER);

		if (isSeparateFrame) {
//			frame.pack();
			outerPanel.setSize(surface.getPreferredSize());
			internalPack(null);
			container.setVisible(true);
		}
		container.validate();
		return container;
	}

	public void pack() {
		final Dimension outerPanelOldSize = outerPanel.getSize();
		outerPanel.setSize(viewer.getSurface().getPreferredSize());
		if (container != viewer && ! viewer.isZoomToFitSelected()) {
			internalPack(outerPanelOldSize);
		}
        if (buttonsBar != null) {
            updateZoomButtonsState();
        }
		viewer.updateFrameTitle();
	}

	private void internalPack(Dimension outerPanelOldSize) {
		final Rectangle workareaRectangle = getWorkareaRectangle();
		if (workareaRectangle.equals(container.getBounds())) {
			forceResizable = true;
		}
		final boolean isHScrollBar = scroller.getHorizontalScrollBar().isShowing() && ! forceResizable;
		final boolean isVScrollBar = scroller.getVerticalScrollBar().isShowing() && ! forceResizable;

		boolean isWidthChangeable = true;
		boolean isHeightChangeable = true;
		if (outerPanelOldSize != null && viewer.getSurface().oldSize != null) {
			isWidthChangeable = forceResizable ||
					(outerPanelOldSize.width == viewer.getSurface().oldSize.width && ! isHScrollBar);
			isHeightChangeable = forceResizable ||
					(outerPanelOldSize.height == viewer.getSurface().oldSize.height && ! isVScrollBar);
		}
		forceResizable = false;
		container.validate();

		final Insets containerInsets = container.getInsets();
		Dimension preferredSize = container.getPreferredSize();
		Rectangle preferredRectangle = new Rectangle(container.getLocation(), preferredSize);

		if (null == outerPanelOldSize && workareaRectangle.contains(preferredRectangle)) {
			((JFrame)container).pack();
		} else {
			Dimension minDimension = new Dimension(
					containerInsets.left + containerInsets.right, containerInsets.top + containerInsets.bottom);
			if (buttonsBar != null && buttonsBar.isVisible) {
				minDimension.width += buttonsBar.getWidth();
				minDimension.height += buttonsBar.getHeight();
			}
			Dimension dim = new Dimension(preferredSize);
			Point location = container.getLocation();
			if ( ! isWidthChangeable) {
				dim.width = container.getWidth();
			} else {
				if (isVScrollBar) dim.width += scroller.getVerticalScrollBar().getWidth();
				if (dim.width < minDimension.width) dim.width = minDimension.width;

				int dx = location.x - workareaRectangle.x;
				if (dx < 0) {
					dx = 0;
					location.x = workareaRectangle.x;
				}
				int w = workareaRectangle.width - dx;
				if (w < dim.width) {
					int dw = dim.width - w;
					if (dw < dx) {
						location.x -= dw;
					} else {
						dim.width = workareaRectangle.width;
						location.x = workareaRectangle.x;
					}
				}
			}
			if ( ! isHeightChangeable) {
				dim.height = container.getHeight();
			} else {

				if (isHScrollBar) dim.height += scroller.getHorizontalScrollBar().getHeight();
				if (dim.height < minDimension.height) dim.height = minDimension.height;

				int dy = location.y - workareaRectangle.y;
				if (dy < 0) {
					dy = 0;
					location.y = workareaRectangle.y;
				}
				int h = workareaRectangle.height - dy;
				if (h < dim.height) {
					int dh = dim.height - h;
					if (dh < dy) {
						location.y -= dh;
					} else {
						dim.height = workareaRectangle.height;
						location.y = workareaRectangle.y;
					}
				}
			}
			if ( ! location.equals(container.getLocation())) {
				container.setLocation(location);
			}
			if ( ! isFullScreen ) {
				container.setSize(dim);
			}
		}
		scroller.revalidate();
	}

	private Rectangle getWorkareaRectangle() {
		final GraphicsConfiguration graphicsConfiguration = container.getGraphicsConfiguration();
		final Rectangle screenBounds = graphicsConfiguration.getBounds();
		final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);

		screenBounds.x += screenInsets.left;
		screenBounds.y += screenInsets.top;
		screenBounds.width -= screenInsets.left + screenInsets.right;
		screenBounds.height -= screenInsets.top + screenInsets.bottom;
		return screenBounds;
	}

	void addZoomButtons() {
		buttonsBar.createStrut();
		zoomOutButton = buttonsBar.createButton("zoom-out", "Zoom Out", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				zoomFitButton.setSelected(false);
				viewer.getUiSettings().zoomOut();
			}
		});
		zoomInButton = buttonsBar.createButton("zoom-in", "Zoom In", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				zoomFitButton.setSelected(false);
				viewer.getUiSettings().zoomIn();
			}
		});
		zoomAsIsButton = buttonsBar.createButton("zoom-100", "Zoom 100%", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				zoomFitButton.setSelected(false);
				forceResizable = false;
				viewer.getUiSettings().zoomAsIs();
			}
		});

		zoomFitButton = buttonsBar.createToggleButton("zoom-fit", "Zoom to Fit Window",
				new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent e) {
						if (e.getStateChange() == ItemEvent.SELECTED) {
							viewer.setZoomToFitSelected(true);
							forceResizable = true;
							zoomToFit();
							updateZoomButtonsState();
						} else {
							viewer.setZoomToFitSelected(false);
						}
						viewer.setSurfaceToHandleKbdFocus();
					}
				});

		zoomFullScreenButton = buttonsBar.createToggleButton("zoom-fullscreen", "Full Screen",
			new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) {
					updateZoomButtonsState();
					if (e.getStateChange() == ItemEvent.SELECTED) {
						svitchOnFullscreenMode();
					} else {
						switchOffFullscreenMode();
					}
					viewer.setSurfaceToHandleKbdFocus();
				}
			});
			if ( ! isSeparateFrame) {
				zoomFullScreenButton.setEnabled(false);
				zoomFitButton.setEnabled(false);
			}
		}

	private void svitchOnFullscreenMode() {
		zoomFullScreenButton.setSelected(true);
		oldContainerBounds = container.getBounds();
		setButtonsBarVisible(false);
		forceResizable = true;
		final JFrame frame = (JFrame)container;
		frame.dispose();
		frame.setUndecorated(true);
		frame.setResizable(false);
		frame.setVisible(true);
		try {
			frame.getGraphicsConfiguration().getDevice().setFullScreenWindow(frame);
			isFullScreen = true;
			scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
			scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			oldScrollerBorder = scroller.getBorder();
			scroller.setBorder(new EmptyBorder(0, 0, 0, 0));
			new FullscreenBorderDetectionThread(frame).start();
		} catch (Exception ex) {
			// nop
		}
	}

	private void switchOffFullscreenMode() {
		if (isFullScreen) {
			zoomFullScreenButton.setSelected(false);
			isFullScreen = false;
			setButtonsBarVisible(true);
			JFrame frame = (JFrame)container;
			try {
				frame.dispose();
				frame.setUndecorated(false);
				frame.setResizable(true);
				frame.getGraphicsConfiguration().getDevice().setFullScreenWindow(null);
			} catch (Exception e) {
				// nop
			}
			scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
			scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scroller.setBorder(oldScrollerBorder);
			container.setBounds(oldContainerBounds);
			frame.setVisible(true);
			pack();
		}
	}

	private void zoomToFit() {
		Dimension scrollerSize = scroller.getSize();
		Insets scrollerInsets = scroller.getInsets();
		viewer.getUiSettings().zoomToFit(scrollerSize.width - scrollerInsets.left - scrollerInsets.right,
				scrollerSize.height - scrollerInsets.top - scrollerInsets.bottom +
						(isFullScreen ? buttonsBar.getHeight() : 0),
				viewer.getWorkingProtocol().getFbWidth(), viewer.getWorkingProtocol().getFbHeight());
	}

	void registerResizeListener(Container container) {
		container.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (viewer.isZoomToFitSelected()) {
					zoomToFit();
					updateZoomButtonsState();
					viewer.updateFrameTitle();
					viewer.setSurfaceToHandleKbdFocus();
				}
			}
		});
	}

	void updateZoomButtonsState() {
		zoomOutButton.setEnabled(viewer.getUiSettings().getScalePercent() > UiSettings.MIN_SCALE_PERCENT);
		zoomInButton.setEnabled(viewer.getUiSettings().getScalePercent() < UiSettings.MAX_SCALE_PERCENT);
		zoomAsIsButton.setEnabled(viewer.getUiSettings().getScalePercent() != 100);
	}

	public ButtonsBar createButtonsBar() {
		buttonsBar = new ButtonsBar();
		return buttonsBar;
	}

	public void setButtonsBarVisible(boolean isVisible) {
		buttonsBar.setVisible(isVisible);
		if (isVisible) {
			buttonsBar.borderOff();
			container.add(buttonsBar.bar, BorderLayout.NORTH);
		} else {
			container.remove(buttonsBar.bar);
			buttonsBar.borderOn();
		}
	}

	public void setButtonsBarVisibleFS(boolean isVisible) {
		if (isVisible) {
			if ( ! buttonsBar.isVisible) {
				lpane.add(buttonsBar.bar, JLayeredPane.POPUP_LAYER, 0);
				final int bbWidth = buttonsBar.bar.getPreferredSize().width;
				buttonsBar.bar.setBounds(
						scroller.getViewport().getViewPosition().x + (scroller.getWidth() - bbWidth)/2, 0,
						bbWidth, buttonsBar.bar.getPreferredSize().height);

				// prevent mouse events to through down to Surface
				if (null == buttonsBarMouseAdapter) buttonsBarMouseAdapter = new EmptyButtonsBarMouseAdapter();
				buttonsBar.bar.addMouseListener(buttonsBarMouseAdapter);
			}
		} else {
			buttonsBar.bar.removeMouseListener(buttonsBarMouseAdapter);
			lpane.remove(buttonsBar.bar);
			lpane.repaint(buttonsBar.bar.getBounds());
		}
		buttonsBar.setVisible(isVisible);
	}

	public static class ButtonsBar {
		private static final Insets BUTTONS_MARGIN = new Insets(2, 2, 2, 2);
		private JPanel bar;
		private boolean isVisible;

		public ButtonsBar() {
			bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		}

		public JButton createButton(String iconId, String tooltipText, ActionListener actionListener) {
			JButton button = new JButton(Utils.getButtonIcon(iconId));
			button.setToolTipText(tooltipText);
			button.setMargin(BUTTONS_MARGIN);
			bar.add(button);
			button.addActionListener(actionListener);
			return button;
		}

		public void createStrut() {
			bar.add(Box.createHorizontalStrut(10));
		}

		public JToggleButton createToggleButton(String iconId, String tooltipText, ItemListener itemListener) {
			JToggleButton button = new JToggleButton(Utils.getButtonIcon(iconId));
			button.setToolTipText(tooltipText);
			button.setMargin(BUTTONS_MARGIN);
			bar.add(button);
			button.addItemListener(itemListener);
			return button;
		}

		public void setVisible(boolean isVisible) {
			this.isVisible = isVisible;
		}

		public int getWidth() {
			return bar.getMinimumSize().width;
		}
		public int getHeight() {
			return bar.getMinimumSize().height;
		}

		public void borderOn() {
			bar.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		}

		public void borderOff() {
			bar.setBorder(BorderFactory.createEmptyBorder());
		}
	}

	private static class EmptyButtonsBarMouseAdapter extends MouseAdapter {
		// empty
	}

	private class FullscreenBorderDetectionThread extends Thread {
		public static final int SHOW_HIDE_BUTTONS_BAR_DELAY_IN_MILLS = 700;
		private final JFrame frame;
		private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		private ScheduledFuture<?> futureForShow;
		private ScheduledFuture<?> futureForHide;
		private Point mousePoint, oldMousePoint;
		private Point viewPosition;

		public FullscreenBorderDetectionThread(JFrame frame) {
			super("FS border detector");
			this.frame = frame;
		}

		public void run() {
			setPriority(Thread.MIN_PRIORITY);
			while(isFullScreen) {
				mousePoint = MouseInfo.getPointerInfo().getLocation();
				if (null == oldMousePoint) oldMousePoint = mousePoint;
				SwingUtilities.convertPointFromScreen(mousePoint, frame);
				viewPosition = scroller.getViewport().getViewPosition();
				processButtonsBarVisibility();

				boolean needScrolling = processVScroll() || processHScroll();
				oldMousePoint = mousePoint;
				if (needScrolling) {
					cancelShowExecutor();
					setButtonsBarVisibleFS(false);
					makeScrolling(viewPosition);
				}
				try {
                    Thread.sleep(100);
                } catch (Exception e) {
					// nop
				}
			}
		}

		private boolean processHScroll() {
			if (mousePoint.x < FS_SCROLLING_ACTIVE_BORDER) {
				if (viewPosition.x > 0) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - mousePoint.x;
					if (mousePoint.y != oldMousePoint.y) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.x -= delta;
					if (viewPosition.x < 0) viewPosition.x = 0;
					return true;
				}
			} else if (mousePoint.x > (frame.getWidth() - FS_SCROLLING_ACTIVE_BORDER)) {
				final Rectangle viewRect = scroller.getViewport().getViewRect();
				final int right = viewRect.width + viewRect.x;
				if (right < outerPanel.getSize().width) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - (frame.getWidth() - mousePoint.x);
					if (mousePoint.y != oldMousePoint.y) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.x += delta;
					if (viewPosition.x + viewRect.width > outerPanel.getSize().width) viewPosition.x =
							outerPanel.getSize().width - viewRect.width;
					return true;
				}
			}
			return false;
		}

		private boolean processVScroll() {
			if (mousePoint.y < FS_SCROLLING_ACTIVE_BORDER) {
				if (viewPosition.y > 0) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - mousePoint.y;
					if (mousePoint.x != oldMousePoint.x) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.y -= delta;
					if (viewPosition.y < 0) viewPosition.y = 0;
					return true;
				}
			} else if (mousePoint.y > (frame.getHeight() - FS_SCROLLING_ACTIVE_BORDER)) {
				final Rectangle viewRect = scroller.getViewport().getViewRect();
				final int bottom = viewRect.height + viewRect.y;
				if (bottom < outerPanel.getSize().height) {
					int delta = FS_SCROLLING_ACTIVE_BORDER - (frame.getHeight() - mousePoint.y);
					if (mousePoint.x != oldMousePoint.x) delta *= 2; // speedify scrolling on mouse moving
					viewPosition.y += delta;
					if (viewPosition.y + viewRect.height > outerPanel.getSize().height) viewPosition.y =
							outerPanel.getSize().height - viewRect.height;
					return true;
				}
			}
			return false;
		}

		private void processButtonsBarVisibility() {
			if (mousePoint.y < 1) {
				cancelHideExecutor();
				// show buttons bar after delay
				if (! buttonsBar.isVisible && (null == futureForShow || futureForShow.isDone())) {
					futureForShow = scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							showButtonsBar();
						}
					}, SHOW_HIDE_BUTTONS_BAR_DELAY_IN_MILLS, TimeUnit.MILLISECONDS);
				}
			} else {
				cancelShowExecutor();
			}
			if (buttonsBar.isVisible && mousePoint.y <= buttonsBar.getHeight()) {
				cancelHideExecutor();
			}
			if (buttonsBar.isVisible && mousePoint.y > buttonsBar.getHeight()) {
				// hide buttons bar after delay
				if (null == futureForHide || futureForHide.isDone()) {
					futureForHide = scheduler.schedule(new Runnable() {
						@Override
						public void run() {
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									setButtonsBarVisibleFS(false);
									container.validate();
								}
							});
						}
					}, SHOW_HIDE_BUTTONS_BAR_DELAY_IN_MILLS, TimeUnit.MILLISECONDS);
				}
			}
		}

		private void cancelHideExecutor() {
			cancelExecutor(futureForHide);
		}
		private void cancelShowExecutor() {
			cancelExecutor(futureForShow);
		}

		private void cancelExecutor(ScheduledFuture<?> future) {
			if (future != null && ! future.isDone()) {
				future.cancel(true);
			}
		}

		private void makeScrolling(final Point viewPosition) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					scroller.getViewport().setViewPosition(viewPosition);
					final Point mousePosition = surface.getMousePosition();
					if (mousePosition != null) {
						final MouseEvent mouseEvent = new MouseEvent(frame, 0, 0, 0,
								mousePosition.x, mousePosition.y, 0, false);
						for (MouseMotionListener mml : surface.getMouseMotionListeners()) {
							mml.mouseMoved(mouseEvent);
						}
					}
				}
			});
		}

		private void showButtonsBar() {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					setButtonsBarVisibleFS(true);
				}
			});
		}
	}
}