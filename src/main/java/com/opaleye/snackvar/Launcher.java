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

package com.opaleye.snackvar;

/**
 * Entry point for the packaged jar.
 *
 * <p>A class that extends {@link javafx.application.Application} cannot be the
 * main class of a jar whose JavaFX runtime lives on the classpath: the JavaFX
 * launcher checks for the {@code javafx.graphics} module and aborts with
 * "JavaFX runtime components are missing" before {@code main} is reached.
 * Going through a plain class that merely calls {@link MainStage#main} skips
 * that check, which is what lets {@code java -jar snackvar.jar} work on a
 * stock JDK with no JavaFX installed.
 */
public final class Launcher {
	private Launcher() {
	}

	public static void main(String[] args) {
		MainStage.main(args);
	}
}
