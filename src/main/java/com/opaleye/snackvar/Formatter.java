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

package com.opaleye.snackvar;

import java.util.TreeMap;
import java.util.Vector;

import com.opaleye.snackvar.mmalignment.AlignedPair;
import com.opaleye.snackvar.reference.Reference;

/**
 * Title : Formatter
 * Contains functions that make a list of AlignedPoints based on the result of jAligner
 * Formatting functions are derived from jaligner.formats.Pair.format() 
 * @author Young-gon Kim
 * 2018.5.
 */

public class Formatter {
	
	public static final char gapChar = '-';
	public TreeMap<Integer, Integer> fwdCoordinateMap = new TreeMap<Integer, Integer>();
	public TreeMap<Integer, Integer> revCoordinateMap = new TreeMap<Integer, Integer>();

	//public static BiMap<Integer, Integer> fwdCoordinateMap = HashBiMap.create();
	//public static BiMap<Integer, Integer> revCoordinateMap = HashBiMap.create();

	public int fwdStartOffset = 700;
	public int revStartOffset = 700;
	public int fwdNewLength = 0;
	public int revNewLength = 0;
	public int fwdTraceAlignStartPoint = 1;
	public int revTraceAlignStartPoint = 1;
	private int firstNumber = 1;
	
	

	public Formatter(int firstNumber) {
		this.firstNumber = firstNumber;
		//fwdCoordinateMap = HashBiMap.create();
		//revCoordinateMap = HashBiMap.create();

		fwdCoordinateMap = new TreeMap<Integer, Integer> ();
		revCoordinateMap = new TreeMap<Integer, Integer> ();


		// Pads the shorter of the two traces so both images end up the same
		// width and the panes scroll together. Measured in trace samples, not
		// bases; the default is about half a screen.
		fwdStartOffset = 700;
		revStartOffset = 700;
		fwdNewLength = 0;
		revNewLength = 0;
		fwdTraceAlignStartPoint = 1;
		revTraceAlignStartPoint = 1;

	}

	/**
	 * Returns a list (Vector) of AlignedPoints
	 * Used for the alignment of 2 sequences (reference vs fwd or rev)
	 * @param alignment : the result of the alignment of jAligner (ref vs fwd or rev)
	 * @param refFile : reference file
	 * @param trace : fwd or rev trace
	 * @param direction : 1:forward, -1:reverse
	 * 	@throws ArrayIndexOutOfBoundsExeption when the alignment fails
	 * 
	 * This functions is derived from jaligner.formats.Pair.format()
	 * 2018.5
	 */

	public Vector<AlignedPoint> format2(AlignedPair ap, Reference refFile, GanseqTrace trace, int direction) throws ArrayIndexOutOfBoundsException {


		// (1) Position in the reference (genomic DNA), 1-based. Never used to index an array.
		int refPos = ap.getStart1()+1;
		// (2) Position in the trace, 0-based, because qCalls and baseCalls are 0-based.
		int tracePos = ap.getStart2();
		char[] refSeq = ap.getAlignedString1().toCharArray();
		char[] traceSeq = ap.getAlignedString2().toCharArray();
		int alignmentLength = ap.getAlignedString1().length();

		// (3) Position in the alignment, i.e. the index into refSeq / traceSeq. 0-based.
		int alignmentPos = 0;
		Vector<AlignedPoint> alignedPoints = new Vector<AlignedPoint>();
		while(alignmentPos < alignmentLength) {
			char refChar = refSeq[alignmentPos];
			char traceChar = traceSeq[alignmentPos];
			AlignedPoint tempPoint = null;

			if(refChar != gapChar) {
				char discrepency = ' ';
				if(refChar!=traceChar)
					discrepency = '*';

				if(direction == 1) 
					tempPoint = new AlignedPoint (refChar, traceChar, gapChar, discrepency, refPos, tracePos+1, -1);
				else if(direction == -1)
					tempPoint = new AlignedPoint (refChar, gapChar, traceChar, discrepency, refPos, -1, tracePos+1);

				if(traceChar!=gapChar) {
					if(direction == 1) {
						tempPoint.setFwdQuality(trace.getQCalls()[tracePos]);
						fwdCoordinateMap.put(tracePos+1, alignedPoints.size()+1);
					}
					else if(direction == -1) {
						tempPoint.setRevQuality(trace.getQCalls()[tracePos]);
						revCoordinateMap.put(tracePos+1, alignedPoints.size()+1);
					}
					tracePos++;
				}
				refPos++;
				alignmentPos++;

			}

			else if(refChar == gapChar) {
				char discrepency = '*';
				if(direction == 1)
					tempPoint = new AlignedPoint (refChar, traceChar, gapChar, discrepency, refPos-1, tracePos+1, -1);
				else if (direction == -1)
					tempPoint = new AlignedPoint (refChar, gapChar, traceChar, discrepency, refPos-1, -1, tracePos+1);
				if(traceChar!=gapChar) {
					if(direction == 1) {
						tempPoint.setFwdQuality(trace.getQCalls()[tracePos]);
						fwdCoordinateMap.put(tracePos+1, alignedPoints.size()+1);
					}
					else if(direction == -1) {
						tempPoint.setRevQuality(trace.getQCalls()[tracePos]);
						revCoordinateMap.put(tracePos+1, alignedPoints.size()+1);
					}
					tracePos++;
				}
				alignmentPos++; 

			}

			if(tempPoint.getDiscrepency() == ' ') {	
				if(direction == 1 && tempPoint.getFwdQuality()<20) {
					tempPoint.setDiscrepency('+');
				}
				if(direction == -1 && tempPoint.getRevQuality()<20) {
					tempPoint.setDiscrepency('+');
				}
			}
			alignedPoints.add(tempPoint);
		}

		if(direction ==1) 
			fwdNewLength = fwdStartOffset + trace.getTraceLength()*2 + (int)RootController.paneWidth/2;
		else if (direction == -1)
			revNewLength = revStartOffset + trace.getTraceLength()*2 + (int)RootController.paneWidth/2;

		// Build cDNA numbering.
		int startGIndex = 0, endGIndex = 0;
		startGIndex = ap.getStart1()+1;
		endGIndex = refPos;

		alignedPoints = addCDnaNumber(alignedPoints, startGIndex, endGIndex, refFile);

		return alignedPoints;
	}

	/**
	 * Returns a list (Vector) of AlignedPoints
	 * Used for the alignment of 3 sequences (reference vs fwd and rev)
	 * @param fwdAlignment : the result of the alignment of jAligner (fwd vs ref)
	 * @param revAlignment : the result of the alignment of jAligner (rev vs ref)
	 * @param refFile : reference file
	 * @param fwdTrace : fwd trace
	 * @param revTrace : rev trace
	 * @throws ArrayIndexOutOfBoundsExeption when the alignment fails
	 * @throws NoContigExeption when fwd trace and rev trace don't have overlap
	 * 
	 * This functions is derived from jaligner.formats.Pair.format()
	 * 2018.5
	 */
	public Vector<AlignedPoint> format3(AlignedPair fwdAp, AlignedPair revAp, Reference refFile, GanseqTrace fwdTrace, GanseqTrace revTrace) throws ArrayIndexOutOfBoundsException, NoContigException {

		int fwdAlignmentLength = fwdAp.getAlignedString1().length();
		int revAlignmentLength = revAp.getAlignedString1().length();

		// (1) Position in the reference (genomic DNA), 1-based. Never used to index an array.
		int fwdRefPos = fwdAp.getStart1()+1;
		int revRefPos = revAp.getStart1()+1;

		// (2) Position in the trace, 0-based, because qCalls and baseCalls are 0-based.
		int fwdTracePos = fwdAp.getStart2();
		int revTracePos = revAp.getStart2(); 


		char[] fwdRefSeq = fwdAp.getAlignedString1().toCharArray();
		char[] fwdTraceSeq = fwdAp.getAlignedString2().toCharArray();
		char[] revRefSeq = revAp.getAlignedString1().toCharArray();
		char[] revTraceSeq = revAp.getAlignedString2().toCharArray();

		// (3) Position in the alignment, i.e. the index into fwdRefSeq / fwdTraceSeq
		// and revRefSeq / revTraceSeq. 0-based.
		int fwdAlignmentPos = 0;
		int revAlignmentPos = 0;

		Vector<AlignedPoint> alignedPoints = new Vector<AlignedPoint>();

		// Leading region covered by only one of the two traces.
		// Forward trace starts first.
		if(fwdRefPos < revRefPos) {

			// The two traces land too far apart to overlap at all. Comparing where
			// each attached to the reference catches this; left alone it would
			// fall off the end of the array below.
			if(revRefPos-fwdRefPos > fwdRefSeq.length) 
				throw new NoContigException();

			// Leaving this loop on a gap is fine; the branches below handle it.
			while(fwdRefPos<revRefPos) {
				char fwdRefChar = fwdRefSeq[fwdAlignmentPos];
				char fwdTraceChar = fwdTraceSeq[fwdAlignmentPos];
				char revTraceChar = gapChar;

				AlignedPoint tempPoint = null;
				if(fwdRefChar == gapChar)
					tempPoint = new AlignedPoint (fwdRefChar, fwdTraceChar, revTraceChar, ' ', fwdRefPos-1, fwdTracePos+1, revTracePos+1);
				else {
					tempPoint = new AlignedPoint (fwdRefChar, fwdTraceChar, revTraceChar, ' ', fwdRefPos++, fwdTracePos+1, revTracePos+1);
				}

				if(fwdTraceChar!=gapChar) {
					tempPoint.setFwdQuality(fwdTrace.getQCalls()[fwdTracePos]);
					// Maps trace position to alignment column, so the chromatogram can
					// be labelled in alignment coordinates. Both are 1-based here.
					fwdCoordinateMap.put(fwdTracePos+1, alignedPoints.size()+1);
					fwdTracePos++;
				}
				alignedPoints.add(tempPoint);
				fwdAlignmentPos++;

			}

			if(fwdTracePos > 0)
				revStartOffset += fwdTrace.getBaseCalls()[fwdTracePos-1] * 2;
		}

		// Reverse trace starts first.
		else if(revRefPos < fwdRefPos) {

			if(fwdRefPos-revRefPos > revRefSeq.length) 
				throw new NoContigException();

			while(revRefPos<fwdRefPos) {
				char revRefChar = revRefSeq[revAlignmentPos];
				char revTraceChar = revTraceSeq[revAlignmentPos];
				char fwdTraceChar = gapChar;

				AlignedPoint tempPoint = null;
				if(revRefChar == gapChar)
					tempPoint = new AlignedPoint (revRefChar, fwdTraceChar, revTraceChar, ' ', revRefPos-1, fwdTracePos+1, revTracePos+1);
				else {
					tempPoint = new AlignedPoint (revRefChar, fwdTraceChar, revTraceChar, ' ', revRefPos++, fwdTracePos+1, revTracePos+1);
				}

				if(revTraceChar!=gapChar) {
					tempPoint.setRevQuality(revTrace.getQCalls()[revTracePos]);
					// Maps trace position to alignment column, so the chromatogram can
					// be labelled in alignment coordinates. Both are 1-based here.
					revCoordinateMap.put(revTracePos+1, alignedPoints.size()+1);
					revTracePos++;
				}
				alignedPoints.add(tempPoint);
				revAlignmentPos++;
			}
			if(revTracePos > 0)
				fwdStartOffset += revTrace.getBaseCalls()[revTracePos-1] * 2;
		}
		fwdTraceAlignStartPoint = fwdTracePos+1;
		revTraceAlignStartPoint = revTracePos+1;

		// Runs until either alignment is exhausted.
		while(fwdAlignmentPos < fwdAlignmentLength && revAlignmentPos < revAlignmentLength) {

			char fwdRefChar = fwdRefSeq[fwdAlignmentPos];
			char revRefChar = revRefSeq[revAlignmentPos];
			char fwdTraceChar = fwdTraceSeq[fwdAlignmentPos];
			char revTraceChar = revTraceSeq[revAlignmentPos];
			AlignedPoint tempPoint = null;

			//		fwdAlignmentPos, revAlignmentPos, fwdRefPos, revRefPos, fwdTracePos,
			//		revTracePos, fwdRefChar, revRefChar, fwdTraceChar, revTraceChar));

			// Both strands read the reference base: no homozygous insertion here.
			if(fwdRefChar == revRefChar && fwdRefChar != gapChar) {
				char discrepency = ' ';
				if(fwdRefChar!=fwdTraceChar || revRefChar != revTraceChar)
					discrepency = '*';
				tempPoint = new AlignedPoint (fwdRefChar, fwdTraceChar, revTraceChar, discrepency, fwdRefPos, fwdTracePos+1, revTracePos+1);
				if(fwdTraceChar!=gapChar) {
					tempPoint.setFwdQuality(fwdTrace.getQCalls()[fwdTracePos]);
					fwdCoordinateMap.put(fwdTracePos+1, alignedPoints.size()+1);
					fwdTracePos++;
				}
				if(revTraceChar!=gapChar) {
					tempPoint.setRevQuality(revTrace.getQCalls()[revTracePos]);
					revCoordinateMap.put(revTracePos+1, alignedPoints.size()+1);
					revTracePos++;
				}
				fwdRefPos++;
				revRefPos++;
				fwdAlignmentPos++;
				revAlignmentPos++;
			}

			// Both alignments show a homozygous insertion at this column.
			else if(fwdRefChar == gapChar && revRefChar == gapChar) {
				char discrepency = '*';
				tempPoint = new AlignedPoint (fwdRefChar, fwdTraceChar, revTraceChar, discrepency, fwdRefPos-1, fwdTracePos+1, revTracePos+1);
				if(fwdTraceChar!=gapChar) {
					tempPoint.setFwdQuality(fwdTrace.getQCalls()[fwdTracePos]);
					fwdCoordinateMap.put(fwdTracePos+1, alignedPoints.size()+1);
					fwdTracePos++;
				}
				if(revTraceChar!=gapChar) {
					tempPoint.setRevQuality(revTrace.getQCalls()[revTracePos]);
					revCoordinateMap.put(revTracePos+1, alignedPoints.size()+1);
					revTracePos++;
				}
				fwdAlignmentPos++; 
				revAlignmentPos++;
			}

			// Insertion gap on the forward strand only; the reverse strand waits.
			else if(fwdRefChar==gapChar) {
				tempPoint = new AlignedPoint (gapChar, fwdTraceChar, gapChar, '*', fwdRefPos-1, fwdTracePos+1, revTracePos+1);

				tempPoint.setFwdQuality(fwdTrace.getQCalls()[fwdTracePos]);
				fwdCoordinateMap.put(fwdTracePos+1, alignedPoints.size()+1);

				fwdAlignmentPos++; 
				fwdTracePos++;
			}

			// Insertion gap on the reverse strand only; the forward strand waits.
			else if(revRefChar==gapChar) {
				tempPoint = new AlignedPoint (gapChar, gapChar, revTraceChar, '*', fwdRefPos-1, fwdTracePos+1, revTracePos+1);
				tempPoint.setRevQuality(revTrace.getQCalls()[revTracePos]);
				revCoordinateMap.put(revTracePos+1, alignedPoints.size()+1);
				revAlignmentPos++;
				revTracePos++;
			}

			if(tempPoint == null) {
				// The forward and reverse alignments disagree on the reference
				// base here, so they have desynchronised and nothing sensible
				// can be emitted for this column.
				throw new NoContigException();
			}
			if(tempPoint.getDiscrepency() == ' ') {	
				if(tempPoint.getFwdQuality()<30 && tempPoint.getRevQuality()<30) {
					tempPoint.setDiscrepency('+');
				}
			}
			alignedPoints.add(tempPoint);
		}

		// Trailing region covered by only one of the two traces.
		// Forward trace runs on past the reverse.
		if(revAlignmentPos == revAlignmentLength) {
			if(fwdTracePos<=0) throw new NoContigException();

			while(fwdAlignmentPos < fwdAlignmentLength) {
				char refChar = fwdRefSeq[fwdAlignmentPos];
				char fwdTraceChar = fwdTraceSeq[fwdAlignmentPos];
				char revTraceChar = gapChar;

				AlignedPoint tempPoint = null;
				if(refChar == gapChar) 
					tempPoint = new AlignedPoint (refChar, fwdTraceChar, revTraceChar, ' ', fwdRefPos-1, fwdTracePos+1, revTracePos);
				else
					tempPoint = new AlignedPoint (refChar, fwdTraceChar, revTraceChar, ' ', fwdRefPos++, fwdTracePos+1, revTracePos);

				if(fwdTraceChar!=gapChar) {
					tempPoint.setFwdQuality(fwdTrace.getQCalls()[fwdTracePos]);
					fwdCoordinateMap.put(fwdTracePos+1, alignedPoints.size()+1);
					fwdTracePos++;
				}
				alignedPoints.add(tempPoint);
				fwdAlignmentPos++;

			}
		}

		// Reverse trace runs on past the forward.
		else if(fwdAlignmentPos == fwdAlignmentLength) {
			if(revTracePos<=0) throw new NoContigException(); 

			while(revAlignmentPos < revAlignmentLength) {
				char refChar = revRefSeq[revAlignmentPos];
				char fwdTraceChar = gapChar;
				char revTraceChar = revTraceSeq[revAlignmentPos];

				AlignedPoint tempPoint = null;
				if(refChar == gapChar) 
					tempPoint = new AlignedPoint (refChar, fwdTraceChar, revTraceChar, ' ', revRefPos-1, fwdTracePos, revTracePos+1);
				else
					tempPoint = new AlignedPoint (refChar, fwdTraceChar, revTraceChar, ' ', revRefPos++, fwdTracePos, revTracePos+1);

				if(revTraceChar!=gapChar) {
					tempPoint.setRevQuality(revTrace.getQCalls()[revTracePos]);
					revCoordinateMap.put(revTracePos+1, alignedPoints.size()+1);
					revTracePos++;
				}
				alignedPoints.add(tempPoint);
				revAlignmentPos++;

			}
		}

		fwdNewLength = fwdStartOffset + fwdTrace.getTraceLength()*2 + (int)RootController.paneWidth/2;
		revNewLength = revStartOffset + revTrace.getTraceLength()*2 + (int)RootController.paneWidth/2;
		
		fwdNewLength = Integer.max(fwdNewLength, revNewLength);
		revNewLength = Integer.max(fwdNewLength, revNewLength);
			
		
		// Build cDNA numbering.


		int startGIndex = 0, endGIndex = 0;
		if(fwdAp.getStart1() <= revAp.getStart1()) startGIndex = fwdAp.getStart1()+1;
		else startGIndex = revAp.getStart1()+1;

		if(fwdRefPos >= revRefPos) endGIndex = fwdRefPos;
		else endGIndex = revRefPos;

		alignedPoints = addCDnaNumber(alignedPoints, startGIndex, endGIndex, refFile);

		return alignedPoints;
	}

	/**
	 * returns true if the gIndex is on exon
	 * returns false if the gIndex is on intron
	 * @param exonStart : a list of start positions of exons in the gene
	 * @param exonEnd : a list of end positions of exons in the gene
	 * @param gIndex : target Index (genomic DNA index)
	 */
	/*
	private static boolean isExon(Vector<Integer> exonStart, Vector<Integer> exonEnd, int gIndex) {
		if(exonStart == null || exonEnd == null) 
			return false;

		for(int i=0;i<exonStart.size();i++) {
			int start = (exonStart.get(i)).intValue();
			int end = (exonEnd.get(i)).intValue();
			if(gIndex >= start && gIndex <= end) return true;
		}
		return false;
	}
	 */

	/**
	 * makes a cDNA indexr for each points in alignedPoints
	 * @param alignedPoints : target array of AlignedPoints
	 * @param startGIndex : start position on genomic DNA 
	 * @param endGIndex : end position on genomic DNA
	 * @param refFile : reference file
	 */
	private Vector<AlignedPoint> addCDnaNumber (Vector<AlignedPoint> alignedPoints, int startGIndex, int endGIndex, Reference refFile) {
		TreeMap<Integer, String> cdnaMap = new TreeMap<Integer, String>();

		int intCDnaStart = 0, intCDnaEnd = 0;
		int cdsIndex = 0; // which CDS block we are inside

		Vector<Integer> cDnaStart = refFile.getcDnaStart();
		Vector<Integer> cDnaEnd = refFile.getcDnaEnd();

		if(cDnaStart != null && cDnaEnd != null) {
			cdsIndex = 0;
			int cDNA = firstNumber-1;
			for(int i=0;i<cDnaStart.size();i++) {
				intCDnaStart = (cDnaStart.get(i)).intValue();
				intCDnaEnd = (cDnaEnd.get(i)).intValue();

				if(startGIndex > intCDnaEnd) {
					cDNA += (intCDnaEnd - intCDnaStart +1);
				}
				else if (startGIndex <= intCDnaEnd && startGIndex >= intCDnaStart) {
					cDNA += (startGIndex-intCDnaStart);
					cdsIndex = i;
					break;
				}
				else {
					cdsIndex = i;
					break;
				}
			}


			for(int i=startGIndex;i<=endGIndex;i++) {
				String tempCIndex = "c.";

				// Coding.
				if(i >= intCDnaStart && i <= intCDnaEnd) {
					cDNA++;
					tempCIndex += cDNA;
					cdnaMap.put(i, tempCIndex);

					if(i==intCDnaEnd) {
						if(cdsIndex < (cDnaStart.size()-1)) {
							cdsIndex++;
							intCDnaStart = (cDnaStart.get(cdsIndex)).intValue();
							intCDnaEnd = (cDnaEnd.get(cdsIndex)).intValue();
						}
					}
				}
				// Non-coding.
				else {
					if(cdsIndex==0 && i < intCDnaStart) { //5' of first CDS
						int offSet = intCDnaStart - i;
						if(firstNumber > 1) {
							tempCIndex += firstNumber + "-" + offSet;
						}
						else {
							tempCIndex += "-" + offSet;
						}
					}
					else if (cdsIndex == cDnaStart.size()-1 && i > intCDnaEnd) { //3' of last CDS
						int offSet = i-intCDnaEnd;
						tempCIndex += "*" + offSet;
					}

					else {	//intron 
						int leftOffset = i-(cDnaEnd.get(cdsIndex-1)).intValue();
						int rightOffset = (cDnaStart.get(cdsIndex)).intValue() - i;

						if(leftOffset <= rightOffset) 
							tempCIndex += cDNA + "+" + leftOffset;

						else 
							tempCIndex += (cDNA+1) + "-" + rightOffset;
					}
					cdnaMap.put(i, tempCIndex);
				}
			}

		}


		Vector<AlignedPoint> tempAlignedPoints = new Vector<AlignedPoint>();
		for(int i=0;i<alignedPoints.size();i++) {
			AlignedPoint tempPoint = alignedPoints.get(i);
			int tempGIndex = tempPoint.getGIndex();
			String stringTempCIndex = cdnaMap.get(tempGIndex);
			boolean coding;
			if(stringTempCIndex == null) {
				// Outside the numbered region (a reference gap at either end):
				// show it without a cDNA coordinate rather than failing the run.
				stringTempCIndex = "";
				coding = false;
			}
			else if(stringTempCIndex.contains("+") || stringTempCIndex.contains("-"))
				coding = false;
			else 
				coding = true;
			tempPoint.setStringCIndex(stringTempCIndex);
			tempPoint.setCoding(coding);

			//tempPoint.setExon(isExon(refFile.getExonStart(), refFile.getExonEnd(), tempGIndex));

			tempAlignedPoints.add(tempPoint);

		}
		return tempAlignedPoints;
	}

	public int getFirstNumber() {
		return firstNumber;
	}

	public void setFirstNumber(int firstNumber) {
		this.firstNumber = firstNumber;
	}

}
