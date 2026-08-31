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

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TreeSet;
import java.util.Vector;

import com.opaleye.snackvar.mmalignment.AlignedPair;
import com.opaleye.snackvar.mmalignment.MMAlignment;
import com.opaleye.snackvar.reference.Reference;
import com.opaleye.snackvar.reference.TVController;
import com.opaleye.snackvar.reference.TranscriptVariant;
import com.opaleye.snackvar.report.ReportController;
import com.opaleye.snackvar.report.VariantReport;
import com.opaleye.snackvar.settings.SettingsController;
import com.opaleye.snackvar.tools.AutoCompleteTextField;
import com.opaleye.snackvar.tools.SymbolTools;
import com.opaleye.snackvar.tools.TooltipDelay;
import com.opaleye.snackvar.ui.AppPaths;
import com.opaleye.snackvar.ui.Dialogs;
import com.opaleye.snackvar.ui.Theme;
import com.opaleye.snackvar.variants.Indel;
import com.opaleye.snackvar.variants.Variant;
import com.opaleye.snackvar.variants.VariantCallerFilter;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Title : RootController
 * FXML Controller class for MainStage.fxml
 * Main class of the Ganseq application
 * @author Young-gon Kim
 *2018.5
 */
public class RootController implements Initializable {

	private static final System.Logger LOG = System.getLogger(RootController.class.getName());
	public static final String version = "3.0.0";
	public static final int fontSize = 13;
	public static final int defaultTrimWithoutConfirm = 35;
	public static final double defaultSecondPeakCutoff = 0.30;
	
	// The two parameters below are per-case: they reset whenever a new case is
	// opened (handleOpenRef, handleOpenSavedRef, confirmFwdTrace, confirmRevTrace).
	// They used to reset on every run, which wiped the user's settings whenever
	// the same sample was re-run with different options.
	public static final int defaultGOP = 30;
	/** Ceiling for the automatic gap-opening-penalty escalation in handleRun(). */
	public static final int maxGOP = 1000;
	public static final int defaultDelinsCutoff = 5;


	public static final double paneWidth = 1238; 
	public static final int filterQualityCutoff = 25;

	// Starts at the user's home directory and follows wherever they last opened
	// a file. The original shipped with the author's own D:\ path baked in.
	private String lastVisitedDir = AppPaths.defaultChooserDir().getAbsolutePath();
	

	/**
	 * Settings parameters
	 */
	public double secondPeakCutoff = defaultSecondPeakCutoff;
	public int gapOpenPenalty = defaultGOP;
	public int trimWithoutConfirm = defaultTrimWithoutConfirm;
	public int delinsCutoff = defaultDelinsCutoff;
	


	@FXML private ScrollPane  fwdPane, revPane, alignmentPane, newAlignmentPane;
	@FXML private Label refFileLabel, fwdTraceFileLabel, revTraceFileLabel;
	@FXML private Button fwdRemoveBtn, revRemoveBtn, removeVariant;
	@FXML private Button fwdHeteroBtn, revHeteroBtn;
	@FXML private Button fwdEditTrimBtn, revEditTrimBtn;
	@FXML private Button fwdZoomInButton, fwdZoomOutButton, revZoomInButton, revZoomOutButton;
	@FXML private TextField tf_firstNumber;
	@FXML private Label offsetLabel;
	@FXML private Label cutoffLabel;

	
	@FXML private Button btn_settings;
	@FXML private Button themeToggle;
	//@FXML private ImageView fwdRuler, revRuler;
	@FXML private TableView<Variant> variantTable;

	@FXML private TextField goPositionText;
	
	ChangeListener<Number> cl = null;

	@FXML private HBox leftHBox;
	@FXML private Label versionLabel;
	@FXML private Label referenceStatusLabel;
	private AutoCompleteTextField atf = null;

	public int runMode = 0;
	public int firstNumber = 1; 
	public Formatter formatter = null; 

	private String refFileName = null, fwdFileName = null, revFileName = null;


	
	private Stage primaryStage;
	public void setPrimaryStage(Stage primaryStage) {
		this.primaryStage = primaryStage;
	}


	//public static boolean AIFiltering;


	public boolean alignmentPerformed = false;
	public Vector<AlignedPoint> alignedPoints = null;

	// The span where both the forward and reverse traces are aligned.
	public int startRange = 0, endRange = 0;
	private File fwdTraceFile, revTraceFile;
	public Reference reference;

	public GanseqTrace trimmedFwdTrace, trimmedRevTrace;
	private GanseqTrace originalFwdTrace, originalRevTrace;
	private HeteroTrace fwdHeteroTrace, revHeteroTrace;
	public boolean refLoaded = false, fwdLoaded = false, revLoaded = false;

	private GridPane gridPane = null;
	private Label[][] labels = null;
	public int fwdTraceStart = 0, fwdTraceEnd = 0;
	public int revTraceStart = 0, revTraceEnd = 0;

	TableColumn<Variant, String> tcVariant = null;
	TableColumn<Variant, String> tcZygosity = null;
	TableColumn<Variant, String> tcFrequency = null;
	TableColumn<Variant, String> tcFrom = null;
	TableColumn<Variant, String> tcEquivalentExpressions = null;



	/**
	 * Initializes required settings
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		File tempFile = new File(lastVisitedDir);
		if(!tempFile.exists())
			lastVisitedDir = AppPaths.defaultChooserDir().getAbsolutePath();
		show(fwdHeteroBtn, false);
		show(revHeteroBtn, false);
		show(fwdRemoveBtn, false);
		show(revRemoveBtn, false);
		show(fwdEditTrimBtn, false);
		show(revEditTrimBtn, false);

		versionLabel.setText("v" + version);

		// An empty chip would otherwise render as a small blank box next to the
		// section title before any file has been opened.
		hideWhenEmpty(refFileLabel);
		hideWhenEmpty(fwdTraceFileLabel);
		hideWhenEmpty(revTraceFileLabel);

		variantTable.setPlaceholder(new Label("Load a reference and at least one trace, then choose Run analysis."));

		Tooltip zoomInTooltip = new Tooltip("Zoom In");
		Tooltip zoomOutTooltip = new Tooltip("Zoom Out");
		Tooltip offsetTooltip = new Tooltip("For custom reference file");
		TooltipDelay.activateTooltipInstantly(zoomInTooltip);
		TooltipDelay.activateTooltipInstantly(zoomOutTooltip);
		TooltipDelay.activateTooltipInstantly(offsetTooltip);


		fwdZoomInButton.setTooltip(zoomInTooltip);
		fwdZoomOutButton.setTooltip(zoomOutTooltip);
		revZoomInButton.setTooltip(zoomInTooltip);
		revZoomOutButton.setTooltip(zoomOutTooltip);
		offsetLabel.setTooltip(offsetTooltip);
		cutoffLabel.setText(String.valueOf(this.secondPeakCutoff));

		atf = new AutoCompleteTextField(this);
		atf.setPrefWidth(220);
		atf.setPromptText("RefSeq or gene name");
		// A missing or unreadable reference directory used to dereference a null
		// listing here and stop the window from opening at all.
		List<String> referenceNames = AppPaths.referenceNames();
		atf.getEntries().addAll(referenceNames);
		if(referenceNames.isEmpty())
			atf.setPromptText("No reference set installed");
		leftHBox.getChildren().add(atf);

		if(referenceNames.isEmpty())
			referenceStatusLabel.setText("No reference sequences found \u2014 run ./fetch-reference.sh to restore them");
		else
			referenceStatusLabel.setText(String.format("%,d reference sequences", referenceNames.size()));

		if(themeToggle != null)
			themeToggle.setText(Theme.isDark() ? "Light mode" : "Dark mode");
		Theme.onChange(this::refreshThemedContent);

		/*
		Button openSavedRefButton = new Button();
		openSavedRefButton.setOnAction((ActionEvent) -> {handleOpenSavedRef();});
		openSavedRefButton.setPrefHeight(23);
		openSavedRefButton.setPrefWidth(100);
		openSavedRefButton.setText("Set as reference");
		leftHBox.getChildren().add(openSavedRefButton);
		 */
	}

	/** Collapses a label out of the layout while it has no text. */
	private static void hideWhenEmpty(Label label) {
		label.visibleProperty().bind(label.textProperty().isNotEmpty());
		label.managedProperty().bind(label.textProperty().isNotEmpty());
	}

	/** Hides a control without leaving its slot behind in the layout. */
	private static void show(javafx.scene.Node node, boolean visible) {
		node.setVisible(visible);
		node.setManaged(visible);
	}

	public void setProperties(double secondPeakCutoff, int gapOpenPenalty, int trimWithoutConfirm, int delinsCutoff) {
		this.secondPeakCutoff = secondPeakCutoff;
		cutoffLabel.setText(String.valueOf(this.secondPeakCutoff));
		this.gapOpenPenalty = gapOpenPenalty;
		this.trimWithoutConfirm = trimWithoutConfirm;
		this.delinsCutoff = delinsCutoff;
	}
	
	/**
	 * Jump to the input c. location
	 */
	public void handleGo() {
		if(!alignmentPerformed) {
			popUp("This function is available only after an alignment has been performed.");
			return;
		}
		String goPosString = goPositionText.getText();
		if(goPosString == null || goPosString.length()==0) {
			popUp("Enter a position to jump to (for example 101 or c.101).");
			return;
		}

		for(int i=0;i<alignedPoints.size();i++)  {
			AlignedPoint ap = alignedPoints.get(i);
			if(!(goPosString.length() >=2 && goPosString.substring(0,2).equals("c.")))
				goPosString = "c." + goPosString;
			if(ap.getStringCIndex().equals(goPosString)) {
				focus(i);
				return;
			}
		}
		popUp("That coordinate is not present in the current alignment.\n\nCheck the cDNA coordinate format \u2014 for example 101 or c.101.");
	}
	

	
	public void handleSettings() {
		try {
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("settings.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			stage.getIcons().add(image);

			SettingsController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setRootController(this);
			controller.initValues(secondPeakCutoff, gapOpenPenalty, filterQualityCutoff, trimWithoutConfirm, delinsCutoff);
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setOnHidden(e -> Theme.forget(scene));
			stage.setScene(scene);
			stage.setTitle("Advanced");
			stage.initOwner(primaryStage);
			stage.show();
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			return;
		}
	}


	private void resetParameters() {

		alignmentPerformed = false;
		alignedPoints = null;
		gridPane = null;
		labels = null;
		alignmentPane.setContent(new Label(""));
		fwdTraceStart = 0; fwdTraceEnd = 0;
		revTraceStart = 0; revTraceEnd = 0;
		startRange = 0; 
		endRange = 0;		
		fwdHeteroTrace = null; 
		revHeteroTrace = null;
		show(fwdHeteroBtn, false);
		show(revHeteroBtn, false);
		variantTable.getItems().clear();
	
	}


	public void handleOpenSavedRef() {
		File inputFile = AppPaths.referenceFile(atf.getText());
		if(inputFile == null) {
			popUp("No reference sequence named '" + atf.getText() + "' was found.\n\n"
					+ "Pick one from the suggestions, or open a GenBank/FASTA file directly "
					+ "with 'Open reference file'.");
			return;
		}
		try {
			reference = new Reference(inputFile, Reference.FASTA);
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			popUp(ex.getMessage());
			return;
		}
		
		gapOpenPenalty = defaultGOP;
		delinsCutoff = defaultDelinsCutoff;

		resetParameters();
		refLoaded = true;
		refFileLabel.setText(reference.getRefName());
	}



	/**
	 * Open and Read reference file
	 */

	public void handleOpenRef() {
		File tempFile2 = new File(lastVisitedDir);
		if(!tempFile2.exists())
			lastVisitedDir=".";

		Vector<String> refTypeList = new Vector<>();
		refTypeList.add("*.gb");
		refTypeList.add("*.gbk");
		refTypeList.add("*.genbank");
		refTypeList.add("*.fasta");
		refTypeList.add("*.fa");
		refTypeList.add("*.txt");

		FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().addAll(
				new ExtensionFilter("GenBank or FASTA or TXT", refTypeList),
				new ExtensionFilter("All Files", "*.*"));
		fileChooser.setInitialDirectory(new File(lastVisitedDir));

		File inputFile = fileChooser.showOpenDialog(primaryStage);
		if(inputFile == null) return;
		lastVisitedDir=inputFile.getParent();
		refFileName = inputFile.getName();



		try {
			// endsWith is bounds-safe; the original took fixed-length substrings
			// from the tail and threw StringIndexOutOfBounds on any name shorter
			// than six characters.
			String lowerName = refFileName.toLowerCase();
			String selectedExtension = "";
			if(lowerName.endsWith(".fasta") || lowerName.endsWith(".fa") || lowerName.endsWith(".txt"))
				selectedExtension = "Fasta";
			else if(lowerName.endsWith(".gb") || lowerName.endsWith(".gbk") || lowerName.endsWith(".genbank"))
				selectedExtension = "Genbank";
			else
				throw new Exception("Unrecognised reference file type: " + refFileName
						+ "\n\nExpected a GenBank (.gb, .gbk) or FASTA (.fasta, .fa, .txt) file.");

			if(selectedExtension.equals("Fasta")) {
				reference = new Reference(inputFile, Reference.FASTA);
			}
			else if (selectedExtension.equals("Genbank")) {
				reference = new Reference(inputFile, Reference.GenBank);

				if(reference.getTvList().size() == 0) {
					throw new Exception("No coding DNA information in Genbank file");
				}
				else if(reference.getTvList().size()==1) {		//one transcript variant from Genbank file
					setTranscriptVariant(0);
				}
				else {	//many transcript variant from Genbank file
					FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("TranscriptVariant.fxml"));
					Parent root1 = (Parent) fxmlLoader.load();
					Stage stage = new Stage();
					Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
					stage.getIcons().add(image);

					TVController controller = fxmlLoader.getController();
					controller.setPrimaryStage(stage);
					controller.setRootController(this);
					controller.init(reference.getTvList());
					Scene scene = new Scene(root1);
					Theme.apply(scene);
					stage.setOnHidden(e -> Theme.forget(scene));
					stage.setScene(scene);
					stage.initOwner(primaryStage);
					stage.setTitle("Choose a Transcript Variant");
					stage.show();
				}
			}


		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			popUp(ex.getMessage());
			return;
		}
		gapOpenPenalty = defaultGOP;
		delinsCutoff = defaultDelinsCutoff;

		resetParameters();
		refLoaded = true;
		refFileLabel.setText(refFileName);
	}


	public void setTranscriptVariant (int selectedId) {
		TranscriptVariant tv = reference.getTvList().get(selectedId);
		reference.setcDnaStart(tv.getcDnaStart());
		reference.setcDnaEnd(tv.getcDnaEnd());
	}

	/** 
	 * Removes reference file
	 */
	public void handleRemoveRef() {
		resetParameters();
		refFileLabel.setText("");
		reference = null;
		refLoaded = false;
	}

	/** 
	 * Open forward trace file and opens trim.fxml with that file
	 */

	public void handleFwdEditTrimming() {
		try {
			GanseqTrace newTrace = originalFwdTrace.clone();
			popUpTrimTrace(newTrace, false);
		}
		catch(Exception ex) {
			popUp(ex.getMessage());
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
	}

	public void handleRevEditTrimming() {
		try {
			GanseqTrace newTrace = originalRevTrace.clone();
			popUpTrimTrace(newTrace, true);
		}
		catch(Exception ex) {
			popUp(ex.getMessage());
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
	}


	public void popUpTrimTrace(GanseqTrace tempTrace, boolean complement) {
		try {
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Trim.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			stage.getIcons().add(image);

			TrimController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setTargetTrace(tempTrace, complement);
			controller.setRootController(this);
			controller.init();
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setOnHidden(e -> Theme.forget(scene));
			stage.setScene(scene);
			stage.initOwner(primaryStage);
			stage.initModality(Modality.WINDOW_MODAL);
			stage.setTitle("Trim sequences");
			stage.show();
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			popUp("Error in loading forward trace file\n" + ex.getMessage());
			return;
		}
	}

	public void handleOpenFwdTrace() {
		File tempFile2 = new File(lastVisitedDir);
		if(!tempFile2.exists())
			lastVisitedDir=".";

		FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().addAll(
				new ExtensionFilter("AB1 Files", "*.ab1"), 
				new ExtensionFilter("All Files", "*.*"));
		fileChooser.setInitialDirectory(new File(lastVisitedDir));

		File fwdTraceFile = fileChooser.showOpenDialog(primaryStage);
		if(fwdTraceFile == null) return;
		lastVisitedDir=fwdTraceFile.getParent();

		try {
			GanseqTrace tempTrace = new GanseqTrace(fwdTraceFile, this);
			if(tempTrace.getSequenceLength()<30) {
				popUp("This trace cannot be used: fewer than 30 called bases, or the quality is too poor.");
				return;
			}
			fwdFileName = fwdTraceFile.getName();
			originalFwdTrace = tempTrace.clone();

			// When the suggested trim is small, apply it directly instead of
			// interrupting the user with the trimming window.
			int startTrimPosition = tempTrace.getFrontTrimPosition();
			int endTrimPosition = tempTrace.getTailTrimPosition();
			int[] bc = tempTrace.getBaseCalls();

			try {
				if(startTrimPosition < bc[trimWithoutConfirm]*GanseqTrace.traceWidth && endTrimPosition > bc[tempTrace.getSequenceLength() - trimWithoutConfirm]*GanseqTrace.traceWidth) {
					tempTrace.makeTrimmedTrace(startTrimPosition, endTrimPosition, false);
					confirmFwdTrace(tempTrace);
					return;
				}
			}
			catch(Exception ex) {}

			popUpTrimTrace(tempTrace, false);
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			popUp("Error in loading a trace file\nPlease check the file format and check if the basecalling has been performed.");
			return;
		}
	}

	/**
	 * Loads the image of trimmed forward trace file
	 * @param trace : trimmed forward trace file
	 */
	public void confirmFwdTrace(GanseqTrace trimmedTrace) {
		trimmedFwdTrace = trimmedTrace;
		try {
			fwdPane.setContent(traceView(trimmedFwdTrace.getDefaultImage()));
			fwdTraceFileLabel.setText(fwdFileName);
			fwdLoaded = true;
			show(fwdRemoveBtn, true);
			show(fwdEditTrimBtn, true);
			
		}
		catch(Exception ex) {
			popUp("Error in loading forward trace file\n" + ex.getMessage());
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
		finally {

		}
		gapOpenPenalty = defaultGOP;
		delinsCutoff = defaultDelinsCutoff;

		resetParameters();
	}

	private void fwdToRev() throws Exception {
		trimmedRevTrace = trimmedFwdTrace.clone();
		trimmedRevTrace.makeComplement();

		originalRevTrace = originalFwdTrace.clone();
		//originalRevTrace.makeComplement();

		revFileName = new String(fwdFileName);
		revTraceFileLabel.setText(revFileName);
		revLoaded = true;
		show(revRemoveBtn, true);
		show(revEditTrimBtn, true);

		revPane.setContent(traceView(trimmedRevTrace.getDefaultImage()));
		handleRemoveFwd();
	}

	private void revToFwd() throws Exception {
		trimmedFwdTrace = trimmedRevTrace.clone();
		trimmedFwdTrace.makeComplement();

		originalFwdTrace = originalRevTrace.clone();
		//originalFwdTrace.makeComplement();

		fwdFileName = new String(revFileName);
		fwdTraceFileLabel.setText(fwdFileName);
		fwdLoaded = true;
		show(fwdRemoveBtn, true);
		show(fwdEditTrimBtn, true);
		
		fwdPane.setContent(traceView(trimmedFwdTrace.getDefaultImage()));
		handleRemoveRev();
	}

	private void swap() throws Exception {
		GanseqTrace tempTrace = trimmedRevTrace.clone();
		trimmedRevTrace = trimmedFwdTrace.clone();
		trimmedRevTrace.makeComplement();
		trimmedFwdTrace = tempTrace;
		trimmedFwdTrace.makeComplement();

		GanseqTrace tempTrace2 = originalRevTrace.clone();
		originalRevTrace = originalFwdTrace.clone();
		//originalRevTrace.makeComplement();
		originalFwdTrace = tempTrace2;
		//originalFwdTrace.makeComplement();



		String tempFileName = new String(revFileName);
		revFileName = new String(fwdFileName);
		fwdFileName = tempFileName;
		fwdTraceFileLabel.setText(fwdFileName);
		revTraceFileLabel.setText(revFileName);

		fwdPane.setContent(traceView(trimmedFwdTrace.getDefaultImage()));

		revPane.setContent(traceView(trimmedRevTrace.getDefaultImage()));
	}


	/**
	 * Remove forward trace file
	 */
	public void handleRemoveFwd() {
		resetParameters();
		fwdTraceFileLabel.setText("");
		fwdPane.setContent(new Label(""));
		fwdTraceFile = null;
		trimmedFwdTrace = null;
		fwdLoaded = false;
		show(fwdHeteroBtn, false);
		show(fwdRemoveBtn, false);
		show(fwdEditTrimBtn, false);

		
	}

	/** 
	 * Open reverse trace file and opens trim.fxml with that file
	 */

	public void handleOpenRevTrace() {
		File tempFile2 = new File(lastVisitedDir);
		if(!tempFile2.exists())
			lastVisitedDir=".";

		FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().addAll(
				new ExtensionFilter("AB1 Files", "*.ab1"), 
				new ExtensionFilter("All Files", "*.*"));

		fileChooser.setInitialDirectory(new File(lastVisitedDir));
		File revTraceFile = fileChooser.showOpenDialog(primaryStage);
		if(revTraceFile == null) return;
		lastVisitedDir=revTraceFile.getParent();

		try {
			GanseqTrace tempTrace = new GanseqTrace(revTraceFile, this);
			if(tempTrace.getSequenceLength()<30) {
				popUp("This trace cannot be used: fewer than 30 called bases, or the quality is too poor.");
				return;
			}
			revFileName = revTraceFile.getName();
			originalRevTrace = tempTrace.clone();

			// When the suggested trim is small, apply it directly instead of
			// interrupting the user with the trimming window.
			int startTrimPosition = tempTrace.getFrontTrimPosition();
			int endTrimPosition = tempTrace.getTailTrimPosition();
			int[] bc = tempTrace.getBaseCalls();

			try {
				if(startTrimPosition < bc[trimWithoutConfirm]*GanseqTrace.traceWidth && endTrimPosition > bc[tempTrace.getSequenceLength() - trimWithoutConfirm]*GanseqTrace.traceWidth) {
					tempTrace.makeTrimmedTrace(startTrimPosition, endTrimPosition, true);
					confirmRevTrace(tempTrace);
					return;
				}
			}
			catch(Exception ex) {}

			popUpTrimTrace(tempTrace, true);
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			popUp("Error in loading a trace file\nPlease check the file format and check if the basecalling has been performed.");
			return;
		}
	}




	/**
	 * Loads the image of trimmed reverse trace file
	 * @param trace : trimmed reverse trace file
	 */
	public void confirmRevTrace(GanseqTrace trimmedTrace) {
		trimmedRevTrace = trimmedTrace;

		try {
			revPane.setContent(traceView(trimmedRevTrace.getDefaultImage()));

			//revRuler.setImage(trimmedRevTrace.getRulerImage());

			// Only set on first load; re-setting it after Edit Trimming confirms
			// would overwrite the name with a stale value.
			revTraceFileLabel.setText(revFileName);
			revLoaded = true;
			show(revRemoveBtn, true);
			show(revEditTrimBtn, true);


		}
		catch(Exception ex) {
			popUp("Error in loading reverse trace file\n" + ex.getMessage());
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
		
		gapOpenPenalty = defaultGOP;
		delinsCutoff = defaultDelinsCutoff;

		resetParameters();
	}

	/**
	 * Remove reverse trace file
	 */
	public void handleRemoveRev() {
		resetParameters();
		revTraceFileLabel.setText("");
		revPane.setContent(new Label(""));

		revTraceFile = null;
		trimmedRevTrace = null;
		revLoaded = false;
		show(revHeteroBtn, false);
		show(revRemoveBtn, false);
		show(revEditTrimBtn, false);
	}

	/**
	 * Activates Hetero Indel View for forward trace 
	 */
	public void handleFwdHetero() {

		try {
			if(trimmedFwdTrace == null) {
				popUp("No forward trace file is loaded.");
				return;
			}
			if(fwdHeteroTrace == null) {
				popUp("No heterozygous indel was detected in this trace.");
				return;
			}
			if(alignmentPerformed == false) {
				popUp("Run the alignment first.");
				return;
			}

			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Hetero.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			stage.getIcons().add(image);

			HeteroController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setRootController(this);
			controller.setHeteroTrace(fwdHeteroTrace);
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setOnHidden(e -> Theme.forget(scene));
			stage.setScene(scene);
			stage.setTitle("Heterozygous Indel View");
			stage.initOwner(primaryStage);
			stage.show();

			controller.showResult();
		} catch(Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
	}
	
	public WritableImage getFwdHeteroImage() {
		WritableImage ret = null; 
		try {
			if(trimmedFwdTrace == null) {
				return null;
			}
			if(fwdHeteroTrace == null) {
				return null;
			}
			if(alignmentPerformed == false) {
				return null;
			}

			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Hetero.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			//Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			//stage.getIcons().add(image);

			HeteroController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setRootController(this);
			controller.setHeteroTrace(fwdHeteroTrace);
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setScene(scene);
			stage.initOwner(primaryStage);
			stage.show();
			controller.showResult();
			ret = controller.getRoot().snapshot(new SnapshotParameters(), null);
			stage.close();
			Theme.forget(scene);
			
		} catch(Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
		return ret;
		
	}

	/**
	 * Activates Hetero Indel View for reverse trace 
	 */
	public void handleRevHetero() {
		try {
			if(trimmedRevTrace == null) {
				popUp("No reverse trace file is loaded.");
				return;
			}
			if(revHeteroTrace == null) {
				popUp("No heterozygous indel was detected in this trace.");
				return;
			}
			if(alignmentPerformed == false) {
				popUp("Run the alignment first.");
				return;
			}
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Hetero.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			stage.getIcons().add(image);

			HeteroController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setRootController(this);
			controller.setHeteroTrace(revHeteroTrace);
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setOnHidden(e -> Theme.forget(scene));
			stage.setScene(scene);
			stage.setTitle("Heterozygous Indel View");
			stage.initOwner(primaryStage);
			stage.show();
			controller.showResult();
		} catch(Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
	}
	
	public WritableImage getRevHeteroImage() {
		WritableImage ret = null; 
		try {
			if(trimmedRevTrace == null) {
				return null;
			}
			if(revHeteroTrace == null) {
				return null;
			}
			if(alignmentPerformed == false) {
				return null;
			}

			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Hetero.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			//Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			//stage.getIcons().add(image);

			HeteroController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setRootController(this);
			controller.setHeteroTrace(revHeteroTrace);
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setScene(scene);
			stage.initOwner(primaryStage);
			stage.show();
			controller.showResult();
			ret = controller.getRoot().snapshot(new SnapshotParameters(), null);
			stage.close();
			Theme.forget(scene);
			
		} catch(Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
		}
		return ret;
		
	}

	/** Shows a modal notice owned by the main window. */
	public void popUp(String message) {
		Dialogs.message(primaryStage, "SnackVar", message);
	}

	/** Shows the terms of use. */
	public void termsPopUp(String message) {
		Dialogs.text(primaryStage, "Terms of Use", "Terms of Use", message);
	}

	/**
	 * Switches between the light and dark palettes, then redraws every
	 * chromatogram: those are AWT images baked at the colours in force when they
	 * were rendered, so restyling the scene alone would leave them stale.
	 */
	public void handleToggleTheme() {
		Theme.toggle();
	}

	private void refreshThemedContent() {
		if (themeToggle != null) {
			themeToggle.setText(Theme.isDark() ? "Light mode" : "Dark mode");
		}
		if (alignmentPerformed && formatter != null) {
			if (fwdLoaded && trimmedFwdTrace != null) {
				fwdPane.setContent(traceView(trimmedFwdTrace.getShadedImage(formatter, 0, 0, 0)));
			}
			if (revLoaded && trimmedRevTrace != null) {
				revPane.setContent(traceView(trimmedRevTrace.getShadedImage(formatter, 0, 0, 0)));
			}
		} else {
			if (fwdLoaded && trimmedFwdTrace != null) {
				fwdPane.setContent(traceView(trimmedFwdTrace.getDefaultImage()));
			}
			if (revLoaded && trimmedRevTrace != null) {
				revPane.setContent(traceView(trimmedRevTrace.getDefaultImage()));
			}
		}
	}

	/** Wraps a rendered chromatogram in a non-interactive ImageView. */
	private ImageView traceView(BufferedImage awtImage) {
		ImageView imageView = new ImageView(SwingFXUtils.toFXImage(awtImage, null));
		imageView.setMouseTransparent(true);
		return imageView;
	}

	private boolean doAlignment() {
		//When only fwd trace is given as input
		MMAlignment mma = new MMAlignment(gapOpenPenalty);
		AlignedPair fwdAp = null, complementedFwdAp = null;
		AlignedPair revAp = null, complementedRevAp = null;
		boolean fwdReversed = false, revReversed = false;

		if(fwdLoaded == true) {
			try {
				fwdAp = mma.localAlignment(reference.getRefString(), trimmedFwdTrace.getSequence());

				int alignmentScore1 = 0;
				for(int i=0;i<fwdAp.getAlignedString1().length();i++) {
					if(fwdAp.getAlignedString1().charAt(i)==fwdAp.getAlignedString2().charAt(i)) 
						alignmentScore1++;
				}

				// Score the reverse complement too, to detect a flipped read.
				complementedFwdAp = mma.localAlignment(reference.getRefString(), SymbolTools.getComplementString(trimmedFwdTrace.getSequence()));
				int alignmentScore2 = 0;
				for(int i=0;i<complementedFwdAp.getAlignedString1().length();i++) {
					if(complementedFwdAp.getAlignedString1().charAt(i)==complementedFwdAp.getAlignedString2().charAt(i)) 
						alignmentScore2++;
				}

				// Keep the original orientation when it scores at least as well.
				if(alignmentScore1 < alignmentScore2) {
					fwdReversed = true;
				}
			}
			catch (Exception ex) {
				popUp(ex.getMessage());
				LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
				return false;
			}
		}

		if(revLoaded == true) {
			try {
				revAp = mma.localAlignment(reference.getRefString(), trimmedRevTrace.getSequence());

				int alignmentScore1 = 0;
				for(int i=0;i<revAp.getAlignedString1().length();i++) {
					if(revAp.getAlignedString1().charAt(i)==revAp.getAlignedString2().charAt(i)) 
						alignmentScore1++;
				}

				// Score the reverse complement too, to detect a flipped read.
				complementedRevAp = mma.localAlignment(reference.getRefString(), SymbolTools.getComplementString(trimmedRevTrace.getSequence()));
				int alignmentScore2 = 0;
				for(int i=0;i<complementedRevAp.getAlignedString1().length();i++) {
					if(complementedRevAp.getAlignedString1().charAt(i)==complementedRevAp.getAlignedString2().charAt(i)) 
						alignmentScore2++;
				}

				// Keep the original orientation when it scores at least as well.
				if(alignmentScore1 < alignmentScore2) {
					revReversed = true;
				}

			}

			catch (Exception ex) {
				popUp(ex.getMessage());
				LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
				return false;
			}
		}


		// Swap the traces when they came in reversed. On any error, leave them as they are.
		try {
			if(revLoaded == false && fwdReversed) {
				popUp("The forward and reverse traces appear to be swapped; they have been reassigned.");
				fwdToRev();
				fwdAp = null;
				revAp = complementedFwdAp;
			}
			else if(fwdLoaded == false && revReversed) {
				popUp("The forward and reverse traces appear to be swapped; they have been reassigned.");
				revToFwd();
				revAp = null;
				fwdAp = complementedRevAp;
			}
			else if(fwdReversed == true && revReversed == true) {
				popUp("The forward and reverse traces appear to be swapped; they have been reassigned.");
				swap();
				fwdAp = complementedRevAp;
				revAp = complementedFwdAp;
			}
			else if(fwdReversed) {
				popUp("The forward trace may be reversed.");
			}
			else if(revReversed) {
				popUp("The reverse trace may be reversed.");
			}



		}
		catch(Exception ex) {
			popUp(ex.getMessage());
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			return false;
		}

		if(fwdLoaded == true && revLoaded == false) {
			try {
				alignedPoints = formatter.format2(fwdAp, reference, trimmedFwdTrace, 1);
			}

			catch (Exception ex) {
				popUp(ex.getMessage());
				LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
				return false;
			}
		}

		//When only rev trace is given as input
		else if(fwdLoaded == false && revLoaded == true) {
			try {
				alignedPoints = formatter.format2(revAp, reference, trimmedRevTrace, -1);
			}
			catch (Exception ex) {
				popUp(ex.getMessage());
				LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
				return false;
			}
		}

		//When both of fwd trace and rev trace are given
		else  if(fwdLoaded == true && revLoaded == true) {

			try {
				alignedPoints = formatter.format3(fwdAp, revAp, reference, trimmedFwdTrace, trimmedRevTrace);
			}
			catch (NoContigException ex) {
				popUp(ex.getMessage());
				LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
				return false;
			}
			catch (Exception ex) {
				popUp(ex.getMessage());
				LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
				return false;
			}
		}
		return true;
	}

	public void handleSaveRef() {
		if(refLoaded == false) {
			return;
		}

		String source = reference.getRefString().toLowerCase();
		for(int i=0;i<reference.getcDnaStart().size();i++) {
			int from = reference.getcDnaStart().get(i)-1;
			int to = reference.getcDnaEnd().get(i)-1;
			source = source.substring(0, from) + (source.substring(from, to+1)).toUpperCase() + source.substring(to+1, source.length()) ;
		}

		File file = new File(lastVisitedDir, reference.getRefName() + ".fasta");

		// Overwrite rather than append: the original opened this in append mode,
		// so saving the same reference a second time produced a file holding two
		// concatenated copies of the sequence.
		try (BufferedWriter bWriter = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
			bWriter.write(source);
		} catch(IOException e) {
			popUp("Could not save the reference to " + file.getAbsolutePath() + "\n" + e.getMessage());
			return;
		}
		popUp("Reference saved to\n" + file.getAbsolutePath());
	}


	/**
	 * Performs alignment, Detects variants, Shows results
	 */
	public void handleRun() {
		resetParameters();
		
		try {
			firstNumber = Integer.parseInt(tf_firstNumber.getText());
			if(firstNumber<1) throw new Exception();
		}
		catch(Exception ex) {
			popUp("The first coding DNA number must be a positive integer.");
			return;
		}

		formatter = new Formatter(firstNumber);

		if(refLoaded == false) {
			popUp("Load a reference file before running.");
			return;
		}
		else if(fwdLoaded == false && revLoaded == false) {  
			popUp("Load at least one trace file (forward or reverse) before running.");
			return;
		}

		if(!doAlignment()) return;

		setRange();
		printAlignedResult();
		alignmentPerformed = true;

		if(fwdLoaded) {
			// Redraw the trace panes in the new alignment coordinates.
			fwdPane.setContent(traceView(trimmedFwdTrace.getShadedImage(formatter, 0,0,0)));

		}
		if(revLoaded) {
			revPane.setContent(traceView(trimmedRevTrace.getShadedImage(formatter, 0,0,0)));
		}

		adjustFwdRevPane(alignedPoints.get(0));

		Vector<Variant> heteroIndelList = detectHeteroIndel();
		VariantCallerFilter vcf = new VariantCallerFilter(this, heteroIndelList);
		TreeSet<Variant> variantList = vcf.getVariantList();

		// Escalating the gap-opening penalty can resolve a misalignment, but the
		// original re-entered handleRun() unconditionally: once the penalty hit
		// its 1000 ceiling neither branch changed it, so a trace that still
		// looked misaligned recursed until the stack overflowed. Only retry
		// while the penalty is actually still rising.
		if(vcf.misAlignment(variantList) && gapOpenPenalty < maxGOP) {
			if(gapOpenPenalty == defaultGOP)
				gapOpenPenalty = 200;
			else
				gapOpenPenalty = Integer.min(gapOpenPenalty + 200, maxGOP);
			handleRun();
			return;
		}
		
		if(gapOpenPenalty > 30) 
			popUp("Hetero indel optimization mode is activated.\nHigher gap opening penalty than default value is being used.\nDeactivation is available in 'Advanced'");


		if(variantList.size()==0) popUp("No variants were detected.");
		else {
			variantTable.setEditable(true);
			tcVariant = column(0);
			tcZygosity = column(1);
			tcFrequency = column(2);
			tcFrom = column(3);
			tcEquivalentExpressions = column(4);

			tcVariant.setCellValueFactory(new PropertyValueFactory<>("variantProperty"));
			tcZygosity.setCellValueFactory(new PropertyValueFactory<>("zygosityProperty"));
			tcFrequency.setCellValueFactory(new PropertyValueFactory<>("frequencyProperty"));
			tcFrom.setCellValueFactory(new PropertyValueFactory<>("fromProperty"));
			tcEquivalentExpressions.setCellValueFactory(new PropertyValueFactory<>("equivalentExpressionsProperty"));

			tcVariant.setCellFactory(TextFieldTableCell.forTableColumn());
			tcEquivalentExpressions.setCellFactory(TextFieldTableCell.forTableColumn());

			ObservableList<Variant> observableList= FXCollections.observableArrayList(variantList);
			observableList.sort(Variant::compareTo);
			variantTable.setItems(observableList);


			if(cl != null) 
				variantTable.getSelectionModel().selectedIndexProperty().removeListener(cl);

			cl = new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
					if(newValue.intValue()<0) return;
					Variant variant = variantTable.getItems().get(newValue.intValue());
					if(variant instanceof Indel && ((Indel) variant).getZygosity().equals("homo"))
						focus2((Indel)variant);
					else { 
						focus(variant.getAlignmentIndex()-1);
					}

				}
			};

			variantTable.getSelectionModel().selectedIndexProperty().addListener(cl);

			Scene scene = primaryStage.getScene();
			scene.setOnKeyPressed(event-> {
				if(event.getCode()==KeyCode.DELETE) handleRemoveVariant();
			});
		}
		
	}

	/** Phred-quality band behind a base in the alignment grid. */
	private static String qualityClass(char base, int quality) {
		if(base == Formatter.gapChar || quality >= 40) return "q-high";
		if(quality >= 30) return "q-good";
		if(quality >= 20) return "q-fair";
		if(quality >= 10) return "q-poor";
		return "q-bad";
	}

	/** Marks a base in the alignment grid as the current selection. */
	private static void select(Label label, boolean selected) {
		if(selected) {
			if(!label.getStyleClass().contains("base-selected"))
				label.getStyleClass().add("base-selected");
		}
		else
			label.getStyleClass().remove("base-selected");
	}

	/** Columns are declared in FXML, so the element type has to be reasserted here. */
	@SuppressWarnings("unchecked")
	private TableColumn<Variant, String> column(int index) {
		return (TableColumn<Variant, String>) variantTable.getColumns().get(index);
	}

	/**
	 * Sets the start and end range of the alignment
	 */
	private void setRange() {
		boolean fwdFound = false, revFound = false;
		for(int i=0;i<alignedPoints.size();i++) {
			AlignedPoint ap = alignedPoints.get(i);
			if(!fwdFound && ap.getFwdChar() != Formatter.gapChar) { 
				fwdTraceStart = i+1;
				trimmedFwdTrace.setAlignedRegionStart(ap.getFwdTraceIndex());
				fwdFound = true;
			}
			if(!revFound && ap.getRevChar() != Formatter.gapChar) { 
				revTraceStart = i+1;
				trimmedRevTrace.setAlignedRegionStart(ap.getRevTraceIndex());
				revFound = true;
			}
			if(revFound && fwdFound) break;
		}

		fwdFound = false; 
		revFound = false;
		for(int i=alignedPoints.size()-1;i>=0; i--) {
			AlignedPoint ap = alignedPoints.get(i);
			if(!fwdFound && ap.getFwdChar() != Formatter.gapChar) { 
				fwdTraceEnd = i+1;
				trimmedFwdTrace.setAlignedRegionEnd(ap.getFwdTraceIndex());
				fwdFound = true;
			}
			if(!revFound && ap.getRevChar() != Formatter.gapChar) { 
				revTraceEnd = i+1;
				trimmedRevTrace.setAlignedRegionEnd(ap.getRevTraceIndex());
				revFound = true;
			}
			if(revFound && fwdFound) break;
		}

		startRange = Integer.max(fwdTraceStart,revTraceStart);
		endRange = Integer.min(fwdTraceEnd, revTraceEnd);

	}


	private Vector<Variant> detectHeteroIndel() {
		Vector<Variant> heteroIndelList = new Vector<Variant>();
		Variant fwdIndel=null, revIndel = null;

		fwdHeteroTrace = null;
		revHeteroTrace = null;

		char tempFwdChar = 'N';
		int tempFwdIndex = 0;

		if(fwdLoaded) {
			fwdHeteroTrace = new HeteroTrace(trimmedFwdTrace, this);
			fwdIndel = fwdHeteroTrace.detectHeteroIndel();

			if(fwdIndel != null) {
				tempFwdChar = fwdIndel.getFwdTraceChar();
				tempFwdIndex = fwdIndel.getFwdTraceIndex();
				heteroIndelList.add(fwdIndel);
				show(fwdHeteroBtn, true);
			}
			else fwdHeteroTrace = null;
		}


		if(revLoaded) {
			revHeteroTrace = new HeteroTrace(trimmedRevTrace, this);
			revIndel = revHeteroTrace.detectHeteroIndel();
			if(revIndel != null) {
				// The same indel found on both strands is merged onto the reverse
				// call so it can be shown in both panes; the forward duplicate is
				// dropped later, once the range is set, in
				// VariantCallerFilter.makeVariantList().
				if(fwdIndel != null) 
					if(fwdIndel.getHGVS().equals(revIndel.getHGVS())) {
						revIndel.setHitCount(2);
						revIndel.setFwdTraceChar(tempFwdChar);
						revIndel.setFwdTraceIndex(tempFwdIndex);
					}
				heteroIndelList.add(revIndel);
				show(revHeteroBtn, true);
			}
			else revHeteroTrace = null;
		}
		return heteroIndelList;
	}

	/**
	 * Prints the result of alignment on the alignment pane
	 */
	private void printAlignedResult() {
		final int gridHeight = 16;
		labels = new Label[3][alignedPoints.size()];
		gridPane = new GridPane();

		Label refTitle = new Label("Reference");
		refTitle.getStyleClass().add("grid-row-label");
		refTitle.setMinSize(120,gridHeight);
		refTitle.setPrefSize(120, gridHeight);
		gridPane.add(refTitle, 0,  1);

		if(fwdLoaded) {
			Label fwdTitle = new Label("Forward");
			fwdTitle.getStyleClass().add("grid-row-label");
			fwdTitle.setMinSize(120,gridHeight);
			fwdTitle.setPrefSize(120, gridHeight);
			gridPane.add(fwdTitle, 0,  2);
		}

		if(revLoaded) {
			Label revTitle = new Label("Reverse");
			revTitle.getStyleClass().add("grid-row-label");
			revTitle.setMinSize(120,gridHeight);
			revTitle.setPrefSize(120, gridHeight);
			gridPane.add(revTitle, 0,  3);
		}

		for (int i=0;i<alignedPoints.size();i++) {
			AlignedPoint point = alignedPoints.get(i);

			// Coordinates shown on hover.
			String tooltipText = (i+1) + "\nCoding DNA : " + point.getStringCIndex() + "\nBase # in reference : " + point.getGIndex() + "\n";

			Tooltip tooltip = new Tooltip(tooltipText);
			//tooltip.setOpacity(0.7);
			tooltip.setAutoHide(false);
			TooltipDelay.activateTooltipInstantly(tooltip);
			TooltipDelay.holdTooltip(tooltip);

			Label refLabel = new Label();
			Label fwdLabel = new Label();
			Label revLabel = new Label();
			Label discrepencyLabel = new Label();
			Label indexLabel = new Label();

			refLabel.getStyleClass().add("gridPane");
			fwdLabel.getStyleClass().add("gridPane");
			revLabel.getStyleClass().add("gridPane");
			discrepencyLabel.getStyleClass().add("gridPane");
			indexLabel.getStyleClass().add("gridPane");

			int fwdTraceIndex = point.getFwdTraceIndex();
			int revTraceIndex = point.getRevTraceIndex();

			refLabel.setTooltip(tooltip);
			discrepencyLabel.setTooltip(tooltip);
			indexLabel.setTooltip(tooltip);
			fwdLabel.setTooltip(tooltip);
			revLabel.setTooltip(tooltip);

			//Index  
			if(i%10==0 && alignedPoints.size()-i >= 5) {
				indexLabel.setText(String.valueOf(i+1));
				indexLabel.getStyleClass().add("grid-index");
				GridPane.setColumnSpan(indexLabel, 10);
				indexLabel.setPrefSize(100, 12);
				indexLabel.setOnMouseClicked(new ClickEventHandler(i, fwdTraceIndex, revTraceIndex, point.getFwdChar(), point.getRevChar()));
				gridPane.add(indexLabel, i+1, 0);
			}

			//Reference
			String sRefChar = Character.toString(point.getRefChar());
			if(!point.isCoding()) sRefChar = sRefChar.toLowerCase();
			refLabel.setText(sRefChar);
			refLabel.setPrefSize(10, 14);
			refLabel.setOnMouseClicked(new ClickEventHandler(i, fwdTraceIndex, revTraceIndex, point.getFwdChar(), point.getRevChar()));


			gridPane.add(refLabel,  i+1, 1);
			labels[0][i] = refLabel;

			//Forward
			if(fwdLoaded) {
				fwdLabel.setText(Character.toString(point.getFwdChar()));
				fwdLabel.getStyleClass().add(qualityClass(point.getFwdChar(), point.getFwdQuality()));
				fwdLabel.setPrefSize(10, 14);
				fwdLabel.setOnMouseClicked(new ClickEventHandler(i, fwdTraceIndex, revTraceIndex, point.getFwdChar(), point.getRevChar()));
				gridPane.add(fwdLabel,  i+1, 2);
				labels[1][i] = fwdLabel;
			}

			//Reverse
			if(revLoaded) {
				revLabel.setText(Character.toString(point.getRevChar()));
				revLabel.getStyleClass().add(qualityClass(point.getRevChar(), point.getRevQuality()));
				revLabel.setPrefSize(10, 14);
				revLabel.setOnMouseClicked(new ClickEventHandler(i, fwdTraceIndex, revTraceIndex, point.getFwdChar(), point.getRevChar()));
				gridPane.add(revLabel,  i+1, 3);
				labels[2][i] = revLabel;
			}

			//Discrepency
			discrepencyLabel.setText(Character.toString(point.getDiscrepency()));
			if(point.getDiscrepency() == '*')
				discrepencyLabel.getStyleClass().add("discrepancy");
			discrepencyLabel.setPrefSize(10, 14);
			discrepencyLabel.setOnMouseClicked(new ClickEventHandler(i, fwdTraceIndex, revTraceIndex, point.getFwdChar(), point.getRevChar()));
			gridPane.add(discrepencyLabel,  i+1, 4);
		}

		alignmentPane.setContent(gridPane);
	}

	/**
	 * Focuses the designated point on the alignment pane
	 * @param index : the point to be focused
	 */
	private void adjustAlignmentPane(int index) {
		if(labels==null) return;
		if(labels[0]==null) return;

		double length = labels[0][labels[0].length-1].getLayoutX();
		if(length<=1280) return;
		double coordinate = labels[0][index].getLayoutX();
		double hValue = (coordinate - 640.0) / (length - 1280.0);
		alignmentPane.setHvalue(hValue);

	}

	private void adjustFwdRevPane(AlignedPoint ap) {
		double fwdCoordinate=0, revCoordinate=0;
		double hValue=0;


		if(fwdLoaded) {
			fwdCoordinate = formatter.fwdStartOffset + trimmedFwdTrace.getBaseCalls()[ap.getFwdTraceIndex()-1]*GanseqTrace.traceWidth;
		}

		if(revLoaded) {
			revCoordinate = formatter.revStartOffset + trimmedRevTrace.getBaseCalls()[ap.getRevTraceIndex()-1]*GanseqTrace.traceWidth;
		}

		if(fwdLoaded && revLoaded) {

			// Handle the overhang where only one of the two traces reaches.
			if(ap.getFwdTraceIndex() == 1 || ap.getRevTraceIndex() == 1) {
				double min = Double.min(fwdCoordinate, revCoordinate);
				fwdCoordinate = min;
				revCoordinate = min;
			}

			if(ap.getFwdTraceIndex() > trimmedFwdTrace.getSequenceLength() || ap.getRevTraceIndex() > trimmedRevTrace.getSequenceLength()) {
				double max = Double.max(fwdCoordinate, revCoordinate);
				fwdCoordinate = max;
				revCoordinate = max;
			}
		}

		if(fwdLoaded) {
			hValue = (fwdCoordinate - paneWidth/2) / (formatter.fwdNewLength - paneWidth);
			if(formatter.fwdNewLength > paneWidth)
				fwdPane.setHvalue(hValue);
		}

		if(revLoaded) {
			hValue = (revCoordinate - paneWidth/2) / (formatter.revNewLength - paneWidth);
			if(formatter.revNewLength > paneWidth)
				revPane.setHvalue(hValue);
		}
	}


	/**
	 * Focuses on the designated points (Alignment pane, forward trace pane, reverse trace pane)
	 * @param selectedAlignmentPos : position to be focused on the alignment pane
	 */
	public void focus(int selectedAlignmentPos) {
		// selectedAlignmentPos is 0-based; selectedFwdPos and selectedRevPos are 1-based.
		
		AlignedPoint ap = alignedPoints.get(selectedAlignmentPos);
		char fwdChar = Formatter.gapChar;
		char revChar = Formatter.gapChar;
		int selectedFwdPos = 0;
		int selectedRevPos = 0;
		
		if(fwdLoaded) {
			selectedFwdPos = ap.getFwdTraceIndex();
			fwdChar = ap.getFwdChar();
		}
		if(revLoaded) {
			selectedRevPos = ap.getRevTraceIndex();
			revChar = ap.getRevChar();
		}

		
		boolean fwdGap = (fwdChar == Formatter.gapChar); 
		boolean revGap = (revChar == Formatter.gapChar);

		for(int i=0; i<alignedPoints.size();i++) {
			Label boxedLabel = labels[0][i];
			if(boxedLabel == null) continue;
			if(i==selectedAlignmentPos) {
				select(boxedLabel, true);
				adjustAlignmentPane(i);
			}
			else {
				select(boxedLabel, false);
			}
		}
		if(fwdLoaded) {
			for(int i=0; i<alignedPoints.size();i++) {
				Label boxedLabel = labels[1][i];
				if(boxedLabel == null) continue;
				if(i==selectedAlignmentPos) {
					select(boxedLabel, true);
				}
				else {
					select(boxedLabel, false);
				}
			}

			// A gap has no peak to point at, so show the trace unshaded.
			BufferedImage awtImage = fwdGap
					? trimmedFwdTrace.getShadedImage(formatter, 0, 0, 0)
					: trimmedFwdTrace.getShadedImage(formatter, 1, selectedFwdPos-1, selectedFwdPos-1);
			fwdPane.setContent(traceView(awtImage));
		}
		if(revLoaded) {
			for(int i=0; i<alignedPoints.size();i++) {
				Label boxedLabel = labels[2][i];
				if(boxedLabel == null) continue;
				if(i==selectedAlignmentPos) {
					select(boxedLabel, true);
				}
				else {
					select(boxedLabel, false);
				}
			}

			BufferedImage awtImage2 = revGap
					? trimmedRevTrace.getShadedImage(formatter, 0, 0, 0)
					: trimmedRevTrace.getShadedImage(formatter, 1, selectedRevPos-1, selectedRevPos-1);
			revPane.setContent(traceView(awtImage2));
		}
		adjustFwdRevPane(alignedPoints.get(selectedAlignmentPos));
	}


	/**
	 * Focus method for Homo deletion variants (highlight range)
	 * Homo insertion : When test data is available
	 * Will be finished Later
	 * @param indel 
	 */
	public void focus2(Indel indel) {
		// All coordinates here are 1-based.
		int startAlignmentPos=0, endAlignmentPos=0;	
		int startFwdTracePos=0, endFwdTracePos=0;
		int startRevTracePos=0, endRevTracePos=0;

		AlignedPoint ap = null;
		if(indel.getType() == Indel.duplication) {
			startAlignmentPos = indel.getAlignmentIndex();
			ap = alignedPoints.get(startAlignmentPos-1);
		}

		else {
			if(indel.getAlignmentIndex() > 1) 	// step one column left, unless already at the start
				startAlignmentPos = indel.getAlignmentIndex() - 1;
			else
				startAlignmentPos = indel.getAlignmentIndex();
			ap = alignedPoints.get(startAlignmentPos-1);
		}


		startFwdTracePos =  ap.getFwdTraceIndex();
		startRevTracePos = ap.getRevTraceIndex();

		int counter = 0;
		AlignedPoint ap2 = null;

		int endOffset = 0;
		if(indel.getType()==Indel.deletion || indel.getType() == Indel.duplication)
			endOffset = 1;



		while(indel.getAlignmentIndex()-1+counter < alignedPoints.size()) {
			ap2 = alignedPoints.get(indel.getAlignmentIndex()-1+counter);
			if(ap2.getGIndex() == indel.getgIndex2()+endOffset) {
				counter++;
				break;
			}
			counter++;
		}
		counter--;

		if(ap2 == null) {
			// The indel starts past the end of the alignment; fall back to the
			// single-point focus rather than dereferencing null below.
			focus(Integer.max(0, indel.getAlignmentIndex()-1));
			return;
		}


		endAlignmentPos =  indel.getAlignmentIndex()+counter;
		endFwdTracePos = ap2.getFwdTraceIndex();
		endRevTracePos = ap2.getRevTraceIndex();

		if(indel.getType() == Indel.duplication) {
			endAlignmentPos--;
			endFwdTracePos--;
			endRevTracePos--;
		}


		adjustAlignmentPane(startAlignmentPos-1);
		for(int i=0; i<alignedPoints.size();i++) {
			Label boxedLabel = labels[0][i];
			if(boxedLabel == null) continue;
			if(i >= startAlignmentPos-1 && i<= endAlignmentPos-1) {
				select(boxedLabel, true);
			}
			else {
				select(boxedLabel, false);
			}
		}
		if(fwdLoaded) {
			for(int i=0; i<alignedPoints.size();i++) {
				Label boxedLabel = labels[1][i];
				if(boxedLabel == null) continue;
				if(i >= startAlignmentPos-1 && i<= endAlignmentPos-1 && (i+1) >= fwdTraceStart && (i+1) <= fwdTraceEnd) {
					select(boxedLabel, true);
				}
				else {
					select(boxedLabel, false);
				}
			}


			int colorStart = Integer.max(0, startFwdTracePos-1);
			int colorEnd = Integer.max(0, endFwdTracePos-1);

			fwdPane.setContent(traceView(trimmedFwdTrace.getShadedImage(formatter, 2, colorStart, colorEnd)));
		}
		if(revLoaded) {
			for(int i=0; i<alignedPoints.size();i++) {
				Label boxedLabel = labels[2][i];
				if(boxedLabel == null) continue;
				if(i >= startAlignmentPos-1 && i<= endAlignmentPos-1 && (i+1) >= revTraceStart && (i+1) <= revTraceEnd) {
					select(boxedLabel, true);
				}
				else {
					select(boxedLabel, false);
				}
			}

			int colorStart = Integer.max(0, startRevTracePos-1);
			int colorEnd = Integer.max(0, endRevTracePos-1);

			revPane.setContent(traceView(trimmedRevTrace.getShadedImage(formatter, 2, colorStart, colorEnd)));
		}
		// startAlignmentPos is 1-based, as everywhere else in this method.
		adjustFwdRevPane(alignedPoints.get(startAlignmentPos-1));
	}

	/**
	 * Title : ClickEventHandler
	 * Click event handler for focusing
	 * @author Young-gon Kim
	 */
	class ClickEventHandler implements EventHandler<MouseEvent> {
		private int selectedAlignmentPos = 0, selectedFwdPos = 0, selectedRevPos = 0;
		char fwdChar, revChar;
		public ClickEventHandler(int selectedAlignmentPos, int selectedFwdPos, int selectedRevPos, char fwdChar, char revChar) {
			super();
			this.selectedAlignmentPos = selectedAlignmentPos;
			this.selectedFwdPos = selectedFwdPos;
			this.selectedRevPos = selectedRevPos;
			this.fwdChar = fwdChar;
			this.revChar = revChar;
		}

		@Override
		public void handle(MouseEvent t) {
			focus(selectedAlignmentPos);
		}
	}



	/**
	 * Handler for remove button
	 */
	public void handleRemoveVariant() {
		//if(!variantListViewFocused) return;
		int index = variantTable.getSelectionModel().getSelectedIndex();
		if(index == -1) return;
		int newSelectedIdx = (index == variantTable.getItems().size() - 1)
				? index - 1
						: index;
		//o_variantList.remove(index);
		//v_variantList.remove(index);
		variantTable.getItems().remove(index);

		// Removing a row moves selection to the one above, so re-selecting this
		// index keeps the highlight in place.
		variantTable.getSelectionModel().select(newSelectedIdx);

		// Removing the first row leaves nothing above it, so the selection
		// listener never fires; focus the new first row explicitly.
		if(index == 0 && variantTable.getItems().size()>0) {
			Variant variant = variantTable.getItems().get(0);
			if(variant instanceof Indel && ((Indel) variant).getZygosity().equals("homo"))
				focus2((Indel)variant);
			else 
				focus(variant.getAlignmentIndex()-1);
		}
	}


	/**
	 * Getters for member variables
	 */

	public void handleFwdZoomIn() {
		if(fwdLoaded) {
			trimmedFwdTrace.zoomIn();
			fwdPane.setContent(traceView(trimmedFwdTrace.getDefaultImage()));
			fwdPane.layout();
			fwdPane.setVvalue(1.0);
		}
	}
	public void handleFwdZoomOut() {
		if(fwdLoaded) {
			trimmedFwdTrace.zoomOut();
			fwdPane.setContent(traceView(trimmedFwdTrace.getDefaultImage()));
			fwdPane.layout();
			fwdPane.setVvalue(1.0);
		}
	}
	public void handleRevZoomIn() {
		if(revLoaded) {
			trimmedRevTrace.zoomIn();
			revPane.setContent(traceView(trimmedRevTrace.getDefaultImage()));
			revPane.layout();
			revPane.setVvalue(1.0);
		}
	}
	public void handleRevZoomOut() {
		if(revLoaded) {
			trimmedRevTrace.zoomOut();
			revPane.setContent(traceView(trimmedRevTrace.getDefaultImage()));
			revPane.layout();
			revPane.setVvalue(1.0);
		}
	}
	
	public void handleTermsOfUse() {
		String text;
		try {
			// Read from the jar, so this works no matter where the app is started from.
			text = AppPaths.termsOfUse();
		}
		catch (IOException ex) {
			popUp("Could not read the terms of use.\n" + ex.getMessage());
			return;
		}
		termsPopUp(text);
	}
	
	public void handleGenerateReport() {
		if(variantTable.getItems().size() <= 0) {
			popUp("There are no variants to report.");
			return;
		}
		
		ArrayList<VariantReport> variantReportList = new ArrayList<VariantReport>();
		int originalIndex = variantTable.getSelectionModel().getSelectedIndex();
		
		for(int i=0;i<variantTable.getItems().size();i++) {
			Variant variant = variantTable.getItems().get(i);
			
			
			variantTable.getSelectionModel().select(i);
			
			String description = variant.getVariantProperty() + ", " + variant.getZygosityProperty();
			ArrayList<String> titleList = new ArrayList<String>();
			ArrayList<WritableImage> imageList = new ArrayList<WritableImage>();
			
			// Collect the captions and snapshots for this variant.
			titleList.add("Alignment");
			imageList.add(alignmentPane.snapshot(new SnapshotParameters(), null));
			
			String tempTitle = "Forward Trace";
			if(fwdTraceFileLabel != null && fwdTraceFileLabel.getText() != null) 
				tempTitle += (" : " + fwdTraceFileLabel.getText());
			titleList.add(tempTitle);

			imageList.add(fwdPane.snapshot(new SnapshotParameters(), null));

			tempTitle = "Reverse Trace";
			if(revTraceFileLabel != null && revTraceFileLabel.getText() != null) 
				tempTitle += (" : " + revTraceFileLabel.getText());
			titleList.add(tempTitle);

			imageList.add(revPane.snapshot(new SnapshotParameters(), null));

			// Type 1 marks a hetero indel, which needs a page to itself; 0 otherwise.
			int type = 0;
			if(variant instanceof Indel && ((Indel) variant).getZygosity().equals("hetero")) {
				type = 1;
				if(variant.getHitCount()==2) {
					tempTitle = "Hetero Indel View (Forward)";
					if(fwdTraceFileLabel != null && fwdTraceFileLabel.getText() != null) 
						tempTitle += (" : " + fwdTraceFileLabel.getText());
					titleList.add(tempTitle);
					
					imageList.add(getFwdHeteroImage());

					tempTitle = "Hetero Indel View (Reverse)";
					if(revTraceFileLabel != null && revTraceFileLabel.getText() != null) 
						tempTitle += (" : " + revTraceFileLabel.getText());
					titleList.add(tempTitle);
					
					imageList.add(getRevHeteroImage());
				}
				else if(variant.getDirection() == GanseqTrace.FORWARD) {
					tempTitle = "Hetero Indel View (Forward)";
					if(fwdTraceFileLabel != null && fwdTraceFileLabel.getText() != null) 
						tempTitle += (" : " + fwdTraceFileLabel.getText());
					titleList.add(tempTitle);
					
					imageList.add(getFwdHeteroImage());
				}
				else if (variant.getDirection() == GanseqTrace.REVERSE) {
					tempTitle = "Hetero Indel View (Reverse)";
					if(revTraceFileLabel != null && revTraceFileLabel.getText() != null) 
						tempTitle += (" : " + revTraceFileLabel.getText());
					titleList.add(tempTitle);
					
					imageList.add(getRevHeteroImage());
				}
			}
			

			VariantReport vr = new VariantReport(description, titleList, imageList, type);
			variantReportList.add(vr);
			
		}
		variantTable.getSelectionModel().select(originalIndex);
		
		try {
			FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("report.fxml"));
			Parent root1 = (Parent) fxmlLoader.load();
			Stage stage = new Stage();
			Image image = new Image(getClass().getResourceAsStream("snack_icon.png"));
			stage.getIcons().add(image);
			ReportController controller = fxmlLoader.getController();
			controller.setPrimaryStage(stage);
			controller.setRootController(this);
			controller.setRefFileName(refFileLabel.getText());
			controller.setVariantReportList(variantReportList);
			Scene scene = new Scene(root1);
			Theme.apply(scene);
			stage.setOnHidden(e -> Theme.forget(scene));
			stage.setScene(scene);
			stage.setTitle("SnackVar Report");
			stage.initOwner(primaryStage);
			stage.show();
		}
		catch (Exception ex) {
			LOG.log(System.Logger.Level.DEBUG, "RootController", ex);
			return;
		}
		
	}
	

	public void setRunMode(int runMode) {
		this.runMode = runMode;
	}


}
