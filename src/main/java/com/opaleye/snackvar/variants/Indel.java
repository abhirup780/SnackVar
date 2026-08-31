/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified from the original SnackVar (https://github.com/Young-gonKim/SnackVar)
 * as part of the SnackVar 3.0 modernisation fork. See NOTICE.
 */

package com.opaleye.snackvar.variants;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;

import org.biojava.bio.symbol.IllegalAlphabetException;

import com.opaleye.snackvar.AlignedPoint;
import com.opaleye.snackvar.EquivExpression;
import com.opaleye.snackvar.Formatter;
import com.opaleye.snackvar.RootController;

import javafx.beans.property.SimpleStringProperty;

/**
 * Title : Indel
 * A subclass of Variant
 * A class for Indel Variant
 * @author Young-gon Kim
 *2018.10
 */
public class Indel extends Variant{

	private static final System.Logger LOG = System.getLogger(Indel.class.getName());
	public static final int deletion = -1;
	public static final int insertion = 1;
	public static final int duplication = 2;
	public static final int delins = 0;

	private int type;
	private int gIndex2;
	private boolean coding1, coding2;
	private String cIndex2;
	private String indelSeq;

	protected TreeSet<EquivExpression> equivExpressionList;



	private int getAlignedIndexFromGIndex(int gIndex) throws Exception {
		Vector<AlignedPoint> aps = rootController.alignedPoints;
		for(int i=0;i<aps.size();i++) {
			AlignedPoint ap = aps.get(i);
			if(ap.getGIndex()==gIndex) return (i+1);	// found: always at least 1
		}
		return 0;
	}

	/** {@link #getAlignedIndexFromGIndex} without the checked exception; 0 when not found. */
	private int alignedIndexOrZero(int gIndexValue) {
		try {
			return getAlignedIndexFromGIndex(gIndexValue);
		}
		catch (Exception ex) {
			return 0;
		}
	}

	/**
	 * Applies the HGVS 3' rule, and then the duplication rule, to an insertion.
	 *
	 * <p>An insertion slides one base to the right whenever its first base equals
	 * the reference base immediately following it — and when it does, the
	 * inserted sequence rotates left by one. So {@code c.150_151insGA} and
	 * {@code c.151_152insAG} describe the same allele, and HGVS requires the
	 * 3'-most form.
	 *
	 * <p>The equivalence search cannot find these: it shifts coordinates while
	 * holding indelSeq fixed, so a placement that needs a different rotation is
	 * invisible to it. Two things followed from that. Reported insertions were
	 * not reliably 3'-normalised, and because the forward and reverse strands
	 * recover different rotations of the same insertion, the two calls never
	 * compared equal — so one variant was listed twice instead of once with a
	 * frequency of 2.
	 *
	 * <p>Once shifted as far right as it goes, an insertion whose sequence
	 * matches the bases directly preceding it is a duplication, which HGVS
	 * requires to be described as such.
	 *
	 * <p>Nothing is committed unless both flanks still resolve to columns in the
	 * alignment, since a call outside it cannot be given cDNA coordinates.
	 */
	private void normaliseInsertion() {
		if (rootController == null || rootController.reference == null) {
			return;
		}
		String refString = rootController.reference.getRefString();
		if (refString == null || indelSeq == null || indelSeq.isEmpty()) {
			return;
		}

		// gIndex is the reference position the sequence is inserted after, so
		// the base immediately following it is refString.charAt(gIndex).
		int position = gIndex;
		String seq = indelSeq;
		int bestPosition = position;
		String bestSeq = seq;

		while (position < refString.length() && seq.charAt(0) == refString.charAt(position)) {
			seq = seq.substring(1) + seq.charAt(0);
			position++;
			if (alignedIndexOrZero(position) > 0 && alignedIndexOrZero(position + 1) > 0) {
				bestPosition = position;
				bestSeq = seq;
			}
			else {
				break;
			}
		}

		int length = bestSeq.length();
		boolean isDuplication = bestPosition >= length
				&& refString.regionMatches(bestPosition - length, bestSeq, 0, length);

		int candidateGIndex = isDuplication ? bestPosition - length + 1 : bestPosition;
		int candidateGIndex2 = isDuplication ? bestPosition : bestPosition + 1;

		if (alignedIndexOrZero(candidateGIndex) == 0 || alignedIndexOrZero(candidateGIndex2) == 0) {
			return; // cannot be expressed in this alignment; leave the call untouched
		}

		indelSeq = bestSeq;
		gIndex = candidateGIndex;
		gIndex2 = candidateGIndex2;
		if (isDuplication) {
			type = duplication;
		}
	}

	/**
	 * Lists the insertion placements equivalent to the current one, each with the
	 * rotation of the inserted sequence that placement requires.
	 *
	 * <p>Mirror of the shift in {@link #normaliseInsertion}: moving one base left
	 * needs the last base of the inserted sequence to equal the reference base at
	 * that position, and rotates the sequence right by one.
	 */
	private void addRotatedInsertionEquivalents() {
		String refString = rootController.reference.getRefString();
		int position = gIndex;
		String seq = indelSeq;

		while (position >= 1 && seq.charAt(seq.length() - 1) == refString.charAt(position - 1)) {
			seq = seq.charAt(seq.length() - 1) + seq.substring(0, seq.length() - 1);
			position--;
			int start = alignedIndexOrZero(position);
			int end = alignedIndexOrZero(position + 1);
			if (start == 0 || end == 0) {
				break;
			}
			equivExpressionList.add(makeHGVS(start, end, seq));
		}
	}

	private String getMutatedSeq(int newGIndex1, int newGIndex2) throws Exception {
		StringBuilder ret = new StringBuilder();
		Vector<AlignedPoint> aps = rootController.alignedPoints;
		//int newGIndex1 = gIndex + offset;
		//int newGIndex2 = gIndex2 + offset;

		AlignedPoint firstPoint = aps.get(0);
		AlignedPoint lastPoint = aps.get(aps.size()-1);
		if(newGIndex1<firstPoint.getGIndex() || newGIndex2>lastPoint.getGIndex()) 
			return null;


		StringBuilder dupBuffer = new StringBuilder();
		for(int i=0;i<aps.size();i++) {
			AlignedPoint ap = aps.get(i);
			if(ap.getRefChar()== Formatter.gapChar) 
				continue;
			if(type == deletion) {
				if(ap.getGIndex()>=newGIndex1 && ap.getGIndex()<=newGIndex2) {
					//do nothing
				}
				else  {
					ret.append(ap.getRefChar());
				}
			}
			else if(type == insertion) {
				ret.append(ap.getRefChar());
				if(ap.getGIndex() == newGIndex1) {
					ret.append(indelSeq);
				}
			}
			else if(type == duplication) {
				if(ap.getGIndex()>=newGIndex1 && ap.getGIndex()<=newGIndex2) {
					dupBuffer.append(ap.getRefChar());
				}
				ret.append(ap.getRefChar());
				if(ap.getGIndex() == newGIndex2) 
					ret.append(dupBuffer);
			}
			else if(type == delins) {
				if(ap.getGIndex()>=newGIndex1 && ap.getGIndex()<=newGIndex2) {
					//do nothing
				}
				else { 
					ret.append(ap.getRefChar());
				}

				if(ap.getGIndex() == newGIndex1) 
					ret.append(indelSeq);
			}
		}

		return ret.toString();

	}


	//constructor for Indel
	public Indel(RootController rootController, String zygosity, int direction, int type, int indelStartIndex, int indelEndIndex, int focusedIndex,  String indelSeq, boolean onTarget) {
		super();
		this.rootController = rootController;
		equivExpressionList = new TreeSet<EquivExpression>();
		Vector<AlignedPoint> aps = rootController.alignedPoints;
		this.direction = direction;
		this.zygosity = zygosity;
		this.onTarget = onTarget;
		this.type = type;
		this.indelSeq = (indelSeq == null) ? "" : indelSeq;
		AlignedPoint ap3 = aps.get(focusedIndex-1);
		this.alignmentIndex = focusedIndex;
		this.fwdTraceIndex = ap3.getFwdTraceIndex();
		this.revTraceIndex = ap3.getRevTraceIndex();
		if(direction == 1) {
			this.fwdTraceChar = ap3.getFwdChar();
			this.revTraceChar = Formatter.gapChar;
		}
		else if(direction == -1) {
			this.fwdTraceChar = Formatter.gapChar;
			this.revTraceChar = ap3.getRevChar();
		}


		// Initial endpoints and everything derived from them.
		AlignedPoint ap1 = aps.get(indelStartIndex-1);
		AlignedPoint ap2 = aps.get(indelEndIndex-1);
		this.cIndex = ap1.getStringCIndex();
		this.cIndex2 = ap2.getStringCIndex();
		this.coding1 = ap1.isCoding();
		this.coding2 = ap2.isCoding();
		this.gIndex = ap1.getGIndex();
		this.gIndex2 = ap2.getGIndex();

		if(type == insertion) {
			// Put the insertion in its HGVS-canonical form before anything is
			// derived from it, so the equivalence list, the reported HGVS and
			// the forward/reverse comparison all agree.
			normaliseInsertion();
			int normalisedStart = alignedIndexOrZero(gIndex);
			int normalisedEnd = alignedIndexOrZero(gIndex2);
			if(normalisedStart > 0 && normalisedEnd > 0) {
				indelStartIndex = normalisedStart;
				indelEndIndex = normalisedEnd;
				ap1 = aps.get(indelStartIndex-1);
				ap2 = aps.get(indelEndIndex-1);
				this.cIndex = ap1.getStringCIndex();
				this.cIndex2 = ap2.getStringCIndex();
				this.coding1 = ap1.isCoding();
				this.coding2 = ap2.isCoding();
			}
		}

		int originalGIndex = gIndex;
		int originalGIndex2 = gIndex2;

		EquivExpression equivExpression = makeHGVS(indelStartIndex, indelEndIndex);
		equivExpressionList.add(equivExpression);
		if(type == insertion) {
			addRotatedInsertionEquivalents();
		}	
		//HGVS = equivExpression.getHGVS();

		// Uncomment to apply left alignment.
		//int leftAlignedStartIndex = 0;
		//int leftAlignedEndIndex = 0;


		// Build the list of equivalent expressions.
		try {
			String originalSeq = getMutatedSeq(gIndex, gIndex2);
			int offset = -1;
			// Search leftwards.
			for(;;offset--) {
				String mutatedSeq = getMutatedSeq(gIndex+offset, gIndex2+offset);
				if(mutatedSeq == null) {
					break;
				}
				else if(mutatedSeq.equals(originalSeq)) {
					int tempStartIndex = getAlignedIndexFromGIndex(gIndex+offset);
					int tempEndIndex = getAlignedIndexFromGIndex(gIndex2+offset);
					equivExpression = makeHGVS(tempStartIndex, tempEndIndex);
					equivExpressionList.add(equivExpression);
				}
			}


			// Deliberately not left-aligned: that would disagree with the
			// alignment and the chromatogram. All equivalent expressions are
			// listed instead, and the leftmost of them is the left-aligned form.

			/* Code for left alignment, if it is ever wanted:
			leftAlignedStartIndex = indelStartIndex;
			leftAlignedEndIndex = indelEndIndex;


			leftAlignedStartIndex = getAlignedIndexFromGIndex(gIndex+maxLeftOffset);
			leftAlignedEndIndex = getAlignedIndexFromGIndex(gIndex2+maxLeftOffset);
			if(leftAlignedStartIndex >0  && leftAlignedEndIndex >0) {	
				ap1 = aps.get(leftAlignedStartIndex-1);
				ap2 = aps.get(leftAlignedEndIndex-1);
				this.cIndex = ap1.getStringCIndex();
				this.cIndex2 = ap2.getStringCIndex();
				this.coding1 = ap1.isCoding();
				this.coding2 = ap2.isCoding();
				this.gIndex = ap1.getGIndex();
				this.gIndex2 = ap2.getGIndex();
			}
			 */


			// Search rightwards too, to complete the equivalence list.
			offset = 1;
			for(;;offset++) {
				String mutatedSeq = getMutatedSeq(originalGIndex+offset, originalGIndex2+offset);
				if(mutatedSeq == null) {
					break;
				}
				else if(mutatedSeq.equals(originalSeq)) {
					int tempStartIndex = getAlignedIndexFromGIndex(originalGIndex+offset);
					int tempEndIndex = getAlignedIndexFromGIndex(originalGIndex2+offset);
					equivExpression = makeHGVS(tempStartIndex, tempEndIndex);
					equivExpressionList.add(equivExpression);
				}
			}
		}
		catch(Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "Indel", ex);
		}

		// Uncomment to apply left alignment.
		//equivExpression = makeHGVS(leftAlignedStartIndex, leftAlignedEndIndex);
		//HGVS = equivExpression.getHGVS();



		if(coding1 && coding2 && rootController.formatter.getFirstNumber() == 1) makeAAChange();

		//right alignment
		EquivExpression rtMostExpression = null;
		int rtMostIndex = -1;
		Iterator<EquivExpression> iter = equivExpressionList.iterator();
		while(iter.hasNext()) {
			EquivExpression tempEquiv = iter.next();
			if(rtMostIndex < tempEquiv.getgIndex2()) {
				rtMostIndex = tempEquiv.getgIndex2();
				rtMostExpression = tempEquiv;
			}
		}

		if(rtMostExpression != null)
			HGVS = rtMostExpression.getHGVS();

		makeTableViewProperties();
	}

	/**
	 * Generates HGVS nomenclature
	 */

	//type, cIndex, cIndex2, indelSeq
	/**
	 * Strips the "c." prefix from the second coordinate of a range.
	 *
	 * <p>HGVS writes a span as {@code c.150_151}, with the prefix stated once.
	 * The insertion branch used to skip this, so every insertion came out as
	 * {@code c.150_c.151ins...}, which is not valid HGVS.
	 */
	private static String rangeEnd(String cIndex) {
		if (cIndex != null && cIndex.startsWith("c.")) {
			return cIndex.substring(2);
		}
		return cIndex;
	}

	private EquivExpression makeHGVS(int alignedIndex1, int alignedIndex2) {
		return makeHGVS(alignedIndex1, alignedIndex2, indelSeq);
	}

	/**
	 * @param seq inserted sequence to use, which differs from {@link #indelSeq}
	 *            when describing a rotated equivalent of an insertion
	 */
	private EquivExpression makeHGVS(int alignedIndex1, int alignedIndex2, String seq) {
		String tempHGVS="";
		Vector<AlignedPoint> aps = rootController.alignedPoints;
		AlignedPoint ap1 = aps.get(alignedIndex1-1);
		AlignedPoint ap2 = aps.get(alignedIndex2-1);
		String localCIndex1 = ap1.getStringCIndex();
		String localCIndex2 = ap2.getStringCIndex();
		int gIndex1 = ap1.getGIndex();
		int gIndex2 = ap2.getGIndex();

		boolean singlePosition = localCIndex1.equals(localCIndex2);
		String span = singlePosition ? localCIndex1 : localCIndex1 + "_" + rangeEnd(localCIndex2);

		if(type == deletion) {
			tempHGVS = span + "del";
		}
		else if(type == insertion) {
			// An insertion is always written against the two flanking bases, so
			// it never collapses to a single position.
			tempHGVS = localCIndex1 + "_" + rangeEnd(localCIndex2) + "ins" + seq;
		}
		else if(type == duplication) {
			tempHGVS = span + "dup";
		}
		else if(type == delins) {
			tempHGVS = span + "delins" + seq;
		}
		return new EquivExpression(gIndex1, gIndex2, tempHGVS);
	}

	/**
	 * Generates AA change
	 */
	private void makeAAChange() {
		int length = 0;
		int i_cIndex1=0, i_cIndex2=0;
		String refString = rootController.reference.getRefString();
		String shiftedSeq = "";
		String ptnCoordi = "";

		Vector<Integer> cDnaStart = rootController.reference.getcDnaStart();
		Vector<Integer> cDnaEnd = rootController.reference.getcDnaEnd();
		if(cDnaStart == null || cDnaEnd == null || cDnaStart.isEmpty()) return;
		StringBuilder originalSeqBuilder = new StringBuilder();
		int intCDnaStart = 0;
		int intCDnaEnd = 0;
		for(int i=0;i<cDnaStart.size();i++) {
			intCDnaStart = (cDnaStart.get(i)).intValue();
			intCDnaEnd = (cDnaEnd.get(i)).intValue();
			originalSeqBuilder.append(refString, intCDnaStart-1, intCDnaEnd);
		}
		// Trailing 3' UTR, so a frameshift can read through past the last CDS block.
		originalSeqBuilder.append(refString.substring(intCDnaEnd));
		String originalSeq = originalSeqBuilder.toString(); 

		try {
			i_cIndex1 = Integer.parseInt(cIndex.replaceAll("[^0-9]",""));
			i_cIndex2 = Integer.parseInt(cIndex2.replaceAll("[^0-9]",""));

			if(type == deletion) {
				length = gIndex2-gIndex+1;
				shiftedSeq = originalSeq.substring(0, i_cIndex1-1) + originalSeq.substring(i_cIndex2);
			}
			else if(type == insertion) {
				length = indelSeq.length();
				shiftedSeq = originalSeq.substring(0, i_cIndex1) + indelSeq + originalSeq.substring(i_cIndex2-1);
			}

			else if(type ==  duplication) {
				length = gIndex2-gIndex+1;
				shiftedSeq = originalSeq.substring(0, i_cIndex1-1) + originalSeq.substring(i_cIndex1-1,i_cIndex2) + originalSeq.substring(i_cIndex1-1);
			}

			else if(type == delins) {
				length = indelSeq.length() - (gIndex2-gIndex+1);
				shiftedSeq = originalSeq.substring(0, i_cIndex1-1) + indelSeq + originalSeq.substring(i_cIndex2);
			}

			if(length % 3 != 0) {		//frameshift
				int fsStartIndex = 0;
				int fsCount = 0;
				int i = 0;

				for(i=0;i<shiftedSeq.length();i+=3) {
					if(i+3>originalSeq.length()) break;
					String originalAA = Variant.getAAfromTriple(originalSeq.substring(i,i+3));
					String shiftedAA = Variant.getAAfromTriple(shiftedSeq.substring(i,i+3));

					// If the first changed residue is a stop, there is no frameshift
					// tail to describe.
					if(shiftedAA.equals("*")) {
						fsStartIndex = (i/3)+1;
						AAChange = "p.(" + originalAA + fsStartIndex + shiftedAA + ")";
						return;
					}
					if(!originalAA.equals(shiftedAA)) {
						fsStartIndex = (i/3)+1;
						ptnCoordi = "p.(" + originalAA + fsStartIndex + shiftedAA + "fs";
						break;
					}
				}

				boolean terminalFound = false;
				try {
					for(;i<shiftedSeq.length();i+=3) {
						if(i+3>originalSeq.length()) break;
						fsCount++;
						String shiftedAA = Variant.getAAfromTriple(shiftedSeq.substring(i,i+3));
						if(shiftedAA.equals("*")) {
							terminalFound = true;
							break;
						}
					}
				}
				catch(StringIndexOutOfBoundsException sie) {
				}
				if(terminalFound)
					AAChange =ptnCoordi + "*" + fsCount + ")";
				else
					AAChange = ptnCoordi+")";
			}
			else {		//No frameshift
				Vector<String> originalAAList = new Vector<String>();
				Vector<String> shiftedAAList = new Vector<String>();

				for(int i=0;i<originalSeq.length();i+=3) {
					if((i+3)>originalSeq.length()) break;
					originalAAList.add(Variant.getAAfromTriple(originalSeq.substring(i,i+3)));
				}

				for(int i=0;i<shiftedSeq.length();i+=3) {
					if((i+3)>shiftedSeq.length()) break;
					shiftedAAList.add(Variant.getAAfromTriple(shiftedSeq.substring(i,i+3)));
				}
				// 1-based coordinates.
				int leftPos = 1;	// last position where both still agree
				int originalRightPos = originalAAList.size();
				int shiftedRightPos = shiftedAAList.size();

				if(originalAAList.size() == shiftedAAList.size()) {
					boolean same = true;
					for(int i=0;i<originalAAList.size();i++) {
						if(!originalAAList.get(i).equals(shiftedAAList.get(i))) same = false;
					}
					if(same) {
						AAChange = "no amino acid change";
						return;
					}
				}

				for(leftPos=1;leftPos<=originalAAList.size() && leftPos<=shiftedAAList.size();leftPos++) {
					if(!originalAAList.get(leftPos-1).equals(shiftedAAList.get(leftPos-1))) break;
				}
				leftPos--;	// 1-based, and the last position where both agree

				while(originalRightPos>leftPos && shiftedRightPos>leftPos) {
					if(!originalAAList.get(originalRightPos-1).equals(shiftedAAList.get(shiftedRightPos-1))) break;
					originalRightPos--;
					shiftedRightPos--;
				}

				// originalRightPos / shiftedRightPos: the first position that differs
				// scanning from the right, or wherever they met leftPos.
				if(leftPos == shiftedRightPos) {	//deletion
					ptnCoordi = "p.(";
					ptnCoordi += originalAAList.get(leftPos+1-1) + (leftPos+1);
					if(leftPos+1 != originalRightPos) ptnCoordi += "_" + originalAAList.get(originalRightPos-1) + originalRightPos;
					ptnCoordi += "del)";
				}
				else if(leftPos == originalRightPos ) { //insertion
					ptnCoordi = "p.(";

					// Decide whether this is a duplication.
					boolean dup = true;
					int indelSize = shiftedRightPos - leftPos;
					for(int i=leftPos+1;i<=shiftedRightPos;i++) {
						if(!(shiftedAAList.get(i-1)).equals(shiftedAAList.get(i-1-indelSize))) {
							dup = false;
							break;
						}
					}
					if(dup) {
						ptnCoordi += originalAAList.get(leftPos-1-(indelSize-1)) + (leftPos-(indelSize-1));
						if(indelSize > 1) {
							ptnCoordi += "_";
							ptnCoordi += originalAAList.get(leftPos-1) + (leftPos);
						}
						ptnCoordi += "dup)";
					}
					else {


						ptnCoordi += originalAAList.get(leftPos-1) + (leftPos);
						if(leftPos>=originalAAList.size()) ptnCoordi += "";
						else ptnCoordi += "_" +originalAAList.get(leftPos) + (leftPos+1);
						ptnCoordi += "ins";
						for(int i=leftPos+1;i<=shiftedRightPos;i++) {
							ptnCoordi+=shiftedAAList.get(i-1);
						}
						ptnCoordi+=")";
					}
				}
				else { //delins
					ptnCoordi = "p.(";
					ptnCoordi += originalAAList.get(leftPos+1-1) + (leftPos+1);
					if(leftPos+1 != originalRightPos) ptnCoordi += "_" + originalAAList.get(originalRightPos-1) + originalRightPos;
					ptnCoordi += "delins";
					for(int i=leftPos+1;i<=shiftedRightPos;i++) {
						ptnCoordi+=shiftedAAList.get(i-1);
					}
					ptnCoordi+=")";
				}
				AAChange = ptnCoordi;
			}

		}
		catch(IllegalAlphabetException iae) {
			//probably due to 'N' from indelSeq
			AAChange = "(untranslatable)";
			return;

		}
		catch(Exception ex) {
			// Deliberately silent: if the change cannot be described cleanly,
			// leave AAChange empty rather than emitting a speculative one.
			return;
		}
		//HGVS += ", " + AAChange;
	}

	/** Getters and setters for member variables*/

	public int getType() {
		return type;
	}
	public String getCIndex2() {
		return cIndex2;
	}

	public String getIndelSeq() {
		return indelSeq;
	}

	public int getgIndex2() {
		return gIndex2;
	}

	public TreeSet<EquivExpression> getEquivExpressionList() {
		return equivExpressionList;
	}
	public void setEquivExpressionList(TreeSet<EquivExpression> equivExpressionList) {
		this.equivExpressionList = equivExpressionList;
	}


}
