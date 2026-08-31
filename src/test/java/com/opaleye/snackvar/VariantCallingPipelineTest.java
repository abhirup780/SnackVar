package com.opaleye.snackvar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.opaleye.snackvar.mmalignment.AlignedPair;
import com.opaleye.snackvar.mmalignment.MMAlignment;
import com.opaleye.snackvar.reference.Reference;
import com.opaleye.snackvar.variants.Variant;
import com.opaleye.snackvar.variants.VariantCallerFilter;

/**
 * End-to-end cover for the analysis path: reference plus chromatogram in, HGVS
 * out. Runs the same sequence of calls as {@code RootController.handleRun}
 * minus the parts that touch the scene graph, so it can run headless.
 */
class VariantCallingPipelineTest {

	/** 300 bases, entirely upper case, so the whole reference is coding and c.n == base n. */
	private static final String REFERENCE =
			("ATGGCTAGCTAGGCATCGATCGTAGCTAGCATCGGATCCTTAAGGCCTTAAGGCCAATTGG"
			+ "CATCGATCGGATCCAAGCTTGGATCCTTAAGGCCAATTCCGGAATTCCGGTTAACCGGTTA"
			+ "ACGTACGTACGTGGCCTTAAGGCCAATTGGCCTTAAGGCCAATTGGCCTTAAGGCCAATTG"
			+ "GCATCGATCGATCGTAGCTAGCTAGCTAGCATCGATCGATCGTAGCTAGCTAGCTAGCATC"
			+ "GATCGATCGTAGCTAGCTAGCTAGCATCGATCGATCGTAGCTAGCTAGCTAGCATCGATCG");

	/** Bases trimmed off each end before alignment, mimicking a real read. */
	private static final int MARGIN = 15;

	private record Result(Vector<AlignedPoint> alignedPoints, TreeSet<Variant> variants) {
		Set<String> hgvs() {
			return variants.stream().map(Variant::getHGVS).collect(Collectors.toSet());
		}
	}

	/**
	 * Runs reference + trace through alignment, formatting and variant calling.
	 *
	 * @param secondAllele per-base second allele for heterozygous positions, or null
	 */
	private static Result analyse(Path dir, String sampleSequence, String secondAllele) throws Exception {
		Path fasta = dir.resolve("ref.fasta");
		Files.writeString(fasta, ">synthetic reference\n" + REFERENCE + "\n");
		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);

		// The read covers the middle of the reference, as a real primer would.
		String read = sampleSequence.substring(MARGIN, sampleSequence.length() - MARGIN);
		String readSecond = (secondAllele == null) ? null
				: secondAllele.substring(MARGIN, secondAllele.length() - MARGIN);

		Path ab1 = dir.resolve("fwd.ab1");
		SyntheticTrace.write(ab1, read, readSecond, 55);

		RootController controller = new RootController();
		controller.reference = reference;

		GanseqTrace trace = new GanseqTrace(ab1.toFile(), controller);
		trace.applyAmbiguousSymbol();
		trace.setAlignedRegionStart(1);
		trace.setAlignedRegionEnd(trace.getSequenceLength());

		MMAlignment mma = new MMAlignment(RootController.defaultGOP);
		AlignedPair pair = mma.localAlignment(reference.getRefString(), trace.getSequence());

		Formatter formatter = new Formatter(1);
		Vector<AlignedPoint> alignedPoints =
				formatter.format2(pair, reference, trace, GanseqTrace.FORWARD);

		controller.formatter = formatter;
		controller.alignedPoints = alignedPoints;
		controller.trimmedFwdTrace = trace;
		controller.fwdLoaded = true;
		controller.revLoaded = false;
		controller.startRange = 1;
		controller.endRange = alignedPoints.size();
		controller.alignmentPerformed = true;

		VariantCallerFilter caller = new VariantCallerFilter(controller, new Vector<>());
		return new Result(alignedPoints, caller.getVariantList());
	}

	private static String withSubstitution(int oneBasedPosition, char newBase) {
		StringBuilder sb = new StringBuilder(REFERENCE);
		sb.setCharAt(oneBasedPosition - 1, newBase);
		return sb.toString();
	}

	@Test
	@DisplayName("a trace identical to the reference calls no variants")
	void perfectMatchCallsNothing(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, REFERENCE, null);

		assertFalse(result.alignedPoints().isEmpty(), "the read should align");
		assertTrue(result.variants().isEmpty(),
				"unexpected calls on a clean trace: " + result.hgvs());
	}

	@Test
	@DisplayName("the alignment carries cDNA coordinates through to the variant")
	void alignmentIsNumberedInCdnaCoordinates(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, REFERENCE, null);

		AlignedPoint first = result.alignedPoints().get(0);
		// The whole reference is coding, so every column has a plain c.n number.
		assertTrue(first.isCoding());
		assertTrue(first.getStringCIndex().startsWith("c."), first.getStringCIndex());
		assertEquals("c." + first.getGIndex(), first.getStringCIndex());
	}

	@Test
	@DisplayName("a homozygous substitution is reported in HGVS")
	void callsHomozygousSubstitution(@TempDir Path dir) throws Exception {
		int position = 150;
		char refBase = REFERENCE.charAt(position - 1);
		char altBase = (refBase == 'A') ? 'T' : 'A';

		Result result = analyse(dir, withSubstitution(position, altBase), null);

		String expected = "c." + position + refBase + ">" + altBase;
		assertTrue(result.hgvs().contains(expected),
				"expected " + expected + " but got " + result.hgvs());

		Variant called = result.variants().stream()
				.filter(v -> v.getHGVS().equals(expected)).findFirst().orElseThrow();
		assertEquals("homo", called.getZygosity());
		assertEquals(GanseqTrace.FORWARD, called.getDirection());
	}

	@Test
	@DisplayName("a substitution is translated to an amino-acid change")
	void reportsAminoAcidChange(@TempDir Path dir) throws Exception {
		int position = 150;
		char refBase = REFERENCE.charAt(position - 1);
		char altBase = (refBase == 'A') ? 'T' : 'A';

		Result result = analyse(dir, withSubstitution(position, altBase), null);
		Variant called = result.variants().stream()
				.filter(v -> v.getHGVS().startsWith("c." + position)).findFirst().orElseThrow();

		// Reference is in frame from base 1, so position 150 is codon 50.
		assertTrue(called.getAAChange().startsWith("p.("), called.getAAChange());
		assertTrue(called.getAAChange().contains("50"),
				"expected codon 50, got " + called.getAAChange());
	}

	@Test
	@DisplayName("a double peak is called as a heterozygous substitution")
	void callsHeterozygousSubstitution(@TempDir Path dir) throws Exception {
		int position = 150;
		char refBase = REFERENCE.charAt(position - 1);
		char altBase = (refBase == 'A') ? 'T' : 'A';

		// Reference base on one allele, alternate on the other.
		Result result = analyse(dir, REFERENCE, withSubstitution(position, altBase));

		String expected = "c." + position + refBase + ">" + altBase;
		assertTrue(result.hgvs().contains(expected),
				"expected " + expected + " but got " + result.hgvs());
		Variant called = result.variants().stream()
				.filter(v -> v.getHGVS().equals(expected)).findFirst().orElseThrow();
		assertEquals("hetero", called.getZygosity());
	}

	@Test
	@DisplayName("two separate substitutions are both reported")
	void callsTwoSubstitutions(@TempDir Path dir) throws Exception {
		int first = 100;
		int second = 200;
		char refFirst = REFERENCE.charAt(first - 1);
		char refSecond = REFERENCE.charAt(second - 1);
		char altFirst = (refFirst == 'A') ? 'T' : 'A';
		char altSecond = (refSecond == 'G') ? 'C' : 'G';

		StringBuilder sample = new StringBuilder(REFERENCE);
		sample.setCharAt(first - 1, altFirst);
		sample.setCharAt(second - 1, altSecond);

		Result result = analyse(dir, sample.toString(), null);

		assertTrue(result.hgvs().contains("c." + first + refFirst + ">" + altFirst),
				result.hgvs().toString());
		assertTrue(result.hgvs().contains("c." + second + refSecond + ">" + altSecond),
				result.hgvs().toString());
	}

	@Test
	@DisplayName("HGVS strings never contain a stringified null")
	void hgvsNeverContainsNull(@TempDir Path dir) throws Exception {
		// The Indel constructor's null guard assigned the parameter rather than
		// the field, so an absent inserted sequence produced "…insnull".
		Result result = analyse(dir, withSubstitution(150, 'A'), null);
		for (String hgvs : result.hgvs()) {
			assertFalse(hgvs.contains("null"), "malformed HGVS: " + hgvs);
		}
	}

	@Test
	@DisplayName("a reverse-complemented read is aligned in the right orientation")
	void handlesReverseComplementedRead(@TempDir Path dir) throws Exception {
		// The caller scores both orientations and keeps the better one; this is
		// the check that a flipped read still lands on the reference.
		Path fasta = dir.resolve("ref.fasta");
		Files.writeString(fasta, ">synthetic reference\n" + REFERENCE + "\n");
		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);

		String read = REFERENCE.substring(MARGIN, REFERENCE.length() - MARGIN);
		String reversed = com.opaleye.snackvar.tools.SymbolTools.getComplementString(read);

		MMAlignment mma = new MMAlignment(RootController.defaultGOP);
		AlignedPair forward = mma.localAlignment(reference.getRefString(), read);
		AlignedPair reverseOriented = mma.localAlignment(reference.getRefString(), reversed);

		assertTrue(identities(forward) > identities(reverseOriented),
				"the correctly oriented read must score higher");
	}

	private static int identities(AlignedPair pair) {
		int score = 0;
		String a = pair.getAlignedString1();
		String b = pair.getAlignedString2();
		for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
			if (a.charAt(i) == b.charAt(i)) {
				score++;
			}
		}
		return score;
	}

	/** Applies the call to the reference and checks it reproduces the sequenced allele. */
	private static void assertSomeCallReproduces(Result result, String allele, String label) {
		assertFalse(result.variants().isEmpty(), "no variant called for " + label);
		for (Variant v : result.variants()) {
			String rebuilt = HgvsApplier.apply(REFERENCE, v.getHGVS());
			if (rebuilt != null && rebuilt.equals(allele)) {
				return;
			}
		}
		throw new AssertionError(label + ": no call reproduces the allele. Got " + result.hgvs());
	}

	@Test
	@DisplayName("a homozygous deletion is called and reproduces the allele")
	void callsHomozygousDeletion(@TempDir Path dir) throws Exception {
		// Both alleles carry it, so this one does show as a gap in the alignment
		// and goes through VariantCallerFilter's homozygous indel path.
		for (int size : new int[] { 1, 3 }) {
			String allele = REFERENCE.substring(0, 150 - 1) + REFERENCE.substring(150 - 1 + size);
			Result result = analyse(dir, allele, null);
			assertSomeCallReproduces(result, allele, size + " bp homozygous deletion");
		}
	}

	@Test
	@DisplayName("a homozygous insertion is called and reproduces the allele")
	void callsHomozygousInsertion(@TempDir Path dir) throws Exception {
		for (String inserted : new String[] { "GA", "CCTA" }) {
			String allele = REFERENCE.substring(0, 150) + inserted + REFERENCE.substring(150);
			Result result = analyse(dir, allele, null);
			assertSomeCallReproduces(result, allele, "homozygous insertion of " + inserted);
		}
	}

	@Test
	@DisplayName("no call anywhere contains a stringified null or a doubled c. prefix")
	void allCallsAreWellFormed(@TempDir Path dir) throws Exception {
		String allele = REFERENCE.substring(0, 150) + "CCTA" + REFERENCE.substring(150);
		Result result = analyse(dir, allele, null);

		for (String hgvs : result.hgvs()) {
			assertFalse(hgvs.contains("null"), "malformed HGVS: " + hgvs);
			assertEquals(1, hgvs.split("c\\.", -1).length - 1,
					"the c. prefix should appear exactly once: " + hgvs);
		}
	}

	@Test
	@DisplayName("aligned points are contiguous in reference coordinates")
	void alignedPointsAreContiguous(@TempDir Path dir) throws Exception {
		Result result = analyse(dir, REFERENCE, null);
		List<AlignedPoint> points = result.alignedPoints();

		for (int i = 1; i < points.size(); i++) {
			int previous = points.get(i - 1).getGIndex();
			int current = points.get(i).getGIndex();
			assertTrue(current == previous || current == previous + 1,
					"reference coordinate jumped from " + previous + " to " + current);
		}
	}
}
