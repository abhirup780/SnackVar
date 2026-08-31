package com.opaleye.snackvar.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceTest {

	/**
	 * SnackVar's FASTA convention: upper case marks coding sequence, lower case
	 * marks everything else, and the coding blocks are recorded as 1-based
	 * inclusive ranges.
	 */
	@Test
	@DisplayName("upper-case runs are recorded as coding regions")
	void readsCodingRegionsFromCase(@TempDir Path dir) throws Exception {
		Path fasta = dir.resolve("test.fasta");
		Files.writeString(fasta, ">test sequence\nttttATGAAATTTtttt\n");

		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);

		assertEquals("ttttATGAAATTTtttt".toUpperCase(), reference.getRefString().toUpperCase());
		assertEquals(1, reference.getcDnaStart().size());
		assertEquals(5, reference.getcDnaStart().get(0), "coding starts at base 5");
		assertEquals(13, reference.getcDnaEnd().get(0), "coding ends at base 13");
	}

	@Test
	@DisplayName("several coding blocks are recorded in order")
	void readsMultipleCodingBlocks(@TempDir Path dir) throws Exception {
		Path fasta = dir.resolve("multi.fasta");
		Files.writeString(fasta, ">multi\naaAAAaaaCCCaa\n");

		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);

		assertEquals(2, reference.getcDnaStart().size());
		assertEquals(3, reference.getcDnaStart().get(0));
		assertEquals(5, reference.getcDnaEnd().get(0));
		assertEquals(9, reference.getcDnaStart().get(1));
		assertEquals(11, reference.getcDnaEnd().get(1));
	}

	@Test
	@DisplayName("a FASTA with no coding region is rejected with a usable message")
	void rejectsFastaWithoutCodingRegion(@TempDir Path dir) throws Exception {
		Path fasta = dir.resolve("nocoding.fasta");
		Files.writeString(fasta, ">no coding\nacgtacgtacgt\n");

		Exception ex = assertThrows(Exception.class,
				() -> new Reference(fasta.toFile(), Reference.FASTA));
		assertTrue(ex.getMessage().contains("coding region"), ex.getMessage());
	}

	@Test
	@DisplayName("the reference remembers the file it came from")
	void keepsTheFileName(@TempDir Path dir) throws Exception {
		Path fasta = dir.resolve("NM_000546.5(TP53).fasta");
		Files.writeString(fasta, ">TP53\nttATGtt\n");

		Reference reference = new Reference(fasta.toFile(), Reference.FASTA);
		assertEquals("NM_000546.5(TP53).fasta", reference.getRefName());
	}

	@Test
	@DisplayName("a missing file surfaces as an error rather than a null reference")
	void missingFileIsAnError(@TempDir Path dir) {
		File missing = dir.resolve("absent.fasta").toFile();
		assertThrows(Exception.class, () -> new Reference(missing, Reference.FASTA));
	}

	@Test
	@DisplayName("bundled reference sequences parse")
	void bundledReferencesParse() throws Exception {
		// Guards the reference set shipped in reference/ against a bad copy.
		File dir = new File("reference");
		if (!dir.isDirectory()) {
			return; // reference set not present in this checkout
		}

		for (String name : List.of("NM_000546.5(TP53)", "NM_007294.4(BRCA1)", "NM_000492.4(CFTR)")) {
			File file = new File(dir, name + ".fasta");
			if (!file.isFile()) {
				continue;
			}
			Reference reference = new Reference(file, Reference.FASTA);
			assertTrue(reference.getRefString().length() > 1000, name + " is too short");
			assertTrue(reference.getcDnaStart().size() >= 1, name + " has no coding region");
			assertTrue(reference.getcDnaEnd().size() == reference.getcDnaStart().size(),
					name + " has mismatched coding-region bounds");
		}
	}

	@Test
	@DisplayName("every bundled file is a .fasta and none is empty")
	void bundledSetIsWellFormed() {
		File dir = new File("reference");
		if (!dir.isDirectory()) {
			return;
		}
		File[] files = dir.listFiles();
		assertTrue(files != null && files.length > 0, "reference/ is empty");

		for (File file : files) {
			assertTrue(file.getName().endsWith(".fasta"), "unexpected file: " + file.getName());
			assertTrue(file.length() > 0, "empty sequence file: " + file.getName());
		}
	}
}
