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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Modal notices and confirmations.
 *
 * <p>These replace the fixed 400x150 {@code popup.fxml} and {@code Terms.fxml}
 * panes, which clipped anything longer than a couple of lines because the label
 * was pinned to an absolute size. Built in code instead, so the dialog sizes
 * itself to its content, picks up the current theme, and closes on Enter or
 * Escape.
 */
public final class Dialogs {

	private Dialogs() {
	}

	private static void decorate(Stage stage, Window owner) {
		if (owner != null) {
			stage.initOwner(owner);
		}
		stage.initModality(Modality.WINDOW_MODAL);
		try {
			stage.getIcons().add(new Image(Dialogs.class.getResourceAsStream("/com/opaleye/snackvar/snack_icon.png")));
		} catch (RuntimeException ex) {
			// A missing icon must not stop a message from being shown.
		}
	}

	/** Shows a message and returns once the user dismisses it. */
	public static void message(Window owner, String title, String message) {
		Stage stage = new Stage();
		decorate(stage, owner);
		stage.setTitle(title);

		Label label = new Label(message == null ? "" : message);
		label.setWrapText(true);
		label.getStyleClass().add("dialog-message");
		label.setMaxWidth(Double.MAX_VALUE);

		Button ok = new Button("OK");
		ok.getStyleClass().add("primary");
		ok.setDefaultButton(true);
		ok.setCancelButton(true);
		ok.setOnAction(e -> stage.close());

		HBox buttons = new HBox(ok);
		buttons.setAlignment(Pos.CENTER_RIGHT);

		VBox root = new VBox(18, label, buttons);
		root.getStyleClass().add("dialog-root");
		root.setPadding(new Insets(22));
		root.setMinWidth(380);
		root.setMaxWidth(560);

		Scene scene = new Scene(root);
		Theme.apply(scene);
		stage.setScene(scene);
		stage.setResizable(false);
		stage.setOnHidden(e -> Theme.forget(scene));
		stage.showAndWait();
	}

	/** Shows a long block of text in a scrollable, read-only area. */
	public static void text(Window owner, String title, String heading, String body) {
		Stage stage = new Stage();
		decorate(stage, owner);
		stage.setTitle(title);

		Label headingLabel = new Label(heading);
		headingLabel.getStyleClass().add("dialog-heading");

		TextArea area = new TextArea(body == null ? "" : body);
		area.setEditable(false);
		area.setWrapText(true);
		area.getStyleClass().add("dialog-text");
		VBox.setVgrow(area, Priority.ALWAYS);

		Button ok = new Button("Close");
		ok.getStyleClass().add("primary");
		ok.setDefaultButton(true);
		ok.setCancelButton(true);
		ok.setOnAction(e -> stage.close());

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox buttons = new HBox(spacer, ok);

		VBox root = new VBox(16, headingLabel, area, buttons);
		root.getStyleClass().add("dialog-root");
		root.setPadding(new Insets(22));
		root.setPrefSize(720, 460);

		Scene scene = new Scene(root);
		Theme.apply(scene);
		stage.setScene(scene);
		stage.setOnHidden(e -> Theme.forget(scene));
		stage.showAndWait();
	}

	/** Asks a yes/no question. Returns true only if the user confirms. */
	public static boolean confirm(Window owner, String title, String message, String confirmLabel) {
		Stage stage = new Stage();
		decorate(stage, owner);
		stage.setTitle(title);

		final boolean[] result = { false };

		Label label = new Label(message == null ? "" : message);
		label.setWrapText(true);
		label.getStyleClass().add("dialog-message");

		Button cancel = new Button("Cancel");
		cancel.setCancelButton(true);
		cancel.setOnAction(e -> stage.close());

		Button confirm = new Button(confirmLabel);
		confirm.getStyleClass().add("primary");
		confirm.setDefaultButton(true);
		confirm.setOnAction(e -> {
			result[0] = true;
			stage.close();
		});

		HBox buttons = new HBox(10, cancel, confirm);
		buttons.setAlignment(Pos.CENTER_RIGHT);

		VBox root = new VBox(18, label, buttons);
		root.getStyleClass().add("dialog-root");
		root.setPadding(new Insets(22));
		root.setMinWidth(400);
		root.setMaxWidth(560);

		Scene scene = new Scene(root);
		Theme.apply(scene);
		stage.setScene(scene);
		stage.setResizable(false);
		stage.setOnHidden(e -> Theme.forget(scene));
		stage.showAndWait();
		return result[0];
	}
}
