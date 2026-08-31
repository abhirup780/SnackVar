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

import com.opaleye.snackvar.AlignedPoint;
import com.opaleye.snackvar.EquivExpression;
import com.opaleye.snackvar.Formatter;
import com.opaleye.snackvar.GanseqTrace;
import com.opaleye.snackvar.RootController;
import com.opaleye.snackvar.TwoPeaks;
import com.opaleye.snackvar.tools.SymbolTools;

public class VariantCallerFilter {

	private static final System.Logger LOG = System.getLogger(VariantCallerFilter.class.getName());
	private boolean fwdLoaded, revLoaded;
	private int startRange, endRange;
	private Vector<Variant> heteroIndelList;
	private GanseqTrace trimmedFwdTrace, trimmedRevTrace;
	private Vector<AlignedPoint> alignedPoints;
	private RootController rootController;
	
	public VariantCallerFilter(RootController rootController, Vector<Variant> heteroIndelList) {
		this.rootController = rootController;
		this.heteroIndelList = heteroIndelList;
		this.fwdLoaded = rootController.fwdLoaded;
		this.revLoaded = rootController.revLoaded;
		this.startRange = rootController.startRange;
		this.endRange = rootController.endRange;
		this.trimmedFwdTrace = rootController.trimmedFwdTrace;
		this.trimmedRevTrace = rootController.trimmedRevTrace; 
		this.alignedPoints = rootController.alignedPoints;
		
	}

	public TreeSet<Variant> getVariantList() {
		TreeSet<Variant> ret = makeVariantList();
		ret = comparisonFilter(ret);
		ret = compressedPeakFilter(ret);

		try {
			ret = makeHomoDelins(ret);
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "VariantCallerFilter", ex);
		}
		
		
		return ret;
	}

	private TreeSet<Variant> compressedPeakFilter(TreeSet<Variant> variantList) {
		TreeSet<Variant> ret = new TreeSet<Variant>();
		Vector<String> filteredHGVSList = new Vector<String>();
		if(fwdLoaded) {
			for(int i=0;i<trimmedFwdTrace.getSequenceLength();i++) {
				try {	// on any error just skip this base; not filtering is the safe default
					Vector<String> IUPACList = SymbolTools.IUPACtoSymbolList(trimmedFwdTrace.getSequence().charAt(i));
					if(IUPACList.size() != 2) continue;
					char refBase, nextBase;
					char base1 = IUPACList.get(0).charAt(0);
					char base2 = IUPACList.get(1).charAt(0);

					int index = rootController.formatter.fwdCoordinateMap.get(i+1);
					AlignedPoint point = alignedPoints.get(index-1);
					if(base1 == point.getRefChar()) {
						refBase = base1;
						nextBase = base2;
					}
					else if(base2 == point.getRefChar()) {
						refBase = base2;
						nextBase = base1;
					}
					else
						continue;

					int[] secondTrace = null;
					switch(nextBase) {
					case 'A': secondTrace = trimmedFwdTrace.getTraceA();break;
					case 'T': secondTrace = trimmedFwdTrace.getTraceT();break;
					case 'G': secondTrace = trimmedFwdTrace.getTraceG();break;
					case 'C': secondTrace = trimmedFwdTrace.getTraceC();break;
					}

					int initialPos = trimmedFwdTrace.getBaseCalls()[i];
					int direction;

					if(secondTrace[initialPos-1] < secondTrace[initialPos] && secondTrace[initialPos] < secondTrace[initialPos+1])
						direction = 1;
					else if(secondTrace[initialPos-1] > secondTrace[initialPos] && secondTrace[initialPos] > secondTrace[initialPos+1])
						direction = -1;
					else
						continue;	// peaks coincide, so there is nothing to separate; only filter clear cases



					for(int index2=index+direction; index2>=1 && index2<=alignedPoints.size(); index2+=direction) {
						AlignedPoint point2 = alignedPoints.get(index2-1);

						char refBase2 = point2.getRefChar();
						if(refBase2 != nextBase) break;
						if(point2.getFwdChar()==Formatter.gapChar) {
							filteredHGVSList.add(point.getStringCIndex() + refBase + ">" + nextBase);
							filteredHGVSList.add(point2.getStringCIndex() + "del" + nextBase);
						}
					}
				}
				catch(ArrayIndexOutOfBoundsException ae) { // an ambiguity code at the very end runs off the trace; skip it
					continue;
				}
				catch(Exception e) {
					//LOG.log(System.Logger.Level.DEBUG, "VariantCallerFilter", e);
					continue;
				}
			}
		}

		if(revLoaded) {
			for(int i=0;i<trimmedRevTrace.getSequenceLength();i++) {
				try {	// on any error just skip this base; not filtering is the safe default
					Vector<String> IUPACList = SymbolTools.IUPACtoSymbolList(trimmedRevTrace.getSequence().charAt(i));
					if(IUPACList.size() != 2) continue;
					char refBase, nextBase;
					char base1 = IUPACList.get(0).charAt(0);
					char base2 = IUPACList.get(1).charAt(0);

					int index = rootController.formatter.revCoordinateMap.get(i+1);
					AlignedPoint point = alignedPoints.get(index-1);
					if(base1 == point.getRefChar()) {
						refBase = base1;
						nextBase = base2;
					}
					else if(base2 == point.getRefChar()) {
						refBase = base2;
						nextBase = base1;
					}
					else
						continue;

					int[] secondTrace = null;
					switch(nextBase) {
					case 'A': secondTrace = trimmedRevTrace.getTraceA();break;
					case 'T': secondTrace = trimmedRevTrace.getTraceT();break;
					case 'G': secondTrace = trimmedRevTrace.getTraceG();break;
					case 'C': secondTrace = trimmedRevTrace.getTraceC();break;
					}

					int initialPos = trimmedRevTrace.getBaseCalls()[i];
					int direction;

					if(secondTrace[initialPos-1] < secondTrace[initialPos] && secondTrace[initialPos] < secondTrace[initialPos+1])
						direction = 1;
					else if(secondTrace[initialPos-1] > secondTrace[initialPos] && secondTrace[initialPos] > secondTrace[initialPos+1])
						direction = -1;
					else
						continue;	// peaks coincide, so there is nothing to separate; only filter clear cases



					for(int index2=index+direction; index2>=1 && index2<=alignedPoints.size(); index2+=direction) {
						AlignedPoint point2 = alignedPoints.get(index2-1);

						char refBase2 = point2.getRefChar();
						if(refBase2 != nextBase) break;
						if(point2.getRevChar()==Formatter.gapChar) {
							filteredHGVSList.add(point.getStringCIndex() + refBase + ">" + nextBase);
							filteredHGVSList.add(point2.getStringCIndex() + "del" + nextBase);
						}
					}
				}
				catch(Exception e) {
					//LOG.log(System.Logger.Level.DEBUG, "VariantCallerFilter", e);
					continue;
				}
			}
		}

		Iterator<Variant> iter = variantList.iterator();
		while(iter.hasNext()) {
			Variant v = iter.next();
			if(!filteredHGVSList.contains(v.getHGVS()))
				ret.add(v);
		}

		return ret;
	}

	// A homozygous indel called in the same region as a heterozygous one is
	// usually an artefact of a misalignment. When that happens the caller
	// retries with a higher gap-opening penalty.
	public boolean misAlignment(TreeSet<Variant> variantSet) {
		Iterator<Variant> iter = variantSet.iterator();
		while(iter.hasNext()) {
			Variant v1 = iter.next();
			if(v1 instanceof Indel && v1.zygosity.equals("hetero")) {
				Indel hetIndel = (Indel)v1;
				TreeSet<EquivExpression> equivSet = hetIndel.getEquivExpressionList();
				int hetMin = Integer.MAX_VALUE;
				int hetMax = Integer.MIN_VALUE;
				// Span covered by all of its equivalent expressions.
				Iterator<EquivExpression> iter2 = equivSet.iterator();
				while(iter2.hasNext()) {
					EquivExpression tempEquiv = iter2.next();
					if(hetMin > tempEquiv.getgIndex1())
						hetMin = tempEquiv.getgIndex1();
					if(hetMax < tempEquiv.getgIndex2())
						hetMax = tempEquiv.getgIndex2();
				}
				// Look for a homozygous indel whose span overlaps it.
				Iterator<Variant> iter3 = variantSet.iterator();
				while(iter3.hasNext()) {
					Variant v2 = iter3.next();
					if(v2 instanceof Indel && v2.zygosity.equals("homo")) {
						Indel homoIndel = (Indel)v2;
						TreeSet<EquivExpression> equivSet2 = homoIndel.getEquivExpressionList();
						int homoMin = Integer.MAX_VALUE;
						int homoMax = Integer.MIN_VALUE;
						// Span covered by all of its equivalent expressions.
						Iterator<EquivExpression> iter4 = equivSet2.iterator();
						while(iter4.hasNext()) {
							EquivExpression tempEquiv2 = iter4.next();
							if(homoMin > tempEquiv2.getgIndex1())
								homoMin = tempEquiv2.getgIndex1();
							if(homoMax < tempEquiv2.getgIndex2())
								homoMax = tempEquiv2.getgIndex2();
						}
						
						// Count ranges that are merely close, not just overlapping.
						// These must stay local: widening hetMin/hetMax themselves
						// would compound across every homozygous indel examined.
						int paddedHomoMin = homoMin - 50;
						int paddedHomoMax = homoMax + 50;
						int paddedHetMin = hetMin - 50;
						int paddedHetMax = hetMax + 50;

						if(!(paddedHetMax < paddedHomoMin || paddedHomoMax < paddedHetMin))
							return true;
					}
				}
			}
		}
		return false;
	}


	/**
	 * Makes a list of variants (SNVs, Indels)
	 * @return  variant list
	 */
	public TreeSet<Variant> makeVariantList() {
		alignedPoints = rootController.alignedPoints;
		TreeSet<Variant> variantList = new TreeSet<Variant>();
		Variant variant = null;

		// Where the heterozygous indel begins on each strand, 0-based. Past that
		// point the trace is a superposition of two alleles, so SNVs and
		// homozygous indels are only searched for on the near side of it.
		int fwdHeteroIndelStartPoint = alignedPoints.size();
		int revHeteroIndelStartPoint = -1;

		// Locate where each hetero indel starts.
		for(Variant heteroIndel:heteroIndelList) {
			if(heteroIndel.direction == 1) {
				fwdHeteroIndelStartPoint = heteroIndel.getAlignmentIndex()-1;	// 1-based to 0-based
			}
			else if(heteroIndel.direction == -1) {
				revHeteroIndelStartPoint = heteroIndel.getAlignmentIndex()-1;	// 1-based to 0-based
			}
		}

		// The same hetero indel seen on both strands was merged onto the reverse
		// call in RootController.detectHeteroIndel(); keep only that one.
		if(heteroIndelList.size() == 2) {
			Indel fwdIndel = (Indel)heteroIndelList.get(0);
			Indel revIndel = (Indel)heteroIndelList.get(1);
			
			
			if(fwdIndel.getHGVS().equals(revIndel.getHGVS())) {
				heteroIndelList = new Vector<Variant>();
				heteroIndelList.add(revIndel);
			}

		}
		variantList.addAll(heteroIndelList);
		

		//FWD SNV 
		for(int i=0;i<fwdHeteroIndelStartPoint;i++) {
			AlignedPoint ap = alignedPoints.get(i);

			if(fwdLoaded && ap.getFwdChar() != Formatter.gapChar && ap.getRefChar() != Formatter.gapChar) {	// indels are called separately
				// A plain A/T/G/C, i.e. a single peak.
				if(ap.getFwdChar()=='A' || ap.getFwdChar()=='T' || ap.getFwdChar()=='G' || ap.getFwdChar()=='C') {
					if(ap.getFwdChar() != ap.getRefChar()) {	
						variant = new SNV(rootController, ap.getRefChar(), ap.getFwdChar(), ap.getRevChar(), 1, ap.getStringCIndex(), (i+1), ap.getFwdTraceIndex(), ap.getRevTraceIndex(), ap.isCoding(), ap.getGIndex(), onTarget(i+1), "homo");
						variantList.add(variant);
					}
				}

				else {		//ambiguous symbol
					Vector<String> IUPACList = SymbolTools.IUPACtoSymbolList(ap.getFwdChar());
					for(String baseString:IUPACList) {
						char baseChar = baseString.charAt(0);
						if(ap.getRefChar() != baseChar) {
							variant = new SNV(rootController, ap.getRefChar(), baseChar, ap.getRevChar(), 1,  ap.getStringCIndex(), (i+1), ap.getFwdTraceIndex(), ap.getRevTraceIndex(), ap.isCoding(), ap.getGIndex(), onTarget(i+1), "hetero");
							variantList.add(variant);
						}
					}
				}
			}
		}

		//REV SNV 
		for(int i=revHeteroIndelStartPoint+1;i<alignedPoints.size();i++) {
			AlignedPoint ap = alignedPoints.get(i);

			if(revLoaded && ap.getRevChar() != Formatter.gapChar && ap.getRefChar() != Formatter.gapChar) {	// indels are called separately

				//ATGC
				if(ap.getRevChar()=='A' || ap.getRevChar()=='T' || ap.getRevChar()=='G' || ap.getRevChar()=='C') {
					if(ap.getRevChar() != ap.getRefChar()) {	
						variant = new SNV(rootController, ap.getRefChar(), ap.getFwdChar(), ap.getRevChar(), -1, ap.getStringCIndex(), (i+1), ap.getFwdTraceIndex(), ap.getRevTraceIndex(), ap.isCoding(), ap.getGIndex(), onTarget(i+1), "homo");
						if(variantList.contains(variant)) {
							variantList.remove(variant);
							variant.setHitCount(2); 
						}
						variantList.add(variant);
					}
				}

				else  {		//ambiguous symbol
					Vector<String> IUPACList = SymbolTools.IUPACtoSymbolList(ap.getRevChar());
					for(String baseString:IUPACList) {
						char baseChar = baseString.charAt(0);
						if(ap.getRefChar() != baseChar) {
							variant = new SNV(rootController, ap.getRefChar(), ap.getFwdChar(), baseChar, -1, ap.getStringCIndex(), (i+1), ap.getFwdTraceIndex(), ap.getRevTraceIndex(), ap.isCoding(), ap.getGIndex(), onTarget(i+1), "hetero");
							if(variantList.contains(variant)) {	
								variantList.remove(variant);
								variant.setHitCount(2); 
							}
							variantList.add(variant);
						}
					}
				}
			}
		}

		//Homo Insertion Call Logic (Fwd)
		if(fwdLoaded) 
			for(int i=0;i<fwdHeteroIndelStartPoint;i++) {
				AlignedPoint ap = alignedPoints.get(i);

				if(ap.getRefChar() == Formatter.gapChar && ap.getFwdChar() != Formatter.gapChar) {
					StringBuffer buffer = new StringBuffer();
					int j=i;
					for(;j<fwdHeteroIndelStartPoint;j++) {
						AlignedPoint ap2 = alignedPoints.get(j);
						if(ap2.getRefChar()==Formatter.gapChar  &&  ap2.getFwdChar() != Formatter.gapChar)
							buffer.append(ap2.getFwdChar());
						else {
							break;
						}
					}
					String insertedSeq = buffer.toString();

					int index2 =0;
					if(j<alignedPoints.size())
						index2 = j;
					else 
						index2 = j-1;

					// Decide whether this insertion is really a duplication.
					boolean duplication = false;
					int dupStartIndex = i-insertedSeq.length();
					if(dupStartIndex >= 0) {
						int k =0;
						for(;k<insertedSeq.length();k++) {
							AlignedPoint ap3 = alignedPoints.get(dupStartIndex + k);
							if(ap3.getFwdChar()!=insertedSeq.charAt(k))
								break;
						}
						if(k==insertedSeq.length()) duplication = true;
					}
					if(duplication) {
						variant = new Indel(rootController, "homo", 1, Indel.duplication, dupStartIndex+1, dupStartIndex +insertedSeq.length(), dupStartIndex+1, insertedSeq, onTarget(dupStartIndex+1));
					}
					else
						variant = new Indel(rootController, "homo", 1, Indel.insertion, i+1, j+1,i+1, insertedSeq, onTarget(i+1));
					variantList.add(variant);

					i=j;
				}
			}

		//Homo Insertion Call Logic (Rev)
		if(revLoaded) 
			for(int i=revHeteroIndelStartPoint+1;i<alignedPoints.size();i++) {
				AlignedPoint ap = alignedPoints.get(i);

				if(ap.getRefChar() == Formatter.gapChar  && ap.getRevChar() != Formatter.gapChar) {
					StringBuffer buffer = new StringBuffer();
					int j=i;
					for(;j<alignedPoints.size();j++) {
						AlignedPoint ap2 = alignedPoints.get(j);
						if(ap2.getRefChar()==Formatter.gapChar  && ap2.getRevChar() != Formatter.gapChar)
							buffer.append(ap2.getRevChar());
						else {
							break;
						}
					}
					String insertedSeq = buffer.toString();

					int index2 =0;
					if(j<alignedPoints.size())
						index2 = j;
					else 
						index2 = j-1;

					// Decide whether this insertion is really a duplication.
					boolean duplication = false;
					int dupStartIndex = i-insertedSeq.length();
					if(dupStartIndex >= 0) {
						int k =0;
						for(;k<insertedSeq.length();k++) {
							AlignedPoint ap3 = alignedPoints.get(dupStartIndex + k);
							if(ap3.getRevChar()!=insertedSeq.charAt(k))
								break;
						}
						if(k==insertedSeq.length()) duplication = true;
					}
					if(duplication) {
						variant = new Indel(rootController, "homo", -1, Indel.duplication, dupStartIndex+1, dupStartIndex +insertedSeq.length(), dupStartIndex+1, insertedSeq, onTarget(dupStartIndex+1));
					}
					else
						variant = new Indel(rootController, "homo", -1, Indel.insertion, i+1, j+1, i+1, insertedSeq, onTarget(i+1));

					if(variantList.contains(variant)) {	// seen on both strands: highlight it in both panes
						variantList.remove(variant);
						variant.setHitCount(2);
						variant.setFwdTraceChar(ap.getFwdChar());
					}
					variantList.add(variant);
					i=j;
				}
			}

		//Homo deletion Call (FWD)
		if(fwdLoaded) 
			for(int i=0;i<fwdHeteroIndelStartPoint;i++) {
				AlignedPoint ap = alignedPoints.get(i);

				if(ap.getFwdChar() == Formatter.gapChar && ap.getRefChar() != Formatter.gapChar) {
					int j=i;
					for(;j<fwdHeteroIndelStartPoint;j++) {
						AlignedPoint ap2 = alignedPoints.get(j);
						if(!(ap2.getFwdChar()==Formatter.gapChar  &&  ap2.getRefChar() != Formatter.gapChar))
							break;
					}

					if(i==0 || j==alignedPoints.size()) {	// overhang covered by one strand only; not a deletion
						i=j;
						continue;
					}

					variant = new Indel(rootController, "homo", 1, Indel.deletion, i+1, j,i+1, "", onTarget(i+1));
					variantList.add(variant);
					i=j;
				}
			}

		//Homo deletion Call (REV)
		if(revLoaded) 
			for(int i=revHeteroIndelStartPoint+1;i<alignedPoints.size();i++) {
				AlignedPoint ap = alignedPoints.get(i);

				if(ap.getRevChar() == Formatter.gapChar && ap.getRefChar() != Formatter.gapChar) {
					int j=i;
					for(;j<alignedPoints.size();j++) {
						AlignedPoint ap2 = alignedPoints.get(j);
						if(!(ap2.getRevChar()==Formatter.gapChar  &&  ap2.getRefChar() != Formatter.gapChar))
							break;
					}
					if(i==0 || j==alignedPoints.size()) {	// overhang covered by one strand only; not a deletion
						i=j;
						continue;
					}

					variant = new Indel(rootController, "homo", -1, Indel.deletion, i+1, j,i+1, "", onTarget(i+1));
					if(variantList.contains(variant)) {	// seen on both strands: highlight it in both panes
						variantList.remove(variant);
						variant.setHitCount(2);
						variant.setFwdTraceChar(ap.getFwdChar());
					}
					variantList.add(variant);
					i=j;
				}
			}
		return variantList;
	}

	/**
	 * Filters variant List
	 * @param variantList
	 * @return returns filtered variant list
	 */
	private TreeSet<Variant> comparisonFilter(TreeSet<Variant> variantList) {
		if(!(fwdLoaded&&revLoaded)) return variantList;

		TreeSet<Variant> tempList = new TreeSet<Variant>(variantList);

		Iterator<Variant> i = variantList.iterator();
		while(i.hasNext()) {
			Variant v = i.next();
			// Only variants seen once, inside the region both strands cover, are candidates.
			if(v.getHitCount() !=1 || v.getAlignmentIndex() < startRange || v.getAlignmentIndex() > endRange)
				continue;

			// Hetero indels are never filtered here.
			if(v instanceof Indel && ((Indel) v).getZygosity().equals("hetero")) {
				continue;
			}

			if(v instanceof SNV) {
				AlignedPoint ap = alignedPoints.get(v.getAlignmentIndex()-1);
				int oppositeSideQuality = 0;
				char oppositeSideChar =  'N';
				if(v.getDirection()==1) {
					oppositeSideQuality = ap.getRevQuality();
					oppositeSideChar  = ap.getRevChar();
				}
				else if(v.getDirection()==-1) {
					oppositeSideQuality = ap.getFwdQuality();
					oppositeSideChar = ap.getFwdChar();
				}
				// The other strand reads the reference base at good quality, so this is noise.
				if(oppositeSideQuality >= RootController.filterQualityCutoff && oppositeSideChar == ap.getRefChar()) {
					tempList.remove(v);
				}
			}
			else if (v instanceof Indel) {	
				Indel indelV = (Indel)v;
				if(indelV.getType()==Indel.deletion) {// filter single-base deletions
					//if(!indelV.getcIndex().equals(indelV.getCIndex2()))  
					//	continue;

					boolean remove = true;
					int counter = 0;
					while(indelV.getAlignmentIndex()-1+counter < alignedPoints.size()) {
						AlignedPoint ap = alignedPoints.get(indelV.getAlignmentIndex()-1+counter);
						int oppositeSideQuality = 0;
						char oppositeSideChar = 'N';
						if(indelV.getDirection()==1) {
							oppositeSideQuality = ap.getRevQuality();
							oppositeSideChar = ap.getRevChar();
						}
						else if(indelV.getDirection()==-1) {
							oppositeSideQuality = ap.getFwdQuality();
							oppositeSideChar = ap.getFwdChar();
						}
						if(!(oppositeSideQuality >= RootController.filterQualityCutoff && oppositeSideChar == ap.getRefChar())) {
							remove = false;
							break;
						}
						if(ap.getGIndex() == indelV.getgIndex2()) break;
						counter++;
					}
					if(remove) {
						tempList.remove(v);
					}
				}
				// Filters single-base insertions. Untested against real data: with an
				// insertion the opposite strand is a gap here, so it is unclear how
				// often this branch can fire.
				else if(indelV.getType()==Indel.insertion) {
					if(indelV.getIndelSeq().length() != 1)   
						continue;

					// Check the quality on the other strand either side of this point.
					int oppositeSideQuality1 = 0;
					int oppositeSideQuality2 = 0;
					char oppositeSideChar1 = 'N', oppositeSideChar2 = 'N';
					AlignedPoint ap1 = null, ap2 = null;
					try {
						ap1 = alignedPoints.get(indelV.getAlignmentIndex()-2);
						ap2 = alignedPoints.get(indelV.getAlignmentIndex());
						// If either lookup fails the qualities stay 0, so nothing is filtered.

						if(indelV.getDirection()==1) {
							oppositeSideQuality1 = ap1.getRevQuality();
							oppositeSideQuality2 = ap2.getRevQuality();
							oppositeSideChar1 = ap1.getRevChar();
							oppositeSideChar2 = ap2.getRevChar();
						}
						else if(indelV.getDirection()==-1) {
							oppositeSideQuality1 = ap1.getFwdQuality();
							oppositeSideQuality2 = ap2.getFwdQuality();
							oppositeSideChar1 = ap1.getFwdChar();
							oppositeSideChar2 = ap2.getFwdChar();
						}
					}
					catch (Exception ex) {
						LOG.log(System.Logger.Level.DEBUG, "VariantCallerFilter", ex);
					}

					if(oppositeSideQuality1 >= RootController.filterQualityCutoff && oppositeSideQuality2 >= RootController.filterQualityCutoff && oppositeSideChar1==ap1.getRefChar() && oppositeSideChar2 == ap2.getRefChar()) {
						tempList.remove(v);
					}
				}
			}
		}
		return tempList;
	}


	private TreeSet<Variant> makeHomoDelins (TreeSet<Variant> variantList) throws Exception {
		TreeSet<Variant> ret = new TreeSet<Variant>(variantList);
		TreeSet<Variant> tempVariantList = new TreeSet<Variant>();


		Variant[] variantArray = new Variant[variantList.size()];
		variantList.toArray(variantArray);

		boolean building = false;
		boolean v1v2Connected = false;
		boolean skipThisTime = false;

		for(int i=1;i<variantArray.length;i++) {
			// v1 was consumed by the group built on the previous iteration.
			if(skipThisTime) {
				skipThisTime = false;
				continue;
			}
			Variant v1 = variantArray[i-1];
			Variant v2 = variantArray[i];
			int v1_Rt_gIndex = 0;
			int v2_Lt_gIndex1 = 0;
			boolean v1MultipleExpression = false;
			boolean v2MultipleExpression = false;

			if(v1.zygosity.equals("hetero") || v2.zygosity.equals("hetero")) {	// skip unless both are homozygous
				v1v2Connected = false;
			}
			else {	// both homozygous
				if(v1 instanceof SNV) {
					v1_Rt_gIndex = v1.getgIndex();
				}
				else if (v1 instanceof Indel) {
					Indel indel1 = (Indel) v1;
					TreeSet<EquivExpression> eeList = indel1.getEquivExpressionList();
					if(eeList.size()>1) v1MultipleExpression = true;

					EquivExpression[] eeArray = new EquivExpression[eeList.size()];
					eeList.toArray(eeArray);
					EquivExpression ee = eeArray[eeArray.length-1];		// rightmost

					v1_Rt_gIndex = ee.getgIndex2();

					if(indel1.getType()==Indel.insertion) {
						v1_Rt_gIndex--;
					}
				}

				if(v2 instanceof SNV) {
					v2_Lt_gIndex1 = v2.getgIndex();
				}
				else if (v2 instanceof Indel) {
					Indel indel2 = (Indel) v2;
					TreeSet<EquivExpression> eeList = indel2.getEquivExpressionList();
					if(eeList.size()>1) v2MultipleExpression = true;

					EquivExpression[] eeArray = new EquivExpression[eeList.size()];
					eeList.toArray(eeArray);
					EquivExpression ee = eeArray[0];		// leftmost

					v2_Lt_gIndex1 = ee.getgIndex1();

					if(indel2.getType()==Indel.insertion) {
						v2_Lt_gIndex1++;
					}
				}

				// Adjacent or overlapping variants keep the group going. Homozygous
				// calls should not overlap, but two heterozygous SNVs can share a
				// position, so overlap is allowed for.
				if(v2_Lt_gIndex1 <= v1_Rt_gIndex + 1) {
					v1v2Connected = true;
				}
				else {
					v1v2Connected = false;
				}
			}

			if(building) {
				if(v1v2Connected) {
					tempVariantList.add(v2);
					if(v2MultipleExpression) {
						ret = updateVariantList(ret, tempVariantList);
						tempVariantList = new TreeSet<Variant>();
						building = false;
						skipThisTime = true;	// v2 is consumed; it must not act as v1 next round
					}
				}
				else if(!v1v2Connected) {
					ret = updateVariantList(ret, tempVariantList);
					tempVariantList = new TreeSet<Variant>();
					building = false;
					// v2 was not consumed here, so it must act as v1 next round.
				}
			}
			else if(!building) {
				if(v1v2Connected) {
					tempVariantList.add(v1);
					tempVariantList.add(v2);
					if(v2MultipleExpression) {
						ret = updateVariantList(ret, tempVariantList);
						tempVariantList = new TreeSet<Variant>();
						building = false;
						skipThisTime = true;	// v2 is consumed; it must not act as v1 next round
					}
					else {
						building = true;
					}
				}
			}
		}

		// A group still being accumulated when the pass ended was never emitted.
		if(building && !tempVariantList.isEmpty()) {
			ret = updateVariantList(ret, tempVariantList);
		}

		// The second pass starts from a clean slate; carrying state over from
		// the first would merge unrelated variants.
		tempVariantList = new TreeSet<Variant>();
		building = false;
		v1v2Connected = false;
		skipThisTime = false;

		// Merge runs of consecutive heterozygous SNVs into a delins.
		for(int i=1;i<variantArray.length;i++) {
			// v1 was consumed by the group built on the previous iteration.
			if(skipThisTime) {
				skipThisTime = false;
				continue;
			}
			Variant v1 = variantArray[i-1];
			Variant v2 = variantArray[i];
			int v1_Rt_gIndex = 0;
			int v2_Lt_gIndex1 = 0;

			// Skip unless both are heterozygous SNVs.
			if(v1.zygosity.equals("homo") || v2.zygosity.equals("homo") || v1 instanceof Indel || v2 instanceof Indel) {	
				v1v2Connected = false;
			}
			else {	// both heterozygous SNVs
				v1_Rt_gIndex = v1.getgIndex();
				v2_Lt_gIndex1 = v2.getgIndex();

				if(v2_Lt_gIndex1 == v1_Rt_gIndex + 1) {
					v1v2Connected = true;
				}
				else {
					v1v2Connected = false;
				}
			}

			if(building) {
				if(v1v2Connected) {
					tempVariantList.add(v2);
				}
				else if(!v1v2Connected) {
					ret = updateVariantList(ret, tempVariantList);
					tempVariantList = new TreeSet<Variant>();
					building = false;
				}
			}
			else if(!building) {
				if(v1v2Connected) {
					tempVariantList.add(v1);
					tempVariantList.add(v2);
					building = true;
				}
			}
		}

		if(building && !tempVariantList.isEmpty()) {
			ret = updateVariantList(ret, tempVariantList);
		}
		return ret;
	}

	// Merges every variant in targetVariantList into a single delins and records
	// it as the combined expression on each of them.
	private TreeSet<Variant> updateVariantList(TreeSet<Variant> originalVariantList, TreeSet<Variant> targetVariantList) throws Exception{
		TreeSet<Variant> ret = new TreeSet<Variant>(originalVariantList);

		// delinsString starts as the reference and has every variant applied to
		// it; comparing the two then yields the combined delins.
		// gIndex of every base in refString / delinsString, kept in step with them.
		Vector<Integer> refIndexList = new Vector<Integer>();
		Vector<Integer> delinsIndexList = new Vector<Integer>();

		StringBuilder refBuilder = new StringBuilder();
		for(int i=0;i<alignedPoints.size();i++) {
			AlignedPoint ap = alignedPoints.get(i);
			char refChar = ap.getRefChar();
			if(refChar != Formatter.gapChar) {
				refBuilder.append(refChar);
				refIndexList.add(ap.getGIndex());
				delinsIndexList.add(ap.getGIndex());
			}
		}
		String refString = refBuilder.toString();
		// delinsString starts as a copy of the reference, then each variant is applied.
		String delinsString = refString;

		Variant[] targetVariantArray = new Variant[targetVariantList.size()];
		targetVariantList.toArray(targetVariantArray);

		// Apply each variant in turn to delinsString and delinsIndexList.
		for(int i=0;i<targetVariantList.size();i++) {

			Variant v = targetVariantArray[i];
			// -1 means not found.
			int delinsListIndex1 = -1, delinsListIndex2 = -1;

			if(v instanceof SNV) {
				delinsListIndex2 = 0;
			}
			// Locate this variant's endpoints in the lists built above.
			for(int j=0;j<delinsIndexList.size();j++) {
				if(delinsIndexList.get(j) == v.getgIndex()) {
					delinsListIndex1 = j;
				}
				if(v instanceof Indel) {
					Indel indel = (Indel)v;
					if(delinsIndexList.get(j) == indel.getgIndex2()) {
						delinsListIndex2 = j;
					}
				}
			}

			if(delinsListIndex1 == -1 || delinsListIndex2 == -1) return ret;


			if(v instanceof SNV) {
				SNV snv = (SNV)v;
				char replacementChar;
				if(snv.direction == 1) 
					replacementChar = snv.getFwdTraceChar();
				else 
					replacementChar = snv.getRevTraceChar();

				delinsString = delinsString.substring(0,delinsListIndex1) + replacementChar + delinsString.substring(delinsListIndex1+1, delinsString.length()); 
			}
			else if(v instanceof Indel) {
				Indel indel = (Indel)v;
				if(indel.getType()==Indel.deletion) {
					delinsString = delinsString.substring(0,delinsListIndex1) + delinsString.substring(delinsListIndex2+1, delinsString.length());
					for(int j=delinsListIndex1;j<=delinsListIndex2;j++) {
						// Removing an element shifts the rest down, so removing the same
						// index repeatedly deletes the whole span.
						delinsIndexList.remove(delinsListIndex1);
					}
				}
				else if(indel.getType()==Indel.insertion) {
					delinsString = delinsString.substring(0, delinsListIndex1+1)+indel.getIndelSeq() + delinsString.substring(delinsListIndex2, delinsString.length());
					for(int j=0;j<indel.getIndelSeq().length();j++) {
						delinsIndexList.add(delinsListIndex1+1,  0);
					}
				}
				else if(indel.getType()==Indel.duplication) {
					delinsString = delinsString.substring(0, delinsListIndex2+1) + indel.getIndelSeq() + delinsString.substring(delinsListIndex2+1, delinsString.length());
					for(int j=0;j<indel.getIndelSeq().length();j++) {
						delinsIndexList.add(delinsListIndex2+1,  0);
					}
				}
			}
		}
		int leftPos = 0;
		int originalRightPos = refString.length()-1;
		int shiftedRightPos = delinsString.length()-1;


		for(;leftPos<refString.length() && leftPos<delinsString.length();leftPos++) {
			if(refString.charAt(leftPos) != delinsString.charAt(leftPos)) break;
		}
		leftPos--;	// last position where both still agree

		while(originalRightPos>leftPos && shiftedRightPos>leftPos) {
			if(refString.charAt(originalRightPos) != delinsString.charAt(shiftedRightPos)) break;
			originalRightPos--;
			shiftedRightPos--;
		}

		// originalRightPos / shiftedRightPos: the first position that differs
		// scanning from the right, or wherever they met leftPos.
		String combinedHgvs = "";
		String insertedSeq = "";

		if(leftPos == shiftedRightPos) {	//deletion
			// A merged group should never reduce to a plain deletion; it is a delins.
		}
		else if(leftPos == originalRightPos ) { //insertion
			// A merged group should never reduce to a plain deletion; it is a delins.
		}

		else { //delins
			String cIndex1 = getCIndexFromGIndex(refIndexList.get(leftPos+1));
			String cIndex2 = getCIndexFromGIndex(refIndexList.get(originalRightPos));
			if(cIndex1.equals(cIndex2)) 
				combinedHgvs = cIndex1;
			else
				combinedHgvs = cIndex1 + "_" +cIndex2;
			combinedHgvs += "delins";
			for(int i=leftPos+1;i<=shiftedRightPos;i++) {
				insertedSeq += delinsString.charAt(i);
			}
			combinedHgvs += insertedSeq;

			// Built only to derive the amino-acid change.
			Indel indel = new Indel(rootController, "homo", 1, Indel.delins, getAlignedIndexFromGIndex(refIndexList.get(leftPos+1)), 
					getAlignedIndexFromGIndex(refIndexList.get(originalRightPos)), getAlignedIndexFromGIndex(refIndexList.get(leftPos+1)), insertedSeq, true);
			combinedHgvs += ", " + indel.getAAChange();
		}

		for(int i=0;i<targetVariantArray.length;i++) {
			Variant v = targetVariantArray[i];
			ret.remove(v);
			v.setCombinedExpression(combinedHgvs);
			v.makeTableViewProperties();
			ret.add(v);
		}

		return ret;
	}

	private String getCIndexFromGIndex(int gIndex) {
		String ret= "";
		for(int i=0;i<alignedPoints.size();i++) {
			AlignedPoint ap = alignedPoints.get(i);
			if(ap.getGIndex() == gIndex) {
				ret = ap.getStringCIndex();
				break;
			}
		}
		return ret;
	}

	private int getAlignedIndexFromGIndex(int gIndex) throws Exception {
		Vector<AlignedPoint> aps = rootController.alignedPoints;
		for(int i=0;i<aps.size();i++) {
			AlignedPoint ap = aps.get(i);
			if(ap.getGIndex()==gIndex) return (i+1);	// found: always at least 1
		}
		return 0;
	}


	private boolean onTarget(int index) {
		if(!fwdLoaded || !revLoaded) return true;
		if(index>=startRange && index<=endRange) return true;
		else return false;
	}




}
