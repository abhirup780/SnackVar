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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import com.opaleye.snackvar.variants.Indel;
import com.opaleye.snackvar.variants.Variant;
import com.opaleye.snackvar.ui.Theme;

public class HeteroTrace extends com.opaleye.snackvar.GanseqTrace {

	private static final System.Logger LOG = System.getLogger(HeteroTrace.class.getName());
	private final int maxIndelSize = 1000;
	private final double indelCutoff = 0.6;
	private String result = "not run yet";

	
	//1: insertion, -1 : deletion
	private int insOrDel = -1;
	private int indelSize = 0;
	char[] refSeq = null;
	char[] subtractedSeq = null;	// the region carrying double peaks
	char[] subtractedSeq2 = null;  // the region without them


	private int doublePeakStartIndex = 1;	// 1-based, in trace coordinates
	private int alignedDoublePeakStartIndex = 1;	// 1-based, the same point as an alignedPoints index
	private int alignedIndelStartIndex = 0;
	private int alignedIndelEndIndex = 0;


	public HeteroTrace(GanseqTrace trace, RootController rootController) {
		this.rootController = rootController;
		this.direction = trace.direction;
		this.traceA = trace.traceA;
		this.traceT = trace.traceT;
		this.traceG = trace.traceG;
		this.traceC = trace.traceC;
		this.traceLength = trace.traceLength;
		this.traceHeight = trace.traceHeight;
		this.sequence = trace.sequence;
		this.sequenceLength = trace.sequenceLength; 
		this.qCalls = trace.qCalls;
		this.baseCalls = trace.baseCalls;

		this.transformedA = trace.transformedA;
		this.transformedT = trace.transformedT;
		this.transformedG = trace.transformedG;
		this.transformedC = trace.transformedC;
		this.alignedRegionStart = trace.alignedRegionStart;
		this.alignedRegionEnd = trace.alignedRegionEnd;
	}

	/**
	 * returns char1 - char2
	 * char1 is the observed base, possibly an ambiguity code; char2 is the
	 * reference base.
	 *
	 * A single peak returns itself regardless of char2. A double peak returns
	 * the other allele once char2 is removed from it, or N when char2 is not
	 * one of the two.
	 */
	private char subtract(char char1, char char2) {
		char ret = 'N';

		// Single peak.
		if(char1=='A' || char1 == 'T' || char1 == 'G' || char1 =='C') {
			ret = char1;
		}

		// Double peak. If neither allele matches the reference -- two different
		// SNVs at one position, which is rare -- fall through and return N.
		else if(char1 == 'R') {
			if(char2 == 'A') ret = 'G';
			else if(char2 == 'G') ret = 'A';
		}
		else if(char1 == 'Y') {
			if(char2 == 'C') ret = 'T';
			else if(char2 == 'T') ret = 'C';
		}
		else if(char1 == 'K') {
			if(char2 == 'T') ret = 'G';
			else if(char2 == 'G') ret = 'T';
		}
		else if (char1 == 'M') {
			if(char2 == 'A') ret = 'C';
			else if(char2 == 'C') ret = 'A';
		}
		else if (char1 == 'S') {
			if(char2 == 'C') ret = 'G';
			else if(char2 == 'G') ret = 'C';
		}
		else if ( char1 == 'W') {
			if(char2 == 'A') ret = 'T';
			else if(char2 == 'T') ret = 'A';
		}

		return ret;

	}


	/**
	 * Returns heteroIndel variant if detected
	 * if not detected, returns nulll
	 */
	public Variant detectHeteroIndel() {
		Variant variant = null;

		/*
		 * Parameters for second peak detection 
		 */
		char[] originalSeq = sequence.toCharArray();

		TreeMap<Integer, Integer> fwdMap = rootController.formatter.fwdCoordinateMap;
		TreeMap<Integer, Integer> revMap = rootController.formatter.revCoordinateMap;

		////////////////////////////
		// Which bases carry a second peak.
		////////////////////////////
		boolean[] secondPeakExist = new boolean[sequenceLength] ;

		for(int i=0;i<sequenceLength;i++) {
			char baseChar = sequence.charAt(i);
			if(baseChar=='A' || baseChar=='T' || baseChar=='G' || baseChar=='C' || baseChar == Formatter.gapChar) {
				secondPeakExist[i] = false;
			}
			else 
				secondPeakExist[i] = true;
		}


		////////////////////////////
		// Find where the heterozygous indel starts.
		////////////////////////////
		int maxLRIndex = 0, maxRLIndex = 0;
		double maxLR = 0, maxRL = 0;
		double maxLt =0, maxRt = 0;

		// Bases excluded at each end when searching for the ratio peak.
		int skip = 10;
		if(direction == 1) {
			// From i onwards is the double-peak region; i is 0-based.
			for(int i=1;i<sequenceLength;i++) {
				// left: start .. i-1
				double left = score(secondPeakExist,0,i-1, -1);
				// right: i .. end
				double right = score(secondPeakExist,i,sequenceLength-1, 1);
				double RL = right/left;
				if(RL > maxRL && i>=skip && i<sequenceLength-skip) {
					maxRL = RL;
					maxRLIndex = i+1; // stored 1-based
					maxRt = right;
					maxLt = left;
				}
			}
		}


		else if(direction == -1) {
			// Up to and including i is the double-peak region; i is 0-based.
			for(int i =0;i<sequenceLength;i++) {
				double left = score(secondPeakExist,0,i, -1);
				double right = score(secondPeakExist,i+1,sequenceLength-1, 1);
				double LR = left/right;
				if(LR > maxLR && i>=skip && i<sequenceLength-skip) {
					maxLR = LR;
					maxLRIndex = i+1;	// stored 1-based
					maxRt = right;
					maxLt = left;
				}
			}
		}
		if(direction==1) {
			if(maxRL<2.0 || maxRt<0.5) return null;
			doublePeakStartIndex = maxRLIndex;
		}
		else {
			if(maxLR<2.0 || maxLt<0.5) return null;
			doublePeakStartIndex = maxLRIndex;
		}

		// Past this point a heterozygous indel is suspected.
		
		
		
		
		////////////////////////////
		// Build the reference strand and the subtracted strand.
		////////////////////////////
		try {

			if(direction == 1) {
				subtractedSeq = new char[sequenceLength-maxRLIndex+1];
				subtractedSeq2 = new char[maxRLIndex-1];
			}
			else if (direction == -1){
				subtractedSeq = new char[maxLRIndex];
				subtractedSeq2 = new char[sequenceLength-maxLRIndex];
			}
			int subtractedSeqCounter = 0;
			int subtractedSeq2Counter = 0;
			refSeq = new char[sequenceLength];

			// i is 0-based; mappedNo and the coordinate maps below are all 1-based.
			for (int i = 0; i < sequenceLength; i++)
			{
				int mappedNo = 0;
				Integer i_mappedNo = null;
				if(direction == FORWARD) {
					i_mappedNo = fwdMap.get(i+1);
				}
				else {
					i_mappedNo = revMap.get(i+1);
				}
				AlignedPoint ap = null;
				// Outside the aligned region this stays null, and the base is treated as N.
				if(i_mappedNo != null) {
					mappedNo = i_mappedNo.intValue();
					ap = rootController.alignedPoints.get(mappedNo-1);
				}

				//RefSeq
				// The base in the reference file itself, as opposed to refSeq, which
				// holds whichever of the two strands matches the reference.
				char realRefChar = 'N';
				if(ap != null) 
					realRefChar = ap.getRefChar();


				/* Not this: refSeq is not the reference itself, but whichever of the
				   two observed strands agrees with it.
				refSeq[i] = refChar;
				 */

				// Single peak: both strands read the same base. Double peak:
				// subtract the strand matching the reference to get the other one.
				char subChar;
				if(originalSeq[i]=='A' || originalSeq[i] == 'T' || originalSeq[i] == 'G' || originalSeq[i] =='C') {
					subChar = originalSeq[i];
				}
				else {
					subChar = subtract(originalSeq[i], realRefChar);
				}

				refSeq[i] = realRefChar;

				// Only the search window contributes to the subtracted strand.
				if((direction ==1 && (i+1) >= maxRLIndex) || (direction==-1) && ((i+1) <= maxLRIndex)) {
					subtractedSeq[subtractedSeqCounter++] = subChar;
				}
				else {
					subtractedSeq2[subtractedSeq2Counter++] = subChar;
				}
			}

			/*
		Decide only whether this is an insertion or a deletion, and apply the
		score cutoff. The exact call is made afterwards.
		Sliding comparison : Reference Seq (not first seq) VS subtracted Seq 
			 */
			result = "";
			if(direction == 1) {
				int max = -1;
				int maxGap = 0;
				insOrDel = -1;

				//deletion 1~maxIndelSize
				for(int gap=1; gap<=maxIndelSize; gap++) {
					int score = 0;
					for(int i=0; i<subtractedSeq.length-gap;i++) {
						// Only unambiguous A/T/G/C matches count towards the score.
						if(subtractedSeq[i]=='A' || subtractedSeq[i] == 'T' || subtractedSeq[i] == 'G' || subtractedSeq[i] =='C')
							if(refSeq[maxRLIndex-1+i+gap] == subtractedSeq[i]) 
								score++;
					}
					result += String.format("Deletion Gap : %d, Score : %d\n", gap, score);
					if(score>max) {
						max = score;
						maxGap = gap;
					}
				}

				//insertion 1~maxIndelSize
				for(int gap=1; gap<=maxIndelSize; gap++) {
					int score = 0;
					for(int i=0; i<subtractedSeq.length-gap;i++) {

						// Only unambiguous A/T/G/C matches count towards the score.
						if(subtractedSeq[i+gap]=='A' || subtractedSeq[i+gap] == 'T' || subtractedSeq[i+gap] == 'G' || subtractedSeq[i+gap] =='C')
							if(refSeq[maxRLIndex-1+i] == subtractedSeq[i+gap]) 
								score++;
					}
					result += String.format("Insertion Gap : %d, Score : %d\n", gap, score);
					if(score>max) {
						insOrDel = 1;
						max = score;
						maxGap = gap;
					}
				}

				//Applying Cutoff

				// Denominator: unambiguous bases only.
				int nonAmbiguousSymbolCount = 0;
				for(int i=0;i<subtractedSeq.length;i++) {
					if(subtractedSeq[i]=='A' || subtractedSeq[i] == 'T' || subtractedSeq[i] == 'G' || subtractedSeq[i] =='C')
						nonAmbiguousSymbolCount++;
				}
				double f_score = 0;

				if(nonAmbiguousSymbolCount != maxGap) // guard against a zero denominator
					f_score = (double)max/(nonAmbiguousSymbolCount-maxGap);

				if(f_score < indelCutoff) return null;

				result = "Gap : " + maxGap + '\n' + result;
				if(insOrDel == 1) result = "Insertion" + result;
				else result = "Deletion " + result;

				indelSize = maxGap;
			}

			else if(direction == -1) {
				int max = -1;
				int maxGap = 0;

				//deletion 1~maxIndelSize
				for(int gap=1; gap<=maxIndelSize; gap++) {
					int score = 0;
					for(int i=0; i<subtractedSeq.length-gap;i++) {
						// Only unambiguous A/T/G/C matches count towards the score.
						if(subtractedSeq[gap+i]=='A' || subtractedSeq[gap+i] == 'T' || subtractedSeq[gap+i] == 'G' || subtractedSeq[gap+i] =='C')
							if(refSeq[i] == subtractedSeq[gap+i]) 
								score++;
					}
					result += String.format("Deletion Gap : %d, Score : %d\n", gap, score);
					if(score>max) {
						max = score;
						maxGap = gap;
					}
				}

				//insertion 1~maxIndelSize
				for(int gap=1; gap<=maxIndelSize; gap++) {
					int score = 0;
					for(int i=0; i<subtractedSeq.length-gap;i++) {

						// Only unambiguous A/T/G/C matches count towards the score.
						if(subtractedSeq[i]=='A' || subtractedSeq[i] == 'T' || subtractedSeq[i] == 'G' || subtractedSeq[i] =='C')
							if(refSeq[i+gap] == subtractedSeq[i]) 
								score++;
					}
					result += String.format("Insertion Gap : %d, Score : %d\n", gap, score);
					if(score>max) {
						insOrDel = 1;
						max = score;
						maxGap = gap;
					}
				}
				//Applying Cutoff
				// Denominator: unambiguous bases only.
				int nonAmbiguousSymbolCount = 0;
				for(int i=0;i<subtractedSeq.length;i++) {
					if(subtractedSeq[i]=='A' || subtractedSeq[i] == 'T' || subtractedSeq[i] == 'G' || subtractedSeq[i] =='C')
						nonAmbiguousSymbolCount++;
				}
				double f_score = 0;
				if(nonAmbiguousSymbolCount != maxGap) // guard against a zero denominator
					f_score = (double)max/(nonAmbiguousSymbolCount-maxGap);

				if(f_score < indelCutoff) return null;

				result = "Gap : " + maxGap + '\n' + result;
				if(insOrDel == 1) result = "Insertion " + result;
				else result = "Deletion" + result;
				indelSize = maxGap;
			}
		}
		catch(Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "HeteroTrace", ex);
			return null;
		}

		
		// Now make the precise indel call.
		
		TreeMap<Integer, Integer> coordiMap = null;
		String s_refSeq = new String(refSeq);
		String s_subtractedSeq = new String(subtractedSeq);
		String indelSeq = "";
		int type = -1;
		if(direction==1) {
			coordiMap = rootController.formatter.fwdCoordinateMap;
		}
		else {
			coordiMap = rootController.formatter.revCoordinateMap;
		}
		try {
			int refSeqIndex=0; // 0-based
			int subSeqIndex=0; // 0-based
			int indelStartIndex=0, indelEndIndex=0;	// 1-based

			if(insOrDel == -1) {
				if(direction==1) {
					// All indices here are 1-based.
					indelStartIndex = doublePeakStartIndex; 
					indelEndIndex = indelStartIndex + indelSize - 1; 

					if(indelEndIndex > s_refSeq.length()) return null;

					// Detect a delins; a plain deletion is the default.
					for(refSeqIndex=indelStartIndex-1; refSeqIndex+indelSize < refSeq.length && subSeqIndex < subtractedSeq.length && indelEndIndex <= s_refSeq.length(); ) {

						// Stop only after delinsCutoff consecutive matches; a single
						// match used to end the search, which truncated delins calls
						// with a long inserted sequence.
						boolean pass = true;
						int matchCount = 0;

						try	{
							int refSeqIndex2 = refSeqIndex;
							int subSeqIndex2 = subSeqIndex;
							for(int i=0;i<rootController.delinsCutoff;i++) { 
								if(refSeq[refSeqIndex2+indelSize] == subtractedSeq[subSeqIndex2])
									matchCount++;
								refSeqIndex2++;
								subSeqIndex2++;
							}
						}
						catch(Exception e) {
							pass = false;
						}
						if(!pass) {	// fewer than delinsCutoff bases left; fall back to a single match
							if(refSeq[refSeqIndex+indelSize] == subtractedSeq[subSeqIndex]) break;
						}
						else {
							if(matchCount == rootController.delinsCutoff)
								break;
						}
						
						indelSeq += subtractedSeq[subSeqIndex];
						refSeqIndex++;	
						subSeqIndex++;  
						indelEndIndex++;
					}
					if(refSeqIndex==indelStartIndex-1) {
						type = Indel.deletion;
						indelSeq = s_refSeq.substring(doublePeakStartIndex-1, doublePeakStartIndex-1+indelSize);
					}
					else type = Indel.delins;

				}
				else if(direction == -1) {
					// All indices here are 1-based.
					indelStartIndex = doublePeakStartIndex-indelSize+1; 
					indelEndIndex = doublePeakStartIndex; 
					if(indelStartIndex<0) return null;

					// Detect a delins; a plain deletion is the default.
					subSeqIndex = s_subtractedSeq.length()-1;
					for(refSeqIndex=doublePeakStartIndex-1-indelSize; refSeqIndex>=0 && subSeqIndex>=0 && indelStartIndex>=1; ) {
						
						
						// Stop only after delinsCutoff consecutive matches; a single
						// match used to end the search, which truncated delins calls
						// with a long inserted sequence.
						boolean pass = true;
						int matchCount = 0;

						try	{
							int refSeqIndex2 = refSeqIndex;
							int subSeqIndex2 = subSeqIndex;
							for(int i=0;i<rootController.delinsCutoff;i++) { 
								if(refSeq[refSeqIndex2] == subtractedSeq[subSeqIndex2])
									matchCount++;
								refSeqIndex2--;
								subSeqIndex2--;
							}
						}
						catch(Exception e) {
							pass = false;
						}
						if(!pass) {	// fewer than delinsCutoff bases left; fall back to a single match
							if(refSeq[refSeqIndex] == subtractedSeq[subSeqIndex]) break;
						}
						else {
							if(matchCount == rootController.delinsCutoff)
								break;
						}
						
						
						//if(refSeq[refSeqIndex] == subtractedSeq[subSeqIndex]) break;
						
						
						indelSeq = subtractedSeq[subSeqIndex] + indelSeq;
						refSeqIndex--;	
						subSeqIndex--;  
						indelStartIndex--;
					}
					if(refSeqIndex==doublePeakStartIndex-1-indelSize) {
						type = Indel.deletion;
						indelSeq = s_refSeq.substring(doublePeakStartIndex-indelSize, doublePeakStartIndex);
					}
					else type = Indel.delins;
				}
			}

			else if(insOrDel == 1) {
				boolean duplication = true;

				if (direction == 1) { 
					// Endpoints on the assumption this is a duplication.
					indelStartIndex = doublePeakStartIndex-indelSize;
					indelEndIndex = doublePeakStartIndex - 1;
					if(indelStartIndex<1) return null;

					for(subSeqIndex=0;subSeqIndex<indelSize;subSeqIndex++) {
						if(subtractedSeq[subSeqIndex] != refSeq[indelStartIndex-1+subSeqIndex]) {
							duplication = false;
							break;
						}
					}
					indelSeq = s_subtractedSeq.substring(0, indelSize);
					
					if(duplication == true) {
						type = Indel.duplication;
					}
					else {
						// Not a duplication, so an insertion or a delins. Re-derive the
						// endpoints on the assumption of an insertion.
						indelStartIndex = doublePeakStartIndex-1;
						indelEndIndex = doublePeakStartIndex;

						//delins detection
						subSeqIndex = indelSize;

						for(refSeqIndex=doublePeakStartIndex-1; refSeqIndex<refSeq.length && subSeqIndex<subtractedSeq.length && indelEndIndex<=refSeq.length;) {
							
							
							boolean pass = true;
							int matchCount = 0;
							try	{
								int refSeqIndex2 = refSeqIndex;
								int subSeqIndex2 = subSeqIndex;
								for(int i=0;i<rootController.delinsCutoff;i++) { 
									if(refSeq[refSeqIndex2] == subtractedSeq[subSeqIndex2])
										matchCount++;
									refSeqIndex2++;
									subSeqIndex2++;
								}
							}
							catch(Exception e) {
								pass = false;
							}
							if(!pass) {	// fewer than delinsCutoff bases left; fall back to a single match
								if(refSeq[refSeqIndex] == subtractedSeq[subSeqIndex]) break;
							}
							else {
								if(matchCount == rootController.delinsCutoff)
									break;
							}

							//if(refSeq[refSeqIndex] == subtractedSeq[subSeqIndex]) break;
							
							indelSeq += subtractedSeq[subSeqIndex];
							refSeqIndex++;	
							subSeqIndex++;  
							indelEndIndex++;
						}
						if(refSeqIndex==doublePeakStartIndex-1) type = Indel.insertion;
						else {
							type = Indel.delins;
							// Insertions and delins are numbered differently: an insertion
							// is bracketed by the bases either side of it, whereas a delins
							// spans the first and last deleted base, as a deletion does.
							indelStartIndex++;
							indelEndIndex--;
						}
					}
				}

				else if(direction == -1) {
					// Endpoints on the assumption this is a duplication.
					indelStartIndex = doublePeakStartIndex+1; 
					indelEndIndex = indelStartIndex + indelSize - 1; 

					if(indelEndIndex > s_refSeq.length()) return null;

					for(int i=0;i<indelSize;i++) {
						if(subtractedSeq[subtractedSeq.length-indelSize+i] != refSeq[indelStartIndex-1+i]) {
							duplication = false;
							break;
						}
					}
					indelSeq = s_subtractedSeq.substring(s_subtractedSeq.length()-indelSize, s_subtractedSeq.length());
					
					if(duplication == true) {
						type = Indel.duplication;
					}
					else {
						// Not a duplication, so an insertion or a delins. Re-derive the
						// endpoints on the assumption of an insertion.
						indelStartIndex = doublePeakStartIndex;
						indelEndIndex = indelStartIndex + 1;
					
						//delins detection
						subSeqIndex = s_subtractedSeq.length()-1-indelSize;
						for(refSeqIndex=doublePeakStartIndex-1; refSeqIndex>=0 && subSeqIndex>=0 && indelStartIndex >=1; ) {

							boolean pass = true;
							int matchCount = 0;

							try	{
								int refSeqIndex2 = refSeqIndex;
								int subSeqIndex2 = subSeqIndex;
								for(int i=0;i<rootController.delinsCutoff;i++) { 
									if(refSeq[refSeqIndex2] == subtractedSeq[subSeqIndex2])
										matchCount++;
									refSeqIndex2--;
									subSeqIndex2--;
								}
							}
							catch(Exception e) {
								pass = false;
							}
							if(!pass) {	// fewer than delinsCutoff bases left; fall back to a single match
								if(refSeq[refSeqIndex] == subtractedSeq[subSeqIndex]) break;
							}
							else {
								if(matchCount == rootController.delinsCutoff)
									break;
							}
							
							//if(refSeq[refSeqIndex] == subtractedSeq[subSeqIndex]) break;

							
							
							indelSeq = subtractedSeq[subSeqIndex] + indelSeq;
							refSeqIndex--;	
							subSeqIndex--;  
							indelStartIndex--;
						}
						if(refSeqIndex==doublePeakStartIndex-1) type = Indel.insertion;
						else {
							type = Indel.delins;
							// Insertions and delins are numbered differently: an insertion
							// is bracketed by the bases either side of it, whereas a delins
							// spans the first and last deleted base, as a deletion does.
							indelStartIndex++;
							indelEndIndex--;
						}
					}
				}
			}


			// Any endpoint falling outside the aligned region makes the call unusable.
			int maxIndex = Integer.max(Integer.max(indelStartIndex, indelEndIndex), doublePeakStartIndex);
			int minIndex = Integer.min(Integer.min(indelStartIndex, indelEndIndex), doublePeakStartIndex);
			
			if(maxIndex > alignedRegionEnd || minIndex < alignedRegionStart) return null;
			
			Integer mappedIndelStart = coordiMap.get(indelStartIndex);
			Integer mappedIndelEnd = coordiMap.get(indelEndIndex);
			Integer mappedDoublePeakStart = coordiMap.get(doublePeakStartIndex);
			if(mappedIndelStart == null || mappedIndelEnd == null || mappedDoublePeakStart == null) {
				// One of the endpoints has no column in the alignment, so the
				// call cannot be placed on the reference.
				return null;
			}
			alignedIndelStartIndex = mappedIndelStart;
			alignedIndelEndIndex = mappedIndelEnd;
			alignedDoublePeakStartIndex = mappedDoublePeakStart;


			if (direction ==1)
				//variant = new Indel(direction, Indel.hetero, type, coordi1, coordi2, s_seq, alignedDoublePeakStartIndex, ap3.getFwdTraceIndex(), ap3.getRevTraceIndex(), ap3.getFwdChar(), Formatter.gapChar, ap1.getGIndex(), ap2.getGIndex(), ap1.isCoding(), ap2.isCoding(), true);
				variant = new Indel(rootController, "hetero", direction, type, alignedIndelStartIndex, alignedIndelEndIndex, alignedDoublePeakStartIndex, indelSeq, true);
			else if (direction == -1)
				//variant = new Indel(direction, Indel.hetero, type, coordi1, coordi2, s_seq, alignedDoublePeakStartIndex, ap3.getFwdTraceIndex(), ap3.getRevTraceIndex(), Formatter.gapChar, ap3.getRevChar(), ap1.getGIndex(), ap2.getGIndex(), ap1.isCoding(), ap2.isCoding(), true);
				variant = new Indel(rootController, "hetero", direction, type, alignedIndelStartIndex, alignedIndelEndIndex, alignedDoublePeakStartIndex, indelSeq, true);

			//variant.setHitCount(2);
		}
		catch(RuntimeException ex) {
			return null;
		}

		return variant;
	}

	/**
	 * @param selectedPosition
	 * @return
	 */


	/**
	 * Chromatogram for the heterozygous-indel view: the same trace, annotated
	 * with the two resolved strands (the reference-matching strand above, the
	 * subtracted strand below) plus alignment and cDNA coordinates.
	 *
	 * @param highlightRefSeq bases of the reference strand to highlight (1-based)
	 * @param highlightSubSeq bases of the subtracted strand to highlight (1-based)
	 */
	public BufferedImage getHeteroImage(Formatter formatter, TreeSet<Integer> highlightRefSeq, TreeSet<Integer> highlightSubSeq) {
		final int width = Integer.max(1, traceLength * traceWidth);
		final int height = traceHeight + 50;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setBackground(Theme.traceBackground());
		g.clearRect(0, 0, width, height);

		TreeMap<Integer, Integer> fwdMap = formatter.fwdCoordinateMap;
		TreeMap<Integer, Integer> revMap = formatter.revCoordinateMap;

		final Font baseFont = new Font(Font.SANS_SERIF, Font.BOLD, 11);
		final Font rulerFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

		g.setColor(Theme.baseA());
		for (int i = 0; i < traceLength - 1; i++) {
			g.drawLine(i * traceWidth, transformedA[i], (i + 1) * traceWidth, transformedA[i + 1]);
		}
		g.setColor(Theme.baseT());
		for (int i = 0; i < traceLength - 1; i++) {
			g.drawLine(i * traceWidth, transformedT[i], (i + 1) * traceWidth, transformedT[i + 1]);
		}
		g.setColor(Theme.baseG());
		for (int i = 0; i < traceLength - 1; i++) {
			g.drawLine(i * traceWidth, transformedG[i], (i + 1) * traceWidth, transformedG[i + 1]);
		}
		g.setColor(Theme.baseC());
		for (int i = 0; i < traceLength - 1; i++) {
			g.drawLine(i * traceWidth, transformedC[i], (i + 1) * traceWidth, transformedC[i + 1]);
		}

		int subtractedSeqCounter = 0;
		int subtractedSeqCounter2 = 0;
		for (int i = 0; i < sequenceLength; i++) {
			Integer i_mappedNo = (direction == FORWARD) ? fwdMap.get(i + 1) : revMap.get(i + 1);
			// Outside the aligned region there is no mapped column, so no coordinates.
			int mappedNo = (i_mappedNo == null) ? 0 : i_mappedNo;
			AlignedPoint ap = (i_mappedNo == null) ? null : rootController.alignedPoints.get(mappedNo - 1);

			char refChar = refSeq[i];
			int xPos = baseCalls[i] * traceWidth;

			g.setFont(baseFont);
			g.setColor(Theme.forBase(refChar));
			g.drawString(Character.toString(refChar), Integer.max(0, xPos - 3), traceHeight + 13);

			// Second strand: the subtracted sequence inside the indel search
			// window, the unaffected sequence outside it.
			boolean insideWindow = (direction == 1 && (i + 1) >= doublePeakStartIndex)
					|| (direction == -1 && (i + 1) <= doublePeakStartIndex);
			char subtractedChar;
			if (insideWindow) {
				if (subtractedSeqCounter >= subtractedSeq.length) continue;
				subtractedChar = subtractedSeq[subtractedSeqCounter++];
			} else {
				if (subtractedSeqCounter2 >= subtractedSeq2.length) continue;
				subtractedChar = subtractedSeq2[subtractedSeqCounter2++];
			}
			g.setColor(Theme.forBase(subtractedChar));
			g.drawString(Character.toString(subtractedChar), Integer.max(0, xPos - 3), traceHeight + 23);

			if (mappedNo != 0 && mappedNo % 10 == 1) {
				g.setColor(Theme.axis());
				g.setFont(rulerFont);
				g.drawLine(xPos, traceHeight + 26, xPos, traceHeight + 30);
				g.drawString(Integer.toString(mappedNo), Integer.max(0, xPos - 3), traceHeight + 40);
				if (ap != null) {
					g.drawString(ap.getStringCIndex(), Integer.max(0, xPos - 3), traceHeight + 50);
				}
			}
		}

		g.setColor(Theme.highlight());
		g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
		if (!highlightRefSeq.isEmpty()) {
			int xPos1 = baseCalls[highlightRefSeq.first() - 1] * traceWidth;
			int xPos2 = baseCalls[highlightRefSeq.last() - 1] * traceWidth;
			g.fillRect(Integer.max(0, xPos1 - 6 * traceWidth), traceHeight + 3,
					(xPos2 - xPos1) + 12 * traceWidth, 12);
		}
		if (!highlightSubSeq.isEmpty()) {
			int xPos1 = baseCalls[highlightSubSeq.first() - 1] * traceWidth;
			int xPos2 = baseCalls[highlightSubSeq.last() - 1] * traceWidth;
			g.fillRect(Integer.max(0, xPos1 - 6 * traceWidth), traceHeight + 14,
					(xPos2 - xPos1) + 12 * traceWidth, 12);
		}
		g.dispose();

		return image;
	}

	/** returns average value of an input array
	 * 
	 * @param array : target array
	 * @param startIndex : start index
	 * @param endIndex : end index
	 */
	protected double score (boolean[] secondPeakExist, int startIndex, int endIndex, int direction) {
		double ret = -1;
		double offset = 0.2;
		if(startIndex > endIndex) return ret;
		int sum = 0;
		int denominator = 0;

		int weightCounter = 10;
		if(direction == 1) {
			for(int i=startIndex;i<=endIndex;i++) {
				denominator += weightCounter;
				if(secondPeakExist[i]) {
					sum += weightCounter;
				}
				if(weightCounter>1) weightCounter--;
			}
		}
		else if(direction == -1) {
			for(int i=endIndex;i>=startIndex;i--) {
				denominator += weightCounter;
				if(secondPeakExist[i]) {
					sum += weightCounter;
				}
				if(weightCounter>1) weightCounter--;
			}
		}
		ret = (double)sum / denominator;
		ret += offset;
		return ret;
	}


	/**
	 * Getters and setters for member variables 
	 */

	public int getInsOrDel() {
		return insOrDel;
	}
	public void setInsOrDel(int insOrDel) {
		this.insOrDel = insOrDel;
	}
	public int getIndelSize() {
		return indelSize;
	}
	public void setIndelSize(int indelSize) {
		this.indelSize = indelSize;
	}
	public char[] getSubtractedSeq() {
		return subtractedSeq;
	}
	public char[] getSubtractedSeq2() {
		return subtractedSeq2;
	}

	public void setSubtractedSeq(char[] subtractedSeq) {
		this.subtractedSeq = subtractedSeq;
	}
	public int getDoublePeakStartIndex() {
		return doublePeakStartIndex;
	}
	public char[] getRefSeq() {
		return refSeq;
	}
	public void setRefSeq(char[] refSeq) {
		this.refSeq = refSeq;
	}
	public int getAlignedDoublePeakStartIndex() {
		return alignedDoublePeakStartIndex;
	}
	public int getAlignedIndelStartIndex() {
		return alignedIndelStartIndex;
	}
	public int getAlignedIndelEndIndex() {
		return alignedIndelEndIndex;
	}



}
