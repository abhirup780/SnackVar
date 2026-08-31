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
 * Added by the SnackVar 3.0 modernisation fork. See NOTICE.
 */

package com.opaleye.snackvar.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Locates the data files the application reads at runtime.
 *
 * <p>The original build resolved {@code ./reference} and {@code Terms_of_use.txt}
 * against the process working directory, so it only worked when launched from
 * the install folder — double-clicking the jar or starting it from a shortcut
 * left the reference list empty and the terms dialog blank. Everything is
 * resolved here relative to the jar itself, with the working directory kept
 * only as a fallback for people who run it the old way.
 */
public final class AppPaths {

	private static final String REFERENCE_DIR_PROPERTY = "snackvar.referenceDir";
	private static final String REFERENCE_DIR_NAME = "reference";

	private AppPaths() {
	}

	/** Directory containing the application jar, or the working directory if unknown. */
	public static Path installDir() {
		try {
			URL location = AppPaths.class.getProtectionDomain().getCodeSource().getLocation();
			Path path = Path.of(location.toURI());
			// Running from a jar: the install dir is its parent. Running from
			// target/classes during development: walk up to the project root.
			if (Files.isRegularFile(path)) {
				return path.getParent();
			}
			return path;
		} catch (URISyntaxException | RuntimeException ex) {
			return Path.of("").toAbsolutePath();
		}
	}

	/**
	 * Every directory that holds reference sequences, in search order.
	 *
	 * <p>Two locations are supported. The complete RefSeq set ships with the
	 * application and lives next to it; a second directory under the user's home
	 * lets sequences be added without touching the install, and takes precedence
	 * only for names the bundled set does not already provide.
	 */
	public static List<File> referenceDirs() {
		String override = System.getProperty(REFERENCE_DIR_PROPERTY);
		if (override != null && !override.isBlank()) {
			File dir = new File(override);
			return dir.isDirectory() ? List.of(dir) : List.of();
		}

		List<File> dirs = new ArrayList<>();
		for (Path candidate : referenceCandidates()) {
			File dir = candidate.toFile();
			if (dir.isDirectory() && !containsSameDir(dirs, dir)) {
				dirs.add(dir);
			}
		}
		return dirs;
	}

	private static boolean containsSameDir(List<File> dirs, File candidate) {
		for (File existing : dirs) {
			try {
				if (Files.isSameFile(existing.toPath(), candidate.toPath())) {
					return true;
				}
			} catch (IOException ex) {
				// Unreadable path: fall through to the cheap comparison below.
			}
			if (existing.getAbsolutePath().equals(candidate.getAbsolutePath())) {
				return true;
			}
		}
		return false;
	}

	/** Optional per-user directory for reference sequences added locally. */
	public static Path userReferenceDir() {
		String userHome = System.getProperty("user.home", "");
		return Path.of(userHome, ".snackvar", REFERENCE_DIR_NAME);
	}

	private static List<Path> referenceCandidates() {
		List<Path> candidates = new ArrayList<>();
		Path install = installDir();
		candidates.add(install.resolve(REFERENCE_DIR_NAME));
		// target/classes -> project root, so `mvn javafx:run` finds it too.
		Path up = install.getParent();
		for (int i = 0; i < 2 && up != null; i++) {
			candidates.add(up.resolve(REFERENCE_DIR_NAME));
			up = up.getParent();
		}
		candidates.add(Path.of("").toAbsolutePath().resolve(REFERENCE_DIR_NAME));
		candidates.add(userReferenceDir());
		return candidates;
	}

	/**
	 * Names of the available reference sequences, without the {@code .fasta}
	 * suffix, merged across every directory and de-duplicated.
	 *
	 * <p>Returns an empty list when nothing is installed, so an absent reference
	 * set degrades to an empty autocomplete rather than preventing the window
	 * from opening -- the original dereferenced a null directory listing here.
	 */
	public static List<String> referenceNames() {
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (File dir : referenceDirs()) {
			String[] entries = dir.list();
			if (entries == null) {
				continue;
			}
			for (String entry : entries) {
				if (entry.endsWith(".fasta")) {
					names.add(entry.substring(0, entry.length() - ".fasta".length()));
				}
			}
		}
		return new ArrayList<>(names);
	}

	/** Resolves a named reference sequence file, or {@code null} if unavailable. */
	public static File referenceFile(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		for (File dir : referenceDirs()) {
			File file = new File(dir, name + ".fasta");
			if (file.isFile()) {
				return file;
			}
		}
		return null;
	}

	/**
	 * Terms of use text. Read from the jar first so it is always present, then
	 * from disk for installs that keep an edited copy beside the jar.
	 */
	public static String termsOfUse() throws IOException {
		try (InputStream in = AppPaths.class.getResourceAsStream("/Terms_of_use.txt")) {
			if (in != null) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		}
		Path beside = installDir().resolve("Terms_of_use.txt");
		if (Files.isReadable(beside)) {
			return Files.readString(beside, StandardCharsets.UTF_8);
		}
		throw new IOException("Terms_of_use.txt is not bundled with this build.");
	}

	/** Directory a file chooser should open in when nothing better is known. */
	public static File defaultChooserDir() {
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			File home = new File(userHome);
			if (home.isDirectory()) {
				return home;
			}
		}
		return Path.of("").toAbsolutePath().toFile();
	}
}
