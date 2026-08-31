package com.opaleye.snackvar.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

	private static final String PROPERTY = "snackvar.referenceDir";

	@AfterEach
	void clearOverride() {
		System.clearProperty(PROPERTY);
	}

	@Test
	@DisplayName("a missing reference directory yields an empty list, not a crash")
	void missingDirectoryIsEmpty(@TempDir Path dir) {
		// The original did `new File("./reference").list()` and dereferenced the
		// null that comes back when the directory is absent, which stopped the
		// main window from opening at all.
		System.setProperty(PROPERTY, dir.resolve("does-not-exist").toString());

		List<String> names = AppPaths.referenceNames();
		assertNotNull(names);
		assertTrue(names.isEmpty());
	}

	@Test
	@DisplayName("names drop the .fasta suffix and ignore other files")
	void listsFastaNamesOnly(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("NM_000546.5(TP53).fasta"), ">x\nACGT\n");
		Files.writeString(dir.resolve("NM_007294.4(BRCA1).fasta"), ">x\nACGT\n");
		Files.writeString(dir.resolve("README.txt"), "not a sequence");
		Files.createDirectory(dir.resolve("subdir"));
		System.setProperty(PROPERTY, dir.toString());

		List<String> names = AppPaths.referenceNames();

		assertEquals(2, names.size());
		assertTrue(names.contains("NM_000546.5(TP53)"));
		assertTrue(names.contains("NM_007294.4(BRCA1)"));
		assertFalse(names.contains("README.txt"));
	}

	@Test
	@DisplayName("names come back sorted so the autocomplete list is stable")
	void namesAreSorted(@TempDir Path dir) throws Exception {
		for (String name : List.of("zeta", "Alpha", "middle")) {
			Files.writeString(dir.resolve(name + ".fasta"), ">x\nACGT\n");
		}
		System.setProperty(PROPERTY, dir.toString());

		assertEquals(List.of("Alpha", "middle", "zeta"), AppPaths.referenceNames());
	}

	@Test
	@DisplayName("a named sequence resolves to its file")
	void resolvesNamedSequence(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("NM_000546.5(TP53).fasta"), ">x\nACGT\n");
		System.setProperty(PROPERTY, dir.toString());

		File file = AppPaths.referenceFile("NM_000546.5(TP53)");
		assertNotNull(file);
		assertTrue(file.isFile());
	}

	@Test
	@DisplayName("an unknown or blank name resolves to null rather than a bad path")
	void unknownNameResolvesToNull(@TempDir Path dir) {
		System.setProperty(PROPERTY, dir.toString());

		assertNull(AppPaths.referenceFile("no-such-gene"));
		assertNull(AppPaths.referenceFile(""));
		assertNull(AppPaths.referenceFile(null));
	}

	@Test
	@DisplayName("the terms of use are read from the jar, not the working directory")
	void termsAreOnTheClasspath() throws Exception {
		// The original opened "Terms_of_use.txt" relative to the process working
		// directory, so the dialog came up empty unless the app was launched
		// from its install folder.
		String terms = AppPaths.termsOfUse();
		assertNotNull(terms);
		assertTrue(terms.contains("Terms of Use"), terms);
	}

	@Test
	@DisplayName("the default chooser directory exists")
	void defaultChooserDirectoryExists() {
		File dir = AppPaths.defaultChooserDir();
		assertNotNull(dir);
		assertTrue(dir.isDirectory());
	}
}
