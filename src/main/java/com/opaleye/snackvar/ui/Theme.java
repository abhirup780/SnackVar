/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Added by the SnackVar 3.0 modernisation fork. See NOTICE.
 */

package com.opaleye.snackvar.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javafx.scene.Scene;

/**
 * Central palette for the application.
 *
 * <p>Two consumers need the same colours and must not drift apart: the JavaFX
 * controls, styled from {@code snackvar.css}, and the chromatogram, which is
 * painted with AWT into a {@link java.awt.image.BufferedImage}. CSS cannot
 * reach the second one, so the trace colours live here in Java and the
 * stylesheet is picked to match.
 *
 * <p>Registered scenes have their stylesheet swapped on {@link #toggle()};
 * listeners registered with {@link #onChange} let the trace panes repaint,
 * since their images are baked at the colours current when they were drawn.
 */
public final class Theme {

	public enum Mode {
		LIGHT, DARK
	}

	private static final String PREF_KEY = "theme";

	private static Mode mode = loadSavedMode();
	private static final List<Scene> scenes = new ArrayList<>();
	private static final List<Runnable> listeners = new ArrayList<>();

	private Theme() {
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(Theme.class);
	}

	private static Mode loadSavedMode() {
		try {
			return Mode.valueOf(prefs().get(PREF_KEY, Mode.LIGHT.name()));
		} catch (RuntimeException ex) {
			// Unreadable or unknown preference: fall back rather than fail to start.
			return Mode.LIGHT;
		}
	}

	public static Mode mode() {
		return mode;
	}

	public static boolean isDark() {
		return mode == Mode.DARK;
	}

	/** Stylesheet URL for the current mode, as required by {@link Scene#getStylesheets()}. */
	public static String stylesheet() {
		String name = isDark() ? "/com/opaleye/snackvar/snackvar-dark.css"
				: "/com/opaleye/snackvar/snackvar-light.css";
		return Theme.class.getResource(name).toExternalForm();
	}

	private static String baseStylesheet() {
		return Theme.class.getResource("/com/opaleye/snackvar/snackvar.css").toExternalForm();
	}

	/**
	 * Applies the current theme to a scene and keeps it in sync with later toggles.
	 * Safe to call more than once for the same scene.
	 */
	public static void apply(Scene scene) {
		if (scene == null) {
			return;
		}
		if (!scenes.contains(scene)) {
			scenes.add(scene);
		}
		restyle(scene);
	}

	/** Stops tracking a scene whose window has closed, so it can be collected. */
	public static void forget(Scene scene) {
		scenes.remove(scene);
	}

	private static void restyle(Scene scene) {
		scene.getStylesheets().setAll(baseStylesheet(), stylesheet());
	}

	/** Registers a callback fired after every theme change (e.g. to repaint traces). */
	public static void onChange(Runnable listener) {
		listeners.add(listener);
	}

	public static void toggle() {
		set(isDark() ? Mode.LIGHT : Mode.DARK);
	}

	public static void set(Mode newMode) {
		if (newMode == mode) {
			return;
		}
		mode = newMode;
		try {
			prefs().put(PREF_KEY, mode.name());
		} catch (RuntimeException ex) {
			// A read-only preference store must not stop the theme from changing.
		}
		for (Scene scene : scenes) {
			restyle(scene);
		}
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	/*
	 * Chromatogram palette. Traces are drawn onto an opaque background, so every
	 * colour here needs enough contrast against traceBackground() in both modes.
	 * G is conventionally black; on a dark ground it becomes near-white so the
	 * four channels stay distinguishable.
	 */

	public static Color traceBackground() {
		return isDark() ? new Color(0x1C1F26) : Color.WHITE;
	}

	public static Color baseA() {
		return isDark() ? new Color(0x4ADE80) : new Color(0x15803D);
	}

	public static Color baseT() {
		return isDark() ? new Color(0xF87171) : new Color(0xDC2626);
	}

	public static Color baseG() {
		return isDark() ? new Color(0xE5E7EB) : new Color(0x111827);
	}

	public static Color baseC() {
		return isDark() ? new Color(0x60A5FA) : new Color(0x2563EB);
	}

	/** Colour for ambiguity codes (R, Y, K, M, S, W) and anything not A/T/G/C. */
	public static Color baseOther() {
		return isDark() ? new Color(0xF0ABFC) : new Color(0xC026D3);
	}

	/** Tick marks and position numbers under the trace. */
	public static Color axis() {
		return isDark() ? new Color(0x9AA1AB) : new Color(0x6B7280);
	}

	/** Fill used to shade a selected base or a region about to be trimmed. */
	public static Color highlight() {
		return isDark() ? new Color(0x60A5FA) : new Color(0x2563EB);
	}

	public static Color forBase(char base) {
		switch (Character.toUpperCase(base)) {
		case 'A':
			return baseA();
		case 'T':
			return baseT();
		case 'G':
			return baseG();
		case 'C':
			return baseC();
		default:
			return baseOther();
		}
	}
}
