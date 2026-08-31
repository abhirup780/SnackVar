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

package com.opaleye.snackvar.tools;

import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Tooltip timing helpers.
 *
 * <p>These used to reach into {@code Tooltip.BEHAVIOR} by reflection and
 * rewrite the private {@code activationTimer} / {@code hideTimer} key frames,
 * because JavaFX 8 exposed no way to change tooltip delays. Under the module
 * system that {@code setAccessible(true)} call throws
 * {@code InaccessibleObjectException} unless the application is launched with
 * {@code --add-opens javafx.controls/javafx.scene.control=ALL-UNNAMED}, which
 * is a large part of why the old build needed so much tweaking to start.
 *
 * <p>JavaFX 9 added {@code setShowDelay} / {@code setShowDuration}, so the hack
 * is no longer needed and the behaviour is now per-tooltip rather than a global
 * mutation of the shared singleton.
 */
public final class TooltipDelay {

	private TooltipDelay() {
	}

	/** Shows the tooltip as soon as the pointer reaches the node. */
	public static void activateTooltipInstantly(Tooltip tooltip) {
		if (tooltip == null) {
			return;
		}
		tooltip.setShowDelay(Duration.ZERO);
	}

	/** Keeps the tooltip up for as long as the pointer stays on the node. */
	public static void holdTooltip(Tooltip tooltip) {
		if (tooltip == null) {
			return;
		}
		tooltip.setShowDuration(Duration.INDEFINITE);
		tooltip.setHideDelay(Duration.ZERO);
	}
}
