package com.opaleye.snackvar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SymbolToolsTest {

	@Test
	@DisplayName("reverse complement flips the strand and the order")
	void reverseComplement() throws Exception {
		assertEquals("ACGT", SymbolTools.getComplementString("ACGT"));
		assertEquals("AACCGGTT", SymbolTools.getComplementString("AACCGGTT"));
		assertEquals("TTTTAAAA", SymbolTools.getComplementString("TTTTAAAA"));
	}

	@Test
	@DisplayName("two superimposed bases map to the right IUPAC code")
	void ambiguityCodes() {
		assertEquals('R', SymbolTools.makeAmbiguousSymbol('A', 'G'));
		assertEquals('R', SymbolTools.makeAmbiguousSymbol('G', 'A'));
		assertEquals('Y', SymbolTools.makeAmbiguousSymbol('C', 'T'));
		assertEquals('K', SymbolTools.makeAmbiguousSymbol('G', 'T'));
		assertEquals('M', SymbolTools.makeAmbiguousSymbol('A', 'C'));
		assertEquals('S', SymbolTools.makeAmbiguousSymbol('G', 'C'));
		assertEquals('W', SymbolTools.makeAmbiguousSymbol('A', 'T'));
	}

	@Test
	@DisplayName("ambiguity coding is order- and case-insensitive")
	void ambiguityCodesIgnoreCaseAndOrder() {
		assertEquals('Y', SymbolTools.makeAmbiguousSymbol('t', 'c'));
		assertEquals(SymbolTools.makeAmbiguousSymbol('A', 'G'),
				SymbolTools.makeAmbiguousSymbol('G', 'A'));
	}

	@Test
	@DisplayName("an unrepresentable pair falls back to N")
	void unknownPairBecomesN() {
		assertEquals('N', SymbolTools.makeAmbiguousSymbol('A', 'A'));
		assertEquals('N', SymbolTools.makeAmbiguousSymbol('A', 'N'));
	}

	@Test
	@DisplayName("IUPAC codes expand to the bases they stand for")
	void iupacExpansion() {
		assertEquals(List.of("A"), SymbolTools.IUPACtoSymbolList('A'));
		assertEquals(List.of("A", "G"), SymbolTools.IUPACtoSymbolList('R'));
		assertEquals(List.of("C", "T"), SymbolTools.IUPACtoSymbolList('Y'));
		assertEquals(List.of("A", "C", "G"), SymbolTools.IUPACtoSymbolList('V'));
		assertEquals(4, SymbolTools.IUPACtoSymbolList('N').size());
	}

	@Test
	@DisplayName("an unrecognised symbol expands to nothing")
	void unknownSymbolExpandsToEmpty() {
		Vector<String> expanded = SymbolTools.IUPACtoSymbolList('?');
		assertTrue(expanded.isEmpty());
	}

	@Test
	@DisplayName("channel indices map to bases in the order the trace stores them")
	void numberToBase() {
		assertEquals('A', SymbolTools.numberToBase(0));
		assertEquals('T', SymbolTools.numberToBase(1));
		assertEquals('G', SymbolTools.numberToBase(2));
		assertEquals('C', SymbolTools.numberToBase(3));
	}
}
