package com.opaleye.snackvar.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Loads every FXML view with its controller and stylesheet attached.
 *
 * <p>An fx:id that no longer matches a field, a renamed handler or a malformed
 * stylesheet only shows up when the view is inflated, which otherwise happens
 * for the first time in front of a user. Needs a display, so it is skipped on a
 * headless machine rather than failing there.
 */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class FxmlLoadTest {

	private static final List<String> VIEWS = List.of(
			"MainStage.fxml", "settings.fxml", "Trim.fxml",
			"Hetero.fxml", "TranscriptVariant.fxml", "report.fxml");

	@BeforeAll
	static void startToolkit() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		try {
			Platform.startup(started::countDown);
		} catch (IllegalStateException alreadyRunning) {
			started.countDown();
		}
		assertTrue(started.await(30, TimeUnit.SECONDS), "JavaFX toolkit did not start");
	}

	@Test
	@DisplayName("every view inflates with its controller and stylesheets")
	void allViewsLoad() throws Exception {
		for (String view : VIEWS) {
			URL url = FxmlLoadTest.class.getResource("/com/opaleye/snackvar/" + view);
			assertNotNull(url, "missing resource: " + view);

			AtomicReference<Throwable> failure = new AtomicReference<>();
			CountDownLatch done = new CountDownLatch(1);

			Platform.runLater(() -> {
				try {
					Parent root = FXMLLoader.load(url);
					Scene scene = new Scene(root);
					Theme.apply(scene);
					// Forces the stylesheets to parse and the layout to run.
					root.applyCss();
					root.layout();
					Theme.forget(scene);
				} catch (Throwable t) {
					failure.set(t);
				} finally {
					done.countDown();
				}
			});

			assertTrue(done.await(30, TimeUnit.SECONDS), "timed out loading " + view);
			if (failure.get() != null) {
				throw new AssertionError("failed to load " + view, failure.get());
			}
		}
	}

	@Test
	@DisplayName("both stylesheets parse without errors")
	void stylesheetsParse() throws Exception {
		for (Theme.Mode mode : Theme.Mode.values()) {
			AtomicReference<Throwable> failure = new AtomicReference<>();
			CountDownLatch done = new CountDownLatch(1);

			Platform.runLater(() -> {
				try {
					Theme.set(mode);
					Parent root = FXMLLoader.load(
							FxmlLoadTest.class.getResource("/com/opaleye/snackvar/MainStage.fxml"));
					Scene scene = new Scene(root);
					Theme.apply(scene);
					root.applyCss();
					root.layout();
					Theme.forget(scene);
				} catch (Throwable t) {
					failure.set(t);
				} finally {
					done.countDown();
				}
			});

			assertTrue(done.await(30, TimeUnit.SECONDS), "timed out in " + mode);
			if (failure.get() != null) {
				throw new AssertionError("stylesheet failure in " + mode, failure.get());
			}
		}
		Theme.set(Theme.Mode.LIGHT);
	}
}
