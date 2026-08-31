package com.opaleye.snackvar;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a valid ABI (.ab1) chromatogram from a DNA sequence.
 *
 * <p>The upstream project ships no trace data, so there was nothing to test the
 * analysis pipeline against. This writes the subset of the ABIF container that
 * BioJava's {@code ABITrace} actually reads -- the {@code FWO_} channel order,
 * twelve {@code DATA} records (only 9-12 carry signal), and the second
 * {@code PBAS} / {@code PLOC} / {@code PCON} records holding the base calls,
 * peak positions and quality scores.
 *
 * <p>Peaks are Gaussian and evenly spaced, which is idealised but exercises the
 * same code paths as a real capillary read, including double-peak detection
 * when a position is given two bases.
 */
public final class SyntheticTrace {

	/** Samples between adjacent base calls; real reads sit around 10-12. */
	public static final int SPACING = 12;
	private static final int LEAD_IN = 14;
	private static final double SIGMA = 2.6;
	private static final int FULL_HEIGHT = 1200;
	private static final int BASELINE = 12;

	private final int length;
	private final short[][] channels; // [A, C, G, T][sample]
	private final short[] peakPositions;
	private final byte[] qualities;
	private final StringBuilder called = new StringBuilder();

	private SyntheticTrace(int baseCount) {
		this.length = LEAD_IN * 2 + baseCount * SPACING;
		this.channels = new short[4][length];
		this.peakPositions = new short[baseCount];
		this.qualities = new byte[baseCount];
		for (int channel = 0; channel < 4; channel++) {
			for (int i = 0; i < length; i++) {
				channels[channel][i] = BASELINE;
			}
		}
	}

	private static int channelOf(char base) {
		switch (Character.toUpperCase(base)) {
		case 'A': return 0;
		case 'C': return 1;
		case 'G': return 2;
		case 'T': return 3;
		default: throw new IllegalArgumentException("not a base: " + base);
		}
	}

	private void addPeak(int centre, int channel, double amplitude) {
		for (int i = Math.max(0, centre - 12); i < Math.min(length, centre + 13); i++) {
			double d = i - centre;
			double v = amplitude * Math.exp(-(d * d) / (2 * SIGMA * SIGMA));
			channels[channel][i] = (short) Math.min(32000, channels[channel][i] + (int) v);
		}
	}

	/**
	 * Writes a trace for {@code sequence} to {@code file}.
	 *
	 * <p>A lower-case base marks a heterozygous position: it is drawn at half
	 * height alongside the corresponding base from {@code secondAllele}, which
	 * is what makes a double peak the caller should pick up.
	 *
	 * @param sequence      called sequence; the trace's own base calls
	 * @param secondAllele  same length as {@code sequence}, or null for a clean
	 *                      homozygous trace; a differing character adds a second
	 *                      peak at that position
	 * @param quality       phred score written for every base
	 */
	public static void write(Path file, String sequence, String secondAllele, int quality) throws IOException {
		SyntheticTrace trace = new SyntheticTrace(sequence.length());

		for (int i = 0; i < sequence.length(); i++) {
			int centre = LEAD_IN + i * SPACING;
			char primary = Character.toUpperCase(sequence.charAt(i));
			char second = (secondAllele == null) ? primary : Character.toUpperCase(secondAllele.charAt(i));

			if (primary == second) {
				trace.addPeak(centre, channelOf(primary), FULL_HEIGHT);
			} else {
				// Two alleles at roughly equal signal, as a real heterozygote reads.
				trace.addPeak(centre, channelOf(primary), FULL_HEIGHT * 0.52);
				trace.addPeak(centre, channelOf(second), FULL_HEIGHT * 0.48);
			}
			trace.called.append(primary);
			trace.peakPositions[i] = (short) centre;
			trace.qualities[i] = (byte) quality;
		}

		Files.write(file, trace.toAbi());
	}

	/** Convenience for a clean homozygous trace. */
	public static void write(Path file, String sequence) throws IOException {
		write(file, sequence, null, 55);
	}

	// ---------------------------------------------------------------- ABIF ---

	private record Entry(String name, int number, int elementType, int elementSize,
			int numElements, byte[] data) {

		int dataSize() {
			return numElements * elementSize;
		}
	}

	private byte[] toAbi() throws IOException {
		List<Entry> entries = new ArrayList<>();

		// FWO_ fixes which DATA record is which channel. "ACGT" maps
		// DATA9->A, DATA10->C, DATA11->G, DATA12->T.
		entries.add(new Entry("FWO_", 1, 2, 1, 4, "ACGT".getBytes(StandardCharsets.US_ASCII)));

		// ABITrace counts DATA records by order of appearance and uses the
		// 9th through 12th, so eight placeholders have to come first.
		for (int i = 1; i <= 8; i++) {
			entries.add(new Entry("DATA", i, 4, 2, 2, new byte[4]));
		}
		for (int channel = 0; channel < 4; channel++) {
			entries.add(new Entry("DATA", 9 + channel, 4, 2, length, shortsToBytes(channels[channel])));
		}

		// Likewise, the second PBAS/PLOC/PCON record is the one that is read.
		byte[] bases = called.toString().getBytes(StandardCharsets.US_ASCII);
		entries.add(new Entry("PBAS", 1, 2, 1, bases.length, bases));
		entries.add(new Entry("PBAS", 2, 2, 1, bases.length, bases));
		entries.add(new Entry("PLOC", 1, 4, 2, peakPositions.length, shortsToBytes(peakPositions)));
		entries.add(new Entry("PLOC", 2, 4, 2, peakPositions.length, shortsToBytes(peakPositions)));
		entries.add(new Entry("PCON", 1, 2, 1, qualities.length, qualities.clone()));
		entries.add(new Entry("PCON", 2, 2, 1, qualities.length, qualities.clone()));

		final int headerSize = 128;

		// Lay the data blocks out after the header; anything four bytes or
		// smaller is stored inline in the directory entry instead.
		int cursor = headerSize;
		int[] offsets = new int[entries.size()];
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			if (e.dataSize() > 4) {
				offsets[i] = cursor;
				cursor += e.dataSize();
			} else {
				offsets[i] = -1;
			}
		}
		int directoryOffset = cursor;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(out);

		dos.writeBytes("ABIF");
		dos.writeShort(101);
		// The header carries one directory entry describing the directory itself.
		dos.writeBytes("tdir");
		dos.writeInt(1);
		dos.writeShort(1023);
		dos.writeShort(28);
		dos.writeInt(entries.size());
		dos.writeInt(entries.size() * 28);
		dos.writeInt(directoryOffset);
		dos.writeInt(0);
		while (out.size() < headerSize) {
			dos.writeByte(0);
		}

		for (int i = 0; i < entries.size(); i++) {
			if (offsets[i] >= 0) {
				dos.write(entries.get(i).data());
			}
		}

		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			dos.writeBytes(e.name());
			dos.writeInt(e.number());
			dos.writeShort(e.elementType());
			dos.writeShort(e.elementSize());
			dos.writeInt(e.numElements());
			dos.writeInt(e.dataSize());
			if (offsets[i] >= 0) {
				dos.writeInt(offsets[i]);
			} else {
				byte[] inline = new byte[4];
				System.arraycopy(e.data(), 0, inline, 0, Math.min(4, e.data().length));
				dos.write(inline);
			}
			dos.writeInt(0);
		}

		dos.flush();
		return out.toByteArray();
	}

	private static byte[] shortsToBytes(short[] values) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(values.length * 2);
		DataOutputStream dos = new DataOutputStream(out);
		for (short v : values) {
			dos.writeShort(v);
		}
		dos.flush();
		return out.toByteArray();
	}
}
