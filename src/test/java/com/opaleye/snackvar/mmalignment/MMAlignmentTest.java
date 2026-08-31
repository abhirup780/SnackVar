package com.opaleye.snackvar.mmalignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the Myers-Miller aligner. The old build exercised this through a
 * {@code main} method carrying hardcoded paths from the author's machine;
 * these are the same checks, run by the build.
 */
class MMAlignmentTest {

	private static final int GAP_OPEN = 30;

	@Test
	@DisplayName("aligned strings always come back the same length")
	void alignedStringsAreTheSameLength() {
		MMAlignment mma = new MMAlignment(GAP_OPEN);
		AlignedPair ap = mma.localAlignment("AATTTTAATTAAATGCATGCATGC", "AATTGCA");

		assertEquals(ap.getAlignedString1().length(), ap.getAlignedString2().length(),
				"an alignment column must have a character on both rows");
	}

	@Test
	@DisplayName("an identical subsequence aligns without gaps")
	void identicalSubsequenceAlignsCleanly() {
		MMAlignment mma = new MMAlignment(GAP_OPEN);
		String reference = "GGGGACGTACGTACGTTTTT";
		AlignedPair ap = mma.localAlignment(reference, "ACGTACGTACGT");

		assertEquals("ACGTACGTACGT", ap.getAlignedString1());
		assertEquals("ACGTACGTACGT", ap.getAlignedString2());
	}

	@Test
	@DisplayName("start offsets locate the match within the reference")
	void reportsWhereTheMatchStarts() {
		MMAlignment mma = new MMAlignment(GAP_OPEN);
		String reference = "TTTTTTACGTACGTACGTAAAAAA";
		AlignedPair ap = mma.localAlignment(reference, "ACGTACGTACGT");

		assertEquals(6, ap.getStart1(), "the read starts at offset 6 in the reference");
		assertEquals(0, ap.getStart2());
	}

	@Test
	@DisplayName("a single-base deletion shows up as one gap in the read")
	void singleBaseDeletionProducesOneGap() {
		MMAlignment mma = new MMAlignment(GAP_OPEN);
		// The read is the reference with the 'C' at position 9 removed.
		AlignedPair ap = mma.localAlignment("AAGGTTCCAACGTTGGCCAATT", "AAGGTTCCACGTTGGCCAATT");

		String read = ap.getAlignedString2();
		assertEquals(ap.getAlignedString1().length(), read.length());
		assertEquals(1, read.chars().filter(c -> c == '-').count(),
				"exactly one gap should be opened for a one-base deletion");
	}

	@Test
	@DisplayName("an empty read yields an empty alignment rather than throwing")
	void emptyReadIsHandled() {
		MMAlignment mma = new MMAlignment(GAP_OPEN);
		AlignedPair ap = mma.globalAlignment("ACGTACGT", "");

		assertEquals(8, ap.getAlignedString1().length());
		assertTrue(ap.getAlignedString2().chars().allMatch(c -> c == '-'),
				"every column of an empty read must be a gap");
	}

	@Test
	@DisplayName("addRight concatenates both rows")
	void addRightConcatenatesBothRows() {
		AlignedPair left = new AlignedPair("AC", "A-");
		AlignedPair right = new AlignedPair("GT", "GT");

		AlignedPair joined = left.addRight(right);
		assertEquals("ACGT", joined.getAlignedString1());
		assertEquals("A-GT", joined.getAlignedString2());
	}
}
