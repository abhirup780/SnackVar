package com.opaleye.snackvar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.opaleye.snackvar.mmalignment.AlignedPair;
import com.opaleye.snackvar.mmalignment.MMAlignment;
import com.opaleye.snackvar.reference.Reference;
import com.opaleye.snackvar.tools.SymbolTools;
import com.opaleye.snackvar.variants.Indel;
import com.opaleye.snackvar.variants.Variant;

/**
 * Covers heterozygous indel detection — the deconvolution that is SnackVar's
 * reason for existing.
 *
 * <p>A heterozygous indel does not appear as a gap in the alignment. Both
 * alleles are sequenced together, so the trace reads cleanly up to the indel
 * and then becomes a superposition of two out-of-register sequences: every
 * base downstream shows two peaks. {@code HeteroTrace} finds where that starts,
 * subtracts the reference-matching strand to recover the other allele, and
 * infers the indel from how far the two are shifted apart.
 *
 * <p>These tests build exactly that signal: allele 1 is the reference, allele 2
 * carries the indel, and the synthesised chromatogram superimposes them.
 */
class HeteroIndelTest {

	private static final String REFERENCE =
			("ATGGCTAGCTAGGCATCGATCGTAGCTAGCATCGGATCCTTAAGGCCTTAAGGCCAATTGG"
			+ "CATCGATCGGATCCAAGCTTGGATCCTTAAGGCCAATTCCGGAATTCCGGTTAACCGGTTA"
			+ "ACGTACGTACGTGGCCTTAAGGCCAATTGGCCTTAAGGCCAATTGGCCTTAAGGCCAATTG"
			+ "GCATCGATCGATCGTAGCTAGCTAGCTAGCATCGATCGATCGTAGCTAGCTAGCTAGCATC"
			+ "GATCGATCGTAGCTAGCTAGCTAGCATCGATCGATCGTAGCTAGCTAGCTAGCATCGATCG");

	private static final int MARGIN = 15;

	private record Result(HeteroTrace heteroTrace, Variant variant) {
	}

	/**
	 * Runs a two-allele trace through the whole path a real run takes:
	 * alignment, cDNA numbering, ambiguity coding, then indel deconvolution.
	 *
	 * @param allele2 the full second-allele sequence (allele 1 is the reference)
	 */
	private static Result analyse(Path dir, String allele2) throws Exception {
		Path fasta = dir.resolve("ref.fasta");
		Files.writeString(fasta, ">synthetic reference\n" + REFERENCE + "\n");
		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);

		// Both alleles are read together from the same primer.
		String read1 = REFERENCE.substring(MARGIN, REFERENCE.length() - MARGIN);
		String read2 = allele2.substring(MARGIN, MARGIN + read1.length());

		Path ab1 = dir.resolve("fwd.ab1");
		SyntheticTrace.write(ab1, read1, read2, 55);

		RootController controller = new RootController();
		controller.reference = reference;

		GanseqTrace trace = new GanseqTrace(ab1.toFile(), controller);
		// Turns each double peak into an IUPAC ambiguity code.
		trace.applyAmbiguousSymbol();

		MMAlignment mma = new MMAlignment(RootController.defaultGOP);
		AlignedPair pair = mma.localAlignment(reference.getRefString(), trace.getSequence());

		Formatter formatter = new Formatter(1);
		Vector<AlignedPoint> alignedPoints =
				formatter.format2(pair, reference, trace, GanseqTrace.FORWARD);

		// setRange() equivalent: the span of the trace that aligned.
		int firstAligned = Integer.MAX_VALUE;
		int lastAligned = 0;
		for (AlignedPoint ap : alignedPoints) {
			if (ap.getFwdChar() != Formatter.gapChar) {
				firstAligned = Math.min(firstAligned, ap.getFwdTraceIndex());
				lastAligned = Math.max(lastAligned, ap.getFwdTraceIndex());
			}
		}
		trace.setAlignedRegionStart(firstAligned);
		trace.setAlignedRegionEnd(lastAligned);

		controller.formatter = formatter;
		controller.alignedPoints = alignedPoints;
		controller.trimmedFwdTrace = trace;
		controller.fwdLoaded = true;
		controller.revLoaded = false;
		controller.startRange = 1;
		controller.endRange = alignedPoints.size();
		controller.alignmentPerformed = true;

		HeteroTrace heteroTrace = new HeteroTrace(trace, controller);
		return new Result(heteroTrace, heteroTrace.detectHeteroIndel());
	}

	/**
	 * Same as {@link #analyse}, but for a trace read from the opposite primer.
	 *
	 * <p>A reverse read is in register with the reference at its 3' end and falls
	 * out of register going leftwards past the indel — the mirror image of a
	 * forward read — so allele 2 is aligned by its right end here. The trace is
	 * synthesised in the orientation the instrument sees it and then
	 * reverse-complemented, which is the path a real reverse trace takes and
	 * what puts {@code HeteroTrace} on its {@code direction == -1} branch.
	 */
	private static Result analyseReverse(Path dir, String allele2) throws Exception {
		Path fasta = dir.resolve("ref.fasta");
		Files.writeString(fasta, ">synthetic reference\n" + REFERENCE + "\n");
		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);

		String read1 = REFERENCE.substring(MARGIN, REFERENCE.length() - MARGIN);
		int shift = REFERENCE.length() - allele2.length(); // >0 deletion, <0 insertion
		StringBuilder sb = new StringBuilder();
		for (int i = MARGIN; i < REFERENCE.length() - MARGIN; i++) {
			int j = i - shift;
			sb.append((j >= 0 && j < allele2.length()) ? allele2.charAt(j) : REFERENCE.charAt(i));
		}
		String read2 = sb.toString();

		Path ab1 = dir.resolve("rev.ab1");
		SyntheticTrace.write(ab1,
				SymbolTools.getComplementString(read1),
				SymbolTools.getComplementString(read2), 55);

		RootController controller = new RootController();
		controller.reference = reference;

		GanseqTrace trace = new GanseqTrace(ab1.toFile(), controller);
		trace.applyAmbiguousSymbol();
		trace.makeComplement(); // -> direction REVERSE, sequence in reference orientation

		MMAlignment mma = new MMAlignment(RootController.defaultGOP);
		AlignedPair pair = mma.localAlignment(reference.getRefString(), trace.getSequence());

		Formatter formatter = new Formatter(1);
		Vector<AlignedPoint> alignedPoints =
				formatter.format2(pair, reference, trace, GanseqTrace.REVERSE);

		int firstAligned = Integer.MAX_VALUE;
		int lastAligned = 0;
		for (AlignedPoint ap : alignedPoints) {
			if (ap.getRevChar() != Formatter.gapChar) {
				firstAligned = Math.min(firstAligned, ap.getRevTraceIndex());
				lastAligned = Math.max(lastAligned, ap.getRevTraceIndex());
			}
		}
		trace.setAlignedRegionStart(firstAligned);
		trace.setAlignedRegionEnd(lastAligned);

		controller.formatter = formatter;
		controller.alignedPoints = alignedPoints;
		controller.trimmedRevTrace = trace;
		controller.fwdLoaded = false;
		controller.revLoaded = true;
		controller.startRange = 1;
		controller.endRange = alignedPoints.size();
		controller.alignmentPerformed = true;

		HeteroTrace heteroTrace = new HeteroTrace(trace, controller);
		return new Result(heteroTrace, heteroTrace.detectHeteroIndel());
	}

	private static String withDeletion(int oneBasedStart, int length) {
		return REFERENCE.substring(0, oneBasedStart - 1) + REFERENCE.substring(oneBasedStart - 1 + length);
	}

	private static String withInsertion(int afterOneBasedPos, String inserted) {
		return REFERENCE.substring(0, afterOneBasedPos) + inserted + REFERENCE.substring(afterOneBasedPos);
	}

	@Test
	@DisplayName("a clean homozygous trace reports no heterozygous indel")
	void noIndelOnACleanTrace(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, REFERENCE);

		assertNull(result.variant(),
				"a trace with no double peaks must not produce a hetero indel call");
	}

	@Test
	@DisplayName("a heterozygous single-base deletion is detected")
	void detectsSingleBaseDeletion(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, withDeletion(150, 1));

		assertNotNull(result.variant(), "no hetero indel detected");
		assertEquals(1, result.heteroTrace().getIndelSize());
		assertEquals(-1, result.heteroTrace().getInsOrDel(), "should be called as a deletion");
	}

	@Test
	@DisplayName("a heterozygous multi-base deletion is detected at the right size")
	void detectsMultiBaseDeletion(@TempDir Path dir) throws Exception {
		for (int size : new int[] { 2, 3, 5, 8 }) {
			Result result = analyse(dir, withDeletion(150, size));

			assertNotNull(result.variant(), "no call for a " + size + " bp deletion");
			assertEquals(size, result.heteroTrace().getIndelSize(),
					"wrong size for a " + size + " bp deletion");
			assertEquals(-1, result.heteroTrace().getInsOrDel(),
					"a " + size + " bp deletion was not called as a deletion");
		}
	}

	@Test
	@DisplayName("a heterozygous insertion is detected at the right size")
	void detectsInsertion(@TempDir Path dir) throws Exception {
		for (String inserted : new String[] { "T", "GA", "CCTA" }) {
			Result result = analyse(dir, withInsertion(150, inserted));

			assertNotNull(result.variant(), "no call for a " + inserted.length() + " bp insertion");
			assertEquals(inserted.length(), result.heteroTrace().getIndelSize(),
					"wrong size for insertion " + inserted);
			assertEquals(1, result.heteroTrace().getInsOrDel(),
					"insertion " + inserted + " was not called as an insertion");
		}
	}

	@Test
	@DisplayName("the call is a hetero Indel carrying usable HGVS")
	void producesUsableHgvs(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, withDeletion(150, 2));
		Variant variant = result.variant();

		assertNotNull(variant);
		assertTrue(variant instanceof Indel, "expected an Indel, got " + variant.getClass().getSimpleName());
		assertEquals("hetero", variant.getZygosity());

		String hgvs = variant.getHGVS();
		assertNotNull(hgvs);
		assertTrue(hgvs.startsWith("c."), "not cDNA-numbered: " + hgvs);
		assertTrue(hgvs.contains("del") || hgvs.contains("dup") || hgvs.contains("ins"),
				"no indel operation in " + hgvs);
		// Regression: the Indel constructor's null guard assigned the parameter
		// rather than the field, producing "insnull".
		assertTrue(!hgvs.contains("null"), "malformed HGVS: " + hgvs);
	}

	@Test
	@DisplayName("the deconvoluted strands are recovered and are the right length")
	void recoversBothStrands(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, withDeletion(150, 3));
		HeteroTrace hetero = result.heteroTrace();

		assertNotNull(result.variant());
		assertNotNull(hetero.getRefSeq(), "reference strand not recovered");
		assertNotNull(hetero.getSubtractedSeq(), "subtracted strand not recovered");

		// The two subtracted regions together account for every base in the trace.
		int total = hetero.getSubtractedSeq().length + hetero.getSubtractedSeq2().length;
		assertEquals(hetero.getSequenceLength(), total,
				"the double-peak and single-peak regions must partition the read");
	}

	@Test
	@DisplayName("the indel is placed inside the aligned region")
	void indelIsPlacedInsideTheAlignment(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, withDeletion(150, 3));
		HeteroTrace hetero = result.heteroTrace();

		assertNotNull(result.variant());
		int start = hetero.getAlignedIndelStartIndex();
		int end = hetero.getAlignedIndelEndIndex();
		int doublePeak = hetero.getAlignedDoublePeakStartIndex();

		assertTrue(start >= 1, "indel start fell outside the alignment: " + start);
		assertTrue(end >= start, "indel end precedes its start: " + start + ".." + end);
		assertTrue(doublePeak >= 1, "double-peak start fell outside the alignment");
	}

	/**
	 * Applies the reported call to the reference and checks it reproduces the
	 * allele that was sequenced.
	 *
	 * <p>Stronger than comparing against an expected HGVS string: a variant in a
	 * repeat has several equally valid representations, and HGVS mandates the
	 * 3'-most one, so string equality would fail on correct output.
	 */
	private static void assertReproducesAllele(Variant variant, String allele2, String label) {
		assertNotNull(variant, "no call for " + label);
		String hgvs = variant.getHGVS();
		String rebuilt = HgvsApplier.apply(REFERENCE, hgvs);
		assertNotNull(rebuilt, "unparseable HGVS for " + label + ": " + hgvs);
		assertEquals(allele2, rebuilt,
				label + ": " + hgvs + " does not reproduce the sequenced allele");
	}

	@Test
	@DisplayName("a called deletion, applied to the reference, reproduces the allele")
	void deletionCallsAreCorrect(@TempDir Path dir) throws Exception {
		for (int size : new int[] { 1, 2, 3, 5, 8 }) {
			String allele2 = withDeletion(150, size);
			Result result = analyse(dir, allele2);
			assertReproducesAllele(result.variant(), allele2, size + " bp deletion");
		}
	}

	@Test
	@DisplayName("a called insertion, applied to the reference, reproduces the allele")
	void insertionCallsAreCorrect(@TempDir Path dir) throws Exception {
		for (String inserted : new String[] { "T", "GA", "CCTA", "TTGAC" }) {
			String allele2 = withInsertion(150, inserted);
			Result result = analyse(dir, allele2);
			assertReproducesAllele(result.variant(), allele2, "insertion of " + inserted);
		}
	}

	@Test
	@DisplayName("a deletion inside a repeat is reported 3'-most, as HGVS requires")
	void deletionInRepeatIsRightAligned(@TempDir Path dir) throws Exception {
		// This region is a 4-periodic TAGC repeat, so an 8 bp deletion has many
		// equally valid placements and HGVS mandates the rightmost.
		String allele2 = withDeletion(200, 8);
		Result result = analyse(dir, allele2);
		Variant variant = result.variant();
		assertReproducesAllele(variant, allele2, "8 bp deletion in a repeat");

		Indel indel = (Indel) variant;
		int reportedEnd = indel.getgIndex2();
		for (EquivExpression equivalent : indel.getEquivExpressionList()) {
			assertTrue(equivalent.getgIndex2() <= reportedEnd,
					"a further-right equivalent existed but was not reported: " + equivalent.getHGVS());
		}
	}

	@Test
	@DisplayName("every listed equivalent expression is genuinely equivalent")
	void equivalentExpressionsAreAllValid(@TempDir Path dir) throws Exception {
		String allele2 = withDeletion(200, 8);
		Result result = analyse(dir, allele2);
		Indel indel = (Indel) result.variant();
		assertNotNull(indel);

		int checked = 0;
		for (EquivExpression equivalent : indel.getEquivExpressionList()) {
			String rebuilt = HgvsApplier.apply(REFERENCE, equivalent.getHGVS());
			assertNotNull(rebuilt, "unparseable equivalent: " + equivalent.getHGVS());
			assertEquals(allele2, rebuilt,
					"listed as equivalent but is not: " + equivalent.getHGVS());
			checked++;
		}
		assertTrue(checked > 1, "expected several equivalents inside a repeat, got " + checked);
	}

	@Test
	@DisplayName("an insertion duplicating adjacent sequence is reported as a duplication")
	void insertionIntoARunIsCalledDuplication(@TempDir Path dir) throws Exception {
		// c.149 and c.150 are both T. HGVS requires an insertion that repeats
		// adjacent identical sequence to be described as a duplication.
		String allele2 = withInsertion(150, "T");
		Result result = analyse(dir, allele2);

		Indel indel = (Indel) result.variant();
		assertNotNull(indel);
        assertEquals(Indel.duplication, indel.getType(),
                "expected a duplication, got " + indel.getHGVS());
		assertTrue(indel.getHGVS().contains("dup"), indel.getHGVS());
		assertReproducesAllele(indel, allele2, "duplication");
	}

	@Test
	@DisplayName("insertion HGVS states the c. prefix once")
	void insertionHgvsIsWellFormed(@TempDir Path dir) throws Exception {
		// The insertion branch used to skip the prefix strip applied by every
		// other branch, emitting "c.150_c.151insCCTA", which is not valid HGVS.
		String allele2 = withInsertion(150, "CCTA");
		Result result = analyse(dir, allele2);
		String hgvs = result.variant().getHGVS();

		assertEquals(1, hgvs.split("c\\.", -1).length - 1,
				"the c. prefix should appear exactly once: " + hgvs);
		assertTrue(hgvs.matches("c\\.\\d+_\\d+ins[ACGT]+"), "malformed insertion HGVS: " + hgvs);
	}

	@Test
	@DisplayName("a reverse-strand deletion is detected and reproduces the allele")
	void reverseStrandDeletion(@TempDir Path dir) throws Exception {
		// Exercises HeteroTrace's direction == -1 branch, which is a separate
		// implementation from the forward one.
		for (int size : new int[] { 1, 3, 5 }) {
			String allele2 = withDeletion(150, size);
			Result result = analyseReverse(dir, allele2);
			assertNotNull(result.variant(), "no reverse call for a " + size + " bp deletion");
			assertEquals(GanseqTrace.REVERSE, result.variant().getDirection());
			assertEquals(size, result.heteroTrace().getIndelSize());
			assertReproducesAllele(result.variant(), allele2, "reverse " + size + " bp deletion");
		}
	}

	@Test
	@DisplayName("a reverse-strand insertion is detected and reproduces the allele")
	void reverseStrandInsertion(@TempDir Path dir) throws Exception {
		for (String inserted : new String[] { "GA", "CCTA" }) {
			String allele2 = withInsertion(150, inserted);
			Result result = analyseReverse(dir, allele2);
			assertNotNull(result.variant(), "no reverse call for insertion " + inserted);
			assertEquals(GanseqTrace.REVERSE, result.variant().getDirection());
			assertReproducesAllele(result.variant(), allele2, "reverse insertion " + inserted);
		}
	}

	@Test
	@DisplayName("a clean reverse-strand trace reports no heterozygous indel")
	void reverseStrandCleanTrace(@TempDir Path dir) throws Exception {
		Result result = analyseReverse(dir, REFERENCE);
		assertNull(result.variant(), "a clean reverse trace must not produce a call");
	}

	@Test
	@DisplayName("forward and reverse report the same insertion identically")
	void strandsAgreeOnInsertionHgvs(@TempDir Path dir) throws Exception {
		// The two strands recover different rotations of the same inserted
		// sequence. Strand merging compares HGVS by string equality, so unless
		// both are normalised to the same canonical form the variant is listed
		// twice instead of once with a frequency of 2.
		for (String inserted : new String[] { "GA", "CCTA", "TTGAC", "AG" }) {
			String allele2 = withInsertion(150, inserted);

			String forward = analyse(dir, allele2).variant().getHGVS();
			String reverse = analyseReverse(dir, allele2).variant().getHGVS();

			assertEquals(forward, reverse,
					"strands disagree for insertion " + inserted + "; they would not merge");
		}
	}

	@Test
	@DisplayName("insertions are reported 3'-most, as HGVS requires")
	void insertionsAre3PrimeNormalised(@TempDir Path dir) throws Exception {
		Pattern insertion = Pattern.compile("^c\\.(\\d+)_(\\d+)ins([ACGT]+)$");

		for (String inserted : new String[] { "GA", "CCTA", "TTGAC", "AG", "GGCC" }) {
			String allele2 = withInsertion(150, inserted);
			String hgvs = analyse(dir, allele2).variant().getHGVS();

			Matcher m = insertion.matcher(hgvs);
			if (!m.matches()) {
				// Reported as a duplication, which is itself a 3' rule outcome.
				assertTrue(hgvs.contains("dup"), "unexpected form: " + hgvs);
				continue;
			}
			int after = Integer.parseInt(m.group(1));
			String seq = m.group(3);

			// An insertion can shift one base right exactly when its first base
			// equals the reference base that follows it. If that holds, a more
			// 3' representation existed and should have been reported instead.
			char nextRefBase = REFERENCE.charAt(after); // 1-based `after` -> next base
			assertTrue(seq.charAt(0) != nextRefBase,
					hgvs + " is not 3'-most: it can still shift right past '" + nextRefBase + "'");
		}
	}

	@Test
	@DisplayName("an insertion duplicating the preceding bases is reported as a duplication")
	void insertionMatchingPrecedingBasesBecomesDuplication(@TempDir Path dir) throws Exception {
		// HGVS: "insertions that duplicate a copy of the directly preceding
		// sequence should be described as duplications."
		String preceding = REFERENCE.substring(147, 150); // c.148-150
		String allele2 = withInsertion(150, preceding);

		Indel indel = (Indel) analyse(dir, allele2).variant();
		assertNotNull(indel);
		assertTrue(indel.getHGVS().contains("dup"),
				"a duplicated insertion should be reported as dup, got " + indel.getHGVS());
		assertReproducesAllele(indel, allele2, "duplication of " + preceding);
	}

	@Test
	@DisplayName("rotated equivalents of an insertion are listed and are genuinely equivalent")
	void rotatedEquivalentsAreValid(@TempDir Path dir) throws Exception {
		String allele2 = withInsertion(150, "GA");
		Indel indel = (Indel) analyse(dir, allele2).variant();
		assertNotNull(indel);

		int checked = 0;
		for (EquivExpression equivalent : indel.getEquivExpressionList()) {
			String rebuilt = HgvsApplier.apply(REFERENCE, equivalent.getHGVS());
			assertNotNull(rebuilt, "unparseable equivalent: " + equivalent.getHGVS());
			assertEquals(allele2, rebuilt, "listed as equivalent but is not: " + equivalent.getHGVS());
			checked++;
		}
		assertTrue(checked >= 2,
				"the rotated placement should be listed alongside the canonical one, got " + checked);
	}

	@Test
	@DisplayName("the heterozygous indel view renders")
	void heteroViewRenders(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, withDeletion(150, 3));
		assertNotNull(result.variant());

		java.awt.image.BufferedImage image = result.heteroTrace().getHeteroImage(
				new Formatter(1), new java.util.TreeSet<>(), new java.util.TreeSet<>());

		assertNotNull(image);
		assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
		assertEquals(java.awt.image.BufferedImage.TYPE_INT_RGB, image.getType());
	}
}
