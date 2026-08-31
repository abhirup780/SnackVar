package com.opaleye.snackvar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.opaleye.snackvar.ui.Theme;

/**
 * The chromatogram is painted with AWT, so its colours cannot come from CSS.
 * These checks keep the two palettes complete and legible.
 */
class ThemeTest {

	@AfterEach
	void resetTheme() {
		Theme.set(Theme.Mode.LIGHT);
	}

	@Test
	@DisplayName("toggling switches modes and back")
	void toggleSwitchesMode() {
		Theme.set(Theme.Mode.LIGHT);
		assertEquals(Theme.Mode.LIGHT, Theme.mode());

		Theme.toggle();
		assertEquals(Theme.Mode.DARK, Theme.mode());
		assertTrue(Theme.isDark());

		Theme.toggle();
		assertEquals(Theme.Mode.LIGHT, Theme.mode());
	}

	@Test
	@DisplayName("both stylesheets are present on the classpath")
	void stylesheetsResolve() {
		Theme.set(Theme.Mode.LIGHT);
		assertNotNull(Theme.stylesheet());
		Theme.set(Theme.Mode.DARK);
		assertNotNull(Theme.stylesheet());
	}

	@Test
	@DisplayName("the four channels are distinct in both modes")
	void channelColoursAreDistinct() {
		for (Theme.Mode mode : Theme.Mode.values()) {
			Theme.set(mode);
			Color[] channels = { Theme.baseA(), Theme.baseT(), Theme.baseG(), Theme.baseC() };
			for (int i = 0; i < channels.length; i++) {
				for (int j = i + 1; j < channels.length; j++) {
					assertNotEquals(channels[i], channels[j],
							"channels " + i + " and " + j + " collide in " + mode);
				}
			}
		}
	}

	@Test
	@DisplayName("every trace colour contrasts with the background it is drawn on")
	void traceColoursContrastWithBackground() {
		for (Theme.Mode mode : Theme.Mode.values()) {
			Theme.set(mode);
			Color bg = Theme.traceBackground();
			Color[] colours = { Theme.baseA(), Theme.baseT(), Theme.baseG(), Theme.baseC(),
					Theme.baseOther(), Theme.axis() };
			for (Color c : colours) {
				double ratio = contrastRatio(c, bg);
				assertTrue(ratio >= 3.0,
						"contrast " + String.format("%.2f", ratio) + " too low in " + mode
								+ " for rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")");
			}
		}
	}

	@Test
	@DisplayName("bases map to their channel colour, and anything else to the ambiguity colour")
	void baseLookup() {
		Theme.set(Theme.Mode.LIGHT);
		assertEquals(Theme.baseA(), Theme.forBase('A'));
		assertEquals(Theme.baseA(), Theme.forBase('a'));
		assertEquals(Theme.baseC(), Theme.forBase('C'));
		assertEquals(Theme.baseOther(), Theme.forBase('N'));
		assertEquals(Theme.baseOther(), Theme.forBase('R'));
	}

	/** WCAG relative-luminance contrast ratio, 1.0 (identical) to 21.0 (black on white). */
	private static double contrastRatio(Color a, Color b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(Color c) {
		return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
	}

	private static double channel(int value) {
		double v = value / 255.0;
		return (v <= 0.03928) ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}
}
