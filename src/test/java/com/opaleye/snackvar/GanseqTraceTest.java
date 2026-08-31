package com.opaleye.snackvar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.opaleye.snackvar.tools.SymbolTools;

/**
 * Exercises trace loading and the derived data, using a synthesised .ab1 so the
 * pipeline is covered without shipping proprietary instrument output.
 */
class GanseqTraceTest {

	private static final String SEQ =
			"ACGTACGTAAGGTTCCAACCGGTTACGTACGTTTAAGGCCATATGCGCTAGCTAGCATCGATCGATTTAAACCCGGGTTT";

	private static GanseqTrace load(Path dir, String sequence) throws Exception {
		Path ab1 = dir.resolve("test.ab1");
		SyntheticTrace.write(ab1, sequence);
		return new GanseqTrace(ab1.toFile(), null);
	}

	@Test
	@DisplayName("a synthesised trace round-trips through the ABI reader")
	void readsBackTheSequence(@TempDir Path dir) throws Exception {
		GanseqTrace trace = load(dir, SEQ);

		assertEquals(SEQ, trace.getSequence());
		assertEquals(SEQ.length(), trace.getSequenceLength());
		assertEquals(SEQ.length(), trace.getBaseCalls().length);
		assertEquals(SEQ.length(), trace.getQCalls().length);
		assertTrue(trace.getTraceLength() > SEQ.length() * SyntheticTrace.SPACING - 1);
	}

	@Test
	@DisplayName("base calls sit on the tallest channel at each peak")
	void peaksLineUpWithTheCalledBases(@TempDir Path dir) throws Exception {
		GanseqTrace trace = load(dir, SEQ);

		int[][] channels = { trace.getTraceA(), trace.getTraceT(), trace.getTraceG(), trace.getTraceC() };
		for (int i = 0; i < trace.getSequenceLength(); i++) {
			int at = trace.getBaseCalls()[i];
			int tallest = 0;
			for (int c = 1; c < 4; c++) {
				if (channels[c][at] > channels[tallest][at]) {
					tallest = c;
				}
			}
			assertEquals(SEQ.charAt(i), SymbolTools.numberToBase(tallest),
					"channel mismatch at base " + (i + 1));
		}
	}

	@Test
	@DisplayName("a double peak becomes the matching IUPAC ambiguity code")
	void doublePeakBecomesAmbiguityCode(@TempDir Path dir) throws Exception {
		// Position 20 (0-based) carries both A and G, which is IUPAC 'R'.
		StringBuilder second = new StringBuilder(SEQ);
		second.setCharAt(20, SEQ.charAt(20) == 'A' ? 'G' : 'A');

		Path ab1 = dir.resolve("het.ab1");
		SyntheticTrace.write(ab1, SEQ, second.toString(), 55);
		GanseqTrace trace = new GanseqTrace(ab1.toFile(), null);
		trace.applyAmbiguousSymbol();

		char resolved = trace.getSequence().charAt(20);
		assertEquals(SymbolTools.makeAmbiguousSymbol(SEQ.charAt(20), second.charAt(20)), resolved,
				"a 50/50 double peak should resolve to the two-base ambiguity code");
	}

	@Test
	@DisplayName("a clean trace produces no spurious ambiguity codes")
	void cleanTraceStaysUnambiguous(@TempDir Path dir) throws Exception {
		GanseqTrace trace = load(dir, SEQ);
		trace.applyAmbiguousSymbol();

		assertEquals(SEQ, trace.getSequence(),
				"a homozygous trace must not gain ambiguity codes");
	}

	@Test
	@DisplayName("reverse-complementing twice returns the original trace")
	void complementIsItsOwnInverse(@TempDir Path dir) throws Exception {
		GanseqTrace trace = load(dir, SEQ);
		int[] originalA = trace.getTraceA().clone();

		trace.makeComplement();
		assertEquals(SymbolTools.getComplementString(SEQ), trace.getSequence());
		assertEquals(GanseqTrace.REVERSE, trace.getDirection());

		trace.makeComplement();
		assertEquals(SEQ, trace.getSequence());
		assertEquals(GanseqTrace.FORWARD, trace.getDirection());
		org.junit.jupiter.api.Assertions.assertArrayEquals(originalA, trace.getTraceA());
	}

	@Test
	@DisplayName("clone copies the arrays instead of sharing them")
	void cloneIsADeepCopy(@TempDir Path dir) throws Exception {
		// Object.clone() alone handed the copy the original's channel arrays,
		// so an in-place write through one would corrupt the other.
		GanseqTrace trace = load(dir, SEQ);
		GanseqTrace copy = trace.clone();

		assertNotSame(trace.getTraceA(), copy.getTraceA());
		assertNotSame(trace.getBaseCalls(), copy.getBaseCalls());
		assertNotSame(trace.getQCalls(), copy.getQCalls());

		copy.getTraceA()[0] = 31000;
		assertNotSame(31000, trace.getTraceA()[0]);
	}

	@Test
	@DisplayName("trimming keeps only the bases inside the retained window")
	void trimmingKeepsTheInnerBases(@TempDir Path dir) throws Exception {
		GanseqTrace trace = load(dir, SEQ);
		int width = GanseqTrace.traceWidth;

		// Cut ten bases off each end, in image pixels.
		int start = (trace.getBaseCalls()[9] + 3) * width;
		int end = (trace.getBaseCalls()[SEQ.length() - 10] - 3) * width;
		trace.makeTrimmedTrace(start, end, false);

		assertTrue(trace.getSequenceLength() < SEQ.length());
		assertTrue(SEQ.contains(trace.getSequence()),
				"the trimmed sequence must still be a run of the original");
		assertEquals(trace.getSequenceLength(), trace.getBaseCalls().length);
		assertEquals(trace.getSequenceLength(), trace.getQCalls().length);
	}

	@Test
	@DisplayName("chromatograms render at full colour depth")
	void rendersFullColourChromatogram(@TempDir Path dir) throws Exception {
		// The original drew onto a 256-entry TYPE_BYTE_INDEXED surface, which
		// dithered the curves; the trace panes now use full RGB.
		GanseqTrace trace = load(dir, SEQ);
		BufferedImage image = trace.getDefaultImage();

		assertEquals(BufferedImage.TYPE_INT_RGB, image.getType());
		assertEquals(trace.getTraceLength() * GanseqTrace.traceWidth, image.getWidth());
		assertTrue(image.getHeight() > 100);
	}

	@Test
	@DisplayName("zoom stays within sane bounds")
	void zoomIsBounded(@TempDir Path dir) throws Exception {
		GanseqTrace trace = load(dir, SEQ);
		int defaultHeight = trace.getDefaultImage().getHeight();

		for (int i = 0; i < 40; i++) {
			trace.zoomOut();
		}
		assertTrue(trace.getDefaultImage().getHeight() > 0,
				"zooming out repeatedly must not collapse the trace to nothing");

		for (int i = 0; i < 100; i++) {
			trace.zoomIn();
		}
		assertTrue(trace.getDefaultImage().getHeight() < 10000,
				"zooming in repeatedly must not grow without bound");

		trace.zoomDefault();
		assertEquals(defaultHeight, trace.getDefaultImage().getHeight());
	}
}
