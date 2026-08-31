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

import com.opaleye.snackvar.ui.Theme;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Application entry point.
 *
 * <p>Launched through {@link Launcher} when running from the packaged jar; see
 * that class for why the indirection is needed.
 */
public class MainStage extends Application {

	@Override
	public void start(Stage primaryStage) throws Exception {
		Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
		primaryStage.getIcons().add(image);
		primaryStage.setTitle("SnackVar " + RootController.version);

		FXMLLoader loader = new FXMLLoader(getClass().getResource("MainStage.fxml"));
		Parent root = loader.load();
		RootController controller = loader.getController();
		controller.setPrimaryStage(primaryStage);

		Scene scene = new Scene(root);
		Theme.apply(scene);
		primaryStage.setScene(scene);

		// Fit the window to the screen instead of pinning it to (0, 0) at a
		// fixed 1258x868, which overflowed smaller displays.
		Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
		primaryStage.setWidth(Math.min(1320, bounds.getWidth() * 0.95));
		primaryStage.setHeight(Math.min(900, bounds.getHeight() * 0.95));
		primaryStage.setMinWidth(900);
		primaryStage.setMinHeight(600);
		primaryStage.centerOnScreen();

		primaryStage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
