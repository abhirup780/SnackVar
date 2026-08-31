package com.opaleye.snackvar.variants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.biojava.bio.symbol.IllegalAlphabetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VariantTest {

	@Test
	@DisplayName("codons translate to the standard genetic code")
	void translatesStandardCodons() throws Exception {
		assertEquals("Met", Variant.getAAfromTriple("ATG"));
		assertEquals("Trp", Variant.getAAfromTriple("TGG"));
		assertEquals("Phe", Variant.getAAfromTriple("TTT"));
		assertEquals("Phe", Variant.getAAfromTriple("TTC"));
		assertEquals("Gly", Variant.getAAfromTriple("GGA"));
		assertEquals("Arg", Variant.getAAfromTriple("CGG"));
		assertEquals("Ser", Variant.getAAfromTriple("AGT"));
		assertEquals("Leu", Variant.getAAfromTriple("CTG"));
	}

	@Test
	@DisplayName("all three stop codons translate to *")
	void translatesStopCodons() throws Exception {
		assertEquals("*", Variant.getAAfromTriple("TAA"));
		assertEquals("*", Variant.getAAfromTriple("TAG"));
		assertEquals("*", Variant.getAAfromTriple("TGA"));
	}

	@Test
	@DisplayName("translation is case-insensitive")
	void translationIsCaseInsensitive() throws Exception {
		assertEquals("Met", Variant.getAAfromTriple("atg"));
		assertEquals("Met", Variant.getAAfromTriple("aTg"));
	}

	@Test
	@DisplayName("a codon containing an ambiguity code is rejected, not guessed")
	void rejectsAmbiguousCodons() {
		// The caller relies on this to label a variant "(untranslatable)"
		// rather than reporting a wrong amino acid.
		assertThrows(IllegalAlphabetException.class, () -> Variant.getAAfromTriple("ATN"));
		assertThrows(IllegalAlphabetException.class, () -> Variant.getAAfromTriple("NNN"));
	}

	@Test
	@DisplayName("every one of the 64 codons translates")
	void everyCodonIsCovered() throws Exception {
		String bases = "ACGT";
		int translated = 0;
		for (char a : bases.toCharArray()) {
			for (char b : bases.toCharArray()) {
				for (char c : bases.toCharArray()) {
					String codon = "" + a + b + c;
					String aa = Variant.getAAfromTriple(codon);
					if (aa == null || aa.isEmpty()) {
						throw new AssertionError("no translation for " + codon);
					}
					translated++;
				}
			}
		}
		assertEquals(64, translated);
	}
}
