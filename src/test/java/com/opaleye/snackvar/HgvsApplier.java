package com.opaleye.snackvar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a coding-DNA HGVS expression back to a reference sequence.
 *
 * <p>Used to check a variant call independently of how it was produced. A call
 * is correct if applying it to the reference reproduces the allele that was
 * actually sequenced — which is a stronger statement than string-matching an
 * expected HGVS, because a variant in a repeat has several equally valid
 * representations and HGVS requires the 3'-most one.
 *
 * <p>Only the simple coding forms are handled, which is all the tests need:
 * coordinates are plain integers, so this assumes a reference that is coding
 * throughout and numbered from 1.
 */
final class HgvsApplier {

	private static final Pattern DEL = Pattern.compile("^c\\.(\\d+)(?:_(\\d+))?del$");
	private static final Pattern DUP = Pattern.compile("^c\\.(\\d+)(?:_(\\d+))?dup$");
	private static final Pattern INS = Pattern.compile("^c\\.(\\d+)_(\\d+)ins([ACGT]+)$");
	private static final Pattern DELINS = Pattern.compile("^c\\.(\\d+)(?:_(\\d+))?delins([ACGT]+)$");

	private HgvsApplier() {
	}

	/**
	 * @return the reference with the variant applied, or {@code null} if the
	 *         expression is not one of the supported forms
	 */
	static String apply(String reference, String hgvs) {
		Matcher m;

		// delins must be tested before del, since "delins" also starts with "del".
		if ((m = DELINS.matcher(hgvs)).matches()) {
			int from = Integer.parseInt(m.group(1));
			int to = (m.group(2) == null) ? from : Integer.parseInt(m.group(2));
			return reference.substring(0, from - 1) + m.group(3) + reference.substring(to);
		}
		if ((m = DEL.matcher(hgvs)).matches()) {
			int from = Integer.parseInt(m.group(1));
			int to = (m.group(2) == null) ? from : Integer.parseInt(m.group(2));
			return reference.substring(0, from - 1) + reference.substring(to);
		}
		if ((m = DUP.matcher(hgvs)).matches()) {
			int from = Integer.parseInt(m.group(1));
			int to = (m.group(2) == null) ? from : Integer.parseInt(m.group(2));
			// The duplicated copy is inserted immediately after the original.
			return reference.substring(0, to) + reference.substring(from - 1, to) + reference.substring(to);
		}
		if ((m = INS.matcher(hgvs)).matches()) {
			int after = Integer.parseInt(m.group(1));
			return reference.substring(0, after) + m.group(3) + reference.substring(after);
		}
		return null;
	}
}
