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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.TreeMap;

import org.biojava.bio.program.abi.ABITrace;
import org.biojava.bio.seq.DNATools;

import com.opaleye.snackvar.tools.SymbolTools;
import com.opaleye.snackvar.ui.Theme;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/**
 * An ABI trace plus the derived data the rest of the application works with:
 * the four channel intensities, the base calls and their quality scores, and
 * the chromatogram images shown in the trace panes.
 */
public class GanseqTrace implements Cloneable {

	public static final int FORWARD = 1;
	public static final int REVERSE = -1;
	public static final int originalTraceHeight = 110;
	public static final int traceWidth = 2;

	/** Vertical room under the trace for the base letters and the position ruler. */
	protected static final int LABEL_STRIP_HEIGHT = 30;

	protected int traceHeight = 0;

	protected int direction = FORWARD; // starts forward; makeTrimmedTrace flips it for reverse reads
	protected int[] traceA = null;
	protected int[] traceT = null;
	protected int[] traceG = null;
	protected int[] traceC = null;
	protected int traceLength = 0;
	protected String sequence = null;
	protected int sequenceLength = 0;
	protected int[] qCalls = null;
	protected int[] baseCalls = null;

	protected int[] transformedA = null;
	protected int[] transformedT = null;
	protected int[] transformedG = null;
	protected int[] transformedC = null;
	protected int maxHeight = -1;
	protected double ratio = 1.0;

	protected int alignedRegionStart = 0;
	protected int alignedRegionEnd = 0;

	protected RootController rootController;

	public GanseqTrace() {
	}

	public GanseqTrace(File ABIFile, RootController rootController) throws Exception {
		this.rootController = rootController;

		ABITrace tempTrace = new ABITrace(ABIFile);
		traceA = tempTrace.getTrace(DNATools.a());
		traceT = tempTrace.getTrace(DNATools.t());
		traceG = tempTrace.getTrace(DNATools.g());
		traceC = tempTrace.getTrace(DNATools.c());
		traceLength = Integer.min(Integer.min(traceA.length, traceT.length),
				Integer.min(traceG.length, traceC.length));
		sequence = tempTrace.getSequence().seqString().toUpperCase();
		sequenceLength = sequence.length();
		qCalls = tempTrace.getQcalls();
		baseCalls = tempTrace.getBasecalls();

		transformTrace();
	}

	private void transformTrace() {
		maxHeight = -1;
		for (int i = 0; i < traceLength; i++) {
			if (traceA[i] > maxHeight) maxHeight = traceA[i];
			if (traceT[i] > maxHeight) maxHeight = traceT[i];
			if (traceG[i] > maxHeight) maxHeight = traceG[i];
			if (traceC[i] > maxHeight) maxHeight = traceC[i];
		}

		transformedA = new int[traceLength];
		transformedT = new int[traceLength];
		transformedG = new int[traceLength];
		transformedC = new int[traceLength];

		traceHeight = (int) (originalTraceHeight * ratio);

		// A flat trace would divide by zero here; leave the transformed channels
		// at the baseline rather than producing NaN coordinates.
		if (maxHeight <= 0) {
			return;
		}
		double imageHeightRatio = (double) traceHeight / (double) maxHeight;

		for (int i = 0; i < traceLength; i++) {
			transformedA[i] = (int) ((maxHeight - traceA[i]) * imageHeightRatio);
			transformedT[i] = (int) ((maxHeight - traceT[i]) * imageHeightRatio);
			transformedG[i] = (int) ((maxHeight - traceG[i]) * imageHeightRatio);
			transformedC[i] = (int) ((maxHeight - traceC[i]) * imageHeightRatio);
		}
	}

	/**
	 * Creates the canvas every chromatogram is drawn on.
	 *
	 * <p>The original used {@code TYPE_BYTE_INDEXED}, whose 256-entry palette
	 * dithered the trace curves into visible speckle, and drew with the default
	 * aliased pipeline. An RGB surface with antialiasing and pure stroke
	 * geometry is what makes the curves read as smooth lines.
	 */
	private static Graphics2D newCanvas(BufferedImage image, int width, int height) {
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setBackground(Theme.traceBackground());
		g.clearRect(0, 0, width, height);
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		return g;
	}

	private static Font baseFont() {
		return new Font(Font.SANS_SERIF, Font.BOLD, 11);
	}

	private static Font rulerFont() {
		return new Font(Font.SANS_SERIF, Font.PLAIN, 10);
	}

	/** Draws the four channel curves, offset horizontally by {@code startOffset}. */
	private void drawChannels(Graphics2D g, int startOffset) {
		drawChannel(g, transformedA, Theme.baseA(), startOffset);
		drawChannel(g, transformedT, Theme.baseT(), startOffset);
		drawChannel(g, transformedG, Theme.baseG(), startOffset);
		drawChannel(g, transformedC, Theme.baseC(), startOffset);
	}

	private void drawChannel(Graphics2D g, int[] channel, Color color, int startOffset) {
		g.setColor(color);
		for (int i = 0; i < traceLength - 1; i++) {
			g.drawLine(startOffset + i * traceWidth, channel[i],
					startOffset + (i + 1) * traceWidth, channel[i + 1]);
		}
	}

	public BufferedImage getDefaultImage() {
		int width = Integer.max(1, traceLength * traceWidth);
		int height = traceHeight + LABEL_STRIP_HEIGHT;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = newCanvas(image, width, height);

		drawChannels(g, 0);

		for (int i = 0; i < sequenceLength; i++) {
			char[] baseChar = { sequence.charAt(i) };
			int xPos = baseCalls[i] * traceWidth;

			g.setFont(baseFont());
			g.setColor(Theme.forBase(baseChar[0]));
			g.drawChars(baseChar, 0, 1, Integer.max(0, xPos - 3), traceHeight + 13);

			if ((i + 1) % 10 == 1) {
				g.setColor(Theme.axis());
				g.setFont(rulerFont());
				g.drawLine(xPos, traceHeight + 16, xPos, traceHeight + 20);
				g.drawString(Integer.toString(i + 1), xPos - 3, traceHeight + 30);
			}
		}
		g.dispose();
		return image;
	}

	/**
	 * Chromatogram with the regions outside the trim points shaded out.
	 *
	 * @param startTrimPosition left trim position, in image pixels
	 * @param endTrimPosition   right trim position, in image pixels
	 */
	public Image getTrimmingImage(int startTrimPosition, int endTrimPosition) {
		BufferedImage originalImage = getDefaultImage();
		Graphics2D g = originalImage.createGraphics();

		g.setColor(Theme.highlight());
		g.setComposite(AlphaComposite.SrcOver.derive(0.18f));

		int fullWidth = traceWidth * traceLength;
		g.fillRect(0, 0, startTrimPosition + 1, traceHeight);
		int shadeStart = Integer.min(endTrimPosition, fullWidth - 1);
		g.fillRect(shadeStart, 0, fullWidth - endTrimPosition, traceHeight);
		g.dispose();

		return SwingFXUtils.toFXImage(originalImage, null);
	}

	/**
	 * Chromatogram drawn in alignment coordinates, optionally highlighting a base
	 * or a range.
	 *
	 * @param option 0: no shading, 1: single base at {@code startPosition},
	 *               2: the range {@code startPosition}..{@code endPosition}
	 */
	public BufferedImage getShadedImage(Formatter formatter, int option, int startPosition, int endPosition) {
		int newTraceLength;
		int startOffset;

		if (direction == FORWARD) {
			newTraceLength = formatter.fwdNewLength;
			startOffset = formatter.fwdStartOffset;
		} else {
			newTraceLength = formatter.revNewLength;
			startOffset = formatter.revStartOffset;
		}

		int width = Integer.max(1, newTraceLength);
		int height = traceHeight + LABEL_STRIP_HEIGHT;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = newCanvas(image, width, height);

		TreeMap<Integer, Integer> coordinateMap = (direction == FORWARD)
				? formatter.fwdCoordinateMap
				: formatter.revCoordinateMap;

		drawChannels(g, startOffset);

		for (int i = 0; i < sequenceLength; i++) {
			if (i + 1 < alignedRegionStart || i + 1 > alignedRegionEnd) {
				continue;
			}

			char[] baseChar = { sequence.charAt(i) };
			int xPos = startOffset + baseCalls[i] * traceWidth;

			g.setFont(baseFont());
			g.setColor(Theme.forBase(baseChar[0]));
			g.drawChars(baseChar, 0, 1, Integer.max(0, xPos - 3), traceHeight + 13);

			Integer mappedNo = coordinateMap.get(i + 1);
			if (mappedNo != null && mappedNo % 10 == 1) {
				g.setColor(Theme.axis());
				g.setFont(rulerFont());
				g.drawLine(xPos, traceHeight + 16, xPos, traceHeight + 20);
				g.drawString(Integer.toString(mappedNo), xPos - 3, traceHeight + 30);
			}
		}

		if (option == 1 || option == 2) {
			// startPosition/endPosition index baseCalls; guard so a stale
			// selection cannot throw while merely repainting.
			if (startPosition >= 0 && startPosition < baseCalls.length
					&& endPosition >= 0 && endPosition < baseCalls.length) {
				g.setColor(Theme.highlight());
				g.setComposite(AlphaComposite.SrcOver.derive(0.18f));
				int x = startOffset + (baseCalls[startPosition] - 5) * traceWidth;
				int shadeWidth = (option == 1)
						? 10 * traceWidth
						: (baseCalls[endPosition] - baseCalls[startPosition] + 10) * traceWidth;
				g.fillRect(x, 0, shadeWidth, traceHeight + LABEL_STRIP_HEIGHT);
			}
		}

		g.dispose();
		return image;
	}

	/** Vertical intensity scale drawn beside a trace. */
	public Image getRulerImage() {
		int width = 28;
		int height = Integer.max(1, traceHeight);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = newCanvas(image, width, height);
		g.setColor(Theme.axis());
		g.setFont(rulerFont());

		if (maxHeight > 0) {
			double imageHeightRatio = (double) traceHeight / (double) maxHeight;
			for (int i = 1000; i <= maxHeight; i += 1000) {
				int yPos = (int) ((maxHeight - i) * imageHeightRatio);
				g.drawString(Integer.toString(i), 0, yPos);
			}
		}
		g.dispose();
		return SwingFXUtils.toFXImage(image, null);
	}

	/**
	 * Cuts the trace down to the region between the two trim positions.
	 *
	 * @param startTrimPosition left trim position, in image pixels
	 * @param endTrimPosition   right trim position, in image pixels
	 * @param complement        reverse-complement the result (reverse reads)
	 */
	public void makeTrimmedTrace(int startTrimPosition, int endTrimPosition, boolean complement) throws Exception {
		int[] oldA = traceA;
		int[] oldT = traceT;
		int[] oldG = traceG;
		int[] oldC = traceC;

		String oldSequence = sequence;
		int oldSequenceLength = sequenceLength;
		int[] oldBaseCalls = baseCalls;
		int[] oldQCalls = qCalls;

		StringBuilder buffer = new StringBuilder();
		if (startTrimPosition != -1) {
			startTrimPosition /= traceWidth;
		}
		endTrimPosition /= traceWidth;

		// Clamp to the sample range: a trim position derived from a stale, more
		// zoomed-in image would otherwise index past the channel arrays.
		startTrimPosition = Integer.max(-1, Integer.min(startTrimPosition, oldA.length - 1));
		endTrimPosition = Integer.max(startTrimPosition + 1, Integer.min(endTrimPosition, oldA.length));

		traceLength = endTrimPosition - startTrimPosition - 1; // both endpoints excluded
		if (traceLength <= 0) {
			throw new Exception("Trimming would leave an empty trace. Please widen the retained region.");
		}

		traceA = new int[traceLength];
		traceT = new int[traceLength];
		traceG = new int[traceLength];
		traceC = new int[traceLength];

		for (int i = startTrimPosition + 1; i < endTrimPosition; i++) {
			traceA[i - (startTrimPosition + 1)] = oldA[i];
			traceT[i - (startTrimPosition + 1)] = oldT[i];
			traceG[i - (startTrimPosition + 1)] = oldG[i];
			traceC[i - (startTrimPosition + 1)] = oldC[i];
		}
		for (int i = 0; i < oldSequenceLength; i++) {
			if (oldBaseCalls[i] > startTrimPosition && oldBaseCalls[i] < endTrimPosition) {
				buffer.append(oldSequence.charAt(i));
			}
		}

		sequence = buffer.toString().toUpperCase();
		sequenceLength = sequence.length();
		if (sequenceLength == 0) {
			throw new Exception("Trimming would leave no base calls. Please widen the retained region.");
		}
		qCalls = new int[sequenceLength];
		baseCalls = new int[sequenceLength];

		int count = 0;
		for (int i = 0; i < oldSequenceLength; i++) {
			if (oldBaseCalls[i] > startTrimPosition && oldBaseCalls[i] < endTrimPosition) {
				qCalls[count] = oldQCalls[i];
				baseCalls[count] = oldBaseCalls[i] - (startTrimPosition + 1);
				count++;
			}
		}

		transformTrace();

		applyAmbiguousSymbol();
		if (complement) {
			makeComplement();
		}
	}

	/** Reverse-complements the trace in place, flipping every derived array. */
	public void makeComplement() throws IllegalArgumentException {
		int[] newQcalls = new int[sequenceLength];
		int[] newBaseCalls = new int[sequenceLength];
		int[] newA = new int[traceLength];
		int[] newT = new int[traceLength];
		int[] newG = new int[traceLength];
		int[] newC = new int[traceLength];

		try {
			sequence = SymbolTools.getComplementString(sequence);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Failed to reverse-complement the trace: " + ex.getMessage(), ex);
		}

		for (int i = 0; i < sequenceLength; i++) {
			newQcalls[i] = qCalls[sequenceLength - 1 - i];
			newBaseCalls[i] = (traceLength - 1) - baseCalls[sequenceLength - 1 - i];
		}

		for (int i = 0; i < traceLength; i++) {
			newA[i] = traceT[traceLength - 1 - i];
			newT[i] = traceA[traceLength - 1 - i];
			newG[i] = traceC[traceLength - 1 - i];
			newC[i] = traceG[traceLength - 1 - i];
		}
		qCalls = newQcalls;
		baseCalls = newBaseCalls;
		traceA = newA;
		traceT = newT;
		traceG = newG;
		traceC = newC;
		direction = (direction == FORWARD) ? REVERSE : FORWARD;

		transformTrace();
	}

	public TwoPeaks getTwoPeaks(int basePosition, double cutOff) {
		return getTwoPeaks_ruleBasedFiltering(basePosition, cutOff);
	}

	/**
	 * Finds the tallest and second-tallest channel at a base call, and decides
	 * whether the second peak is a genuine superimposed base.
	 *
	 * <p>A neighbouring base whose peak is still rising (or already falling)
	 * through this sample looks like a second peak but is not one; those are
	 * filtered out. Only clear-cut cases are rejected — anything ambiguous is
	 * kept, so the filter never hides a real variant.
	 */
	public TwoPeaks getTwoPeaks_ruleBasedFiltering(int basePosition, double cutOff) {
		int[] baseHeights = new int[4];
		int position = baseCalls[basePosition];
		boolean secondPeakExist = false;

		// A base call can sit on the last sample of a trimmed trace.
		position = Integer.max(0, Integer.min(position, traceLength - 1));

		baseHeights[0] = traceA[position];
		baseHeights[1] = traceT[position];
		baseHeights[2] = traceG[position];
		baseHeights[3] = traceC[position];

		int maxValue = -1;
		int secondMaxValue = -1;
		int maxIndex = 0;
		int secondMaxIndex = 0;

		for (int j = 0; j < 4; j++) {
			if (baseHeights[j] > maxValue) {
				maxValue = baseHeights[j];
				maxIndex = j;
			}
		}

		for (int j = 0; j < 4; j++) {
			if (j == maxIndex) {
				continue;
			}
			if (baseHeights[j] > secondMaxValue) {
				secondMaxValue = baseHeights[j];
				secondMaxIndex = j;
			}
		}

		if (maxValue != 0 && (secondMaxValue / (double) maxValue >= cutOff)) {
			secondPeakExist = true;

			try {
				int[] targetTrace = null;
				// 1: rising to the right, -1: rising to the left,
				// 2: rising both ways, 0: flat or falling both ways
				int slope = 0;

				switch (secondMaxIndex) {
				case 0: targetTrace = traceA; break;
				case 1: targetTrace = traceT; break;
				case 2: targetTrace = traceG; break;
				case 3: targetTrace = traceC; break;
				default: break;
				}

				if (position == 0) {
					slope = 1;
				} else if (position == traceLength - 1) {
					slope = -1;
				} else {
					if (targetTrace[position - 1] < secondMaxValue && secondMaxValue < targetTrace[position + 1]) {
						slope = 1;
					} else if (targetTrace[position - 1] > secondMaxValue && secondMaxValue > targetTrace[position + 1]) {
						slope = -1;
					} else if (targetTrace[position - 1] > secondMaxValue && secondMaxValue < targetTrace[position + 1]) {
						slope = 2;
					}
				}

				int smallIncrement = 10;
				int bigIncrement = 20;

				boolean leftPeakFound = false;
				boolean rightPeakFound = false;

				// Require room for about seven samples before declaring a
				// neighbouring peak: bases sit roughly 9-12 samples apart, so a
				// real neighbour always has that much space. This stops the very
				// last base from being filtered out after a short rise.
				if ((slope == 1 || slope == 2) && position + 7 < traceLength) {
					int rightEnd = (basePosition == sequenceLength - 1)
							? traceLength - 1
							: baseCalls[basePosition + 1];

					int i = position + 1;
					for (; i <= rightEnd; i++) {
						int increment = targetTrace[i] - targetTrace[i - 1];
						if (i == (position + 2) || i == (rightEnd - 1)) {
							if (increment < smallIncrement) break;
						} else if (i > position + 2 && i < rightEnd - 1) {
							if (increment < bigIncrement) break;
						}
					}
					// Ran to the end without breaking: rose the whole way.
					if (i > rightEnd) {
						rightPeakFound = true;
					}
				}
				if ((slope == -1 || slope == 2) && position - 7 >= 0) {
					int leftEnd = (basePosition == 0) ? 0 : baseCalls[basePosition - 1];

					int i = position - 1;
					for (; i >= leftEnd; i--) {
						int increment = targetTrace[i] - targetTrace[i + 1];
						if (i == (leftEnd + 1) || i == (position - 2)) {
							if (increment < smallIncrement) break;
						} else if (i > (leftEnd + 1) && i < (position - 2)) {
							if (increment < bigIncrement) break;
						}
					}
					if (i < leftEnd) {
						leftPeakFound = true;
					}
				}
				if (slope == 1 && rightPeakFound) {
					secondPeakExist = false;
				} else if (slope == -1 && leftPeakFound) {
					secondPeakExist = false;
				} else if (slope == 2 && rightPeakFound && leftPeakFound) {
					secondPeakExist = false;
				}
			} catch (RuntimeException ex) {
				// Filtering is an optimisation, not a correctness requirement:
				// on any indexing surprise keep the second peak.
			}
		}
		return new TwoPeaks(SymbolTools.numberToBase(maxIndex), SymbolTools.numberToBase(secondMaxIndex),
				maxValue, secondMaxValue, secondPeakExist);
	}

	/** Rewrites the sequence, substituting IUPAC ambiguity codes where two peaks coincide. */
	public void applyAmbiguousSymbol() {
		StringBuilder buffer = new StringBuilder(sequenceLength);
		double cutoff = (rootController != null)
				? rootController.secondPeakCutoff
				: RootController.defaultSecondPeakCutoff;
		for (int i = 0; i < sequenceLength; i++) {
			TwoPeaks twoPeaks = getTwoPeaks(i, cutoff);
			if (twoPeaks.secondPeakExist()) {
				buffer.append(SymbolTools.makeAmbiguousSymbol(twoPeaks.getFirstBase(), twoPeaks.getSecondBase()));
			} else {
				buffer.append(twoPeaks.getFirstBase());
			}
		}
		sequence = buffer.toString();
	}

	/**
	 * Suggested 5' trim position, in image pixels, or -1 to keep the whole start.
	 * Slides a window over the quality scores and trims back to the first base
	 * that clears the cutoff.
	 */
	public int getFrontTrimPosition() {
		int scoreTrimPosition = -1;
		int ret = -1;
		final int windowSize = 5;
		final int scoreCutOff = 25;
		boolean qualityPointFound = false;

		int qualitySearchLength = Integer.min(100, sequenceLength - windowSize);
		if (qualitySearchLength <= 0) {
			return -1;
		}

		try {
			int basePosition = -1;

			for (int i = 0; i < qualitySearchLength; i++) {
				int sum = 0;
				for (int j = 0; j < windowSize; j++) {
					sum += qCalls[i + j];
				}
				double avgScore = sum / (double) windowSize;

				if (avgScore >= scoreCutOff) {
					qualityPointFound = true;
					for (basePosition = i + windowSize - 1; basePosition >= i; basePosition--) {
						if (qCalls[basePosition] < scoreCutOff - 10) {
							break;
						}
					}
					if (basePosition == -1) {
						scoreTrimPosition = -1;
					} else {
						// basePosition tops out at sequenceLength-2, so the +1 is safe.
						scoreTrimPosition = (baseCalls[basePosition] + baseCalls[basePosition + 1]) / 2;
						scoreTrimPosition *= traceWidth;
					}
					break;
				}
			}

			if (!qualityPointFound) {
				basePosition = qualitySearchLength - 1;
				scoreTrimPosition = (baseCalls[basePosition] + baseCalls[basePosition + 1]) / 2;
				scoreTrimPosition *= traceWidth;
			}

			ret = scoreTrimPosition;

			// Nothing trimmed by quality, but the 5' end has unbasecalled trace
			// hanging off it: cut that.
			if ((ret == -1) && (baseCalls[0] > 20)) {
				ret = (baseCalls[0] - 3) * traceWidth;
			}
			return ret;
		} catch (RuntimeException ex) {
			// Trimming is a suggestion the user confirms; on any surprise, suggest nothing.
			return ret;
		}
	}

	/** Suggested 3' trim position, in image pixels; the full width means no trim. */
	public int getTailTrimPosition() {
		int scoreTrimPosition = traceLength * traceWidth;
		int ret = traceLength * traceWidth;
		final int windowSize = 20;
		final int scoreCutOff = 25;
		boolean qualityPointFound = false;

		int qScoreSearchLength = Integer.min(sequenceLength - windowSize, 2000);
		if (qScoreSearchLength <= 0) {
			return ret;
		}

		try {
			int basePosition = sequenceLength;
			for (int i = sequenceLength - 1; i >= sequenceLength - qScoreSearchLength; i--) {
				int sum = 0;
				for (int j = 0; j < windowSize; j++) {
					sum += qCalls[i - j];
				}
				double avgScore = sum / (double) windowSize;

				if (avgScore >= scoreCutOff) {
					qualityPointFound = true;
					for (basePosition = i - windowSize + 1; basePosition <= i; basePosition++) {
						if (qCalls[basePosition] < scoreCutOff - 10) {
							break;
						}
					}

					if (basePosition == sequenceLength) {
						scoreTrimPosition = traceLength * traceWidth;
					} else {
						scoreTrimPosition = (baseCalls[basePosition] + baseCalls[basePosition - 1]) / 2;
						scoreTrimPosition *= traceWidth;
					}
					break;
				}
			}

			// Quality never recovered anywhere in the searched window.
			if (!qualityPointFound) {
				basePosition = sequenceLength - qScoreSearchLength;
				scoreTrimPosition = (baseCalls[basePosition] + baseCalls[basePosition - 1]) / 2;
				scoreTrimPosition *= traceWidth;
			}

			ret = scoreTrimPosition;

			// Nothing trimmed by quality, but the 3' end has unbasecalled trace
			// hanging off it: cut that.
			if ((ret == traceLength * traceWidth) && (baseCalls[sequenceLength - 1] + 20 < traceLength)) {
				ret = (baseCalls[sequenceLength - 1] + 3) * traceWidth;
			}
			return ret;
		} catch (RuntimeException ex) {
			return ret;
		}
	}

	public int getDirection() {
		return direction;
	}

	public int[] getTraceA() {
		return traceA;
	}

	public int[] getTraceT() {
		return traceT;
	}

	public int[] getTraceG() {
		return traceG;
	}

	public int[] getTraceC() {
		return traceC;
	}

	public int getTraceLength() {
		return traceLength;
	}

	public String getSequence() {
		return sequence;
	}

	public int getSequenceLength() {
		return sequenceLength;
	}

	public int[] getQCalls() {
		return qCalls;
	}

	public int[] getBaseCalls() {
		return baseCalls;
	}

	public int getAlignedRegionStart() {
		return alignedRegionStart;
	}

	public void setAlignedRegionStart(int alignedRegionStart) {
		this.alignedRegionStart = alignedRegionStart;
	}

	public int getAlignedRegionEnd() {
		return alignedRegionEnd;
	}

	public void setAlignedRegionEnd(int alignedRegionEnd) {
		this.alignedRegionEnd = alignedRegionEnd;
	}

	public int[] getqCalls() {
		return qCalls;
	}

	public void setqCalls(int[] qCalls) {
		this.qCalls = qCalls;
	}

	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	public void setBaseCalls(int[] baseCalls) {
		this.baseCalls = baseCalls;
	}

	public void setSequenceLength(int sequenceLength) {
		this.sequenceLength = sequenceLength;
	}

	public void zoomIn() {
		if (ratio >= 6.0) {
			return;
		}
		ratio += 0.5;
		transformTrace();
	}

	public void zoomOut() {
		if (ratio <= 0.5) {
			return;
		}
		ratio -= 0.5;
		transformTrace();
	}

	public void zoomDefault() {
		ratio = 1.0;
		transformTrace();
	}

	/**
	 * Deep copy.
	 *
	 * <p>{@code Object.clone()} alone would hand the copy the same channel,
	 * base-call and quality arrays as the original. Every current caller happens
	 * to replace those arrays wholesale rather than write through them, so the
	 * aliasing has not bitten yet — but {@code swap()} and the edit-trimming
	 * flows both clone and then mutate, and one in-place write would corrupt the
	 * other trace. Copying the arrays makes the contract match the name.
	 */
	@Override
	public GanseqTrace clone() throws CloneNotSupportedException {
		GanseqTrace copy = (GanseqTrace) super.clone();
		copy.traceA = cloneArray(traceA);
		copy.traceT = cloneArray(traceT);
		copy.traceG = cloneArray(traceG);
		copy.traceC = cloneArray(traceC);
		copy.qCalls = cloneArray(qCalls);
		copy.baseCalls = cloneArray(baseCalls);
		copy.transformedA = cloneArray(transformedA);
		copy.transformedT = cloneArray(transformedT);
		copy.transformedG = cloneArray(transformedG);
		copy.transformedC = cloneArray(transformedC);
		return copy;
	}

	private static int[] cloneArray(int[] source) {
		return (source == null) ? null : source.clone();
	}
}
