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

package com.opaleye.snackvar.report;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import com.opaleye.snackvar.RootController;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ReportController implements Initializable  {
	@FXML private ScrollPane scrollPane;
	@FXML private VBox vBox, outerVBox;
	@FXML private Label refSeqLabel;
	
	private RootController rootController = null;
	private Stage primaryStage;

	private final int imageWidth = 1080;

	private ArrayList<VBox> pages;
	private String refFileName = null; 


	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}

	public void setRootController(RootController rootController) {
		this.rootController = rootController;
	}

	public void setPrimaryStage(Stage primaryStage) {
		this.primaryStage = primaryStage;
	}

	public void handlePrint() {

		try {
			PrinterJob printJob = PrinterJob.createPrinterJob();
			if(printJob == null) {
				// No printer is configured on this machine.
				if(rootController != null)
					rootController.popUp("No printer is available on this system.");
				return;
			}
			PageLayout pageLayout = printJob.getJobSettings().getPageLayout();

			{
				if(!printJob.showPrintDialog(primaryStage)) {
					// User cancelled; the original printed regardless.
					printJob.cancelJob();
					return;
				}

				for(int i=0;i<pages.size();i++) {
					WritableImage wi = (pages.get(i)).snapshot(new SnapshotParameters(), null);
					ByteArrayOutputStream  byteOutput = new ByteArrayOutputStream();

					ImageIO.write( SwingFXUtils.fromFXImage(wi, null ), "png", byteOutput );
					ByteArrayInputStream  byteInput = new ByteArrayInputStream(byteOutput.toByteArray());

					Image image = new Image (byteInput);
					ImageView iv = new ImageView(image);
					iv.setPreserveRatio(true);
					iv.setFitWidth(pageLayout.getPrintableWidth());
					printJob.printPage(iv);

				}
				printJob.endJob();
			}

		}
		catch(Exception ex) {
			if(rootController != null)
				rootController.popUp("Printing failed.\n" + ex.getMessage());
		}
	}

	public void setVariantReportList(ArrayList<VariantReport> variantReportList) {
		pages = new ArrayList<VBox>();
		
		//first page
		VBox currentPage = vBox;
		pages.add(currentPage);

		try {
			
			// Two variants per page, or one on its own if it is a hetero indel
			// (those carry an extra deconvoluted view).
			int pageVariantCnt = 0;
			for(int i=0;i<variantReportList.size();i++) {
				VariantReport variantReport = variantReportList.get(i);
				
				if(variantReport.getType()==1) 
					pageVariantCnt +=2;
				else 
					pageVariantCnt++;

				if(pageVariantCnt > 2) {	//new Page
					currentPage = new VBox(6);
					currentPage.getStyleClass().add("report-page");
					outerVBox.getChildren().add(currentPage);
					pages.add(currentPage);

					if(variantReport.getType()==1) 
						pageVariantCnt = 2;
					else 
						pageVariantCnt = 1;
				}

				// Description is an editable field so it can be filled in before printing.
				Label variantLabel = new Label("Variant " + (i+1));
				variantLabel.getStyleClass().add("report-variant");
				currentPage.getChildren().add(variantLabel);

				HBox descHbox = new HBox(8);
				descHbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
				Label descCaption = new Label("DESCRIPTION");
				descCaption.getStyleClass().add("field-label");
				descHbox.getChildren().add(descCaption);
				TextField descTextField = new TextField(variantReport.getVariantDescription());
				descTextField.setPrefWidth(640);
				descHbox.getChildren().add(descTextField);
				currentPage.getChildren().add(descHbox);

				//alignment pane, fwd, rev pane, hetero indel view (if applicable)
				int length = variantReport.getTitleList().size();
				ArrayList<String> titleList = variantReport.getTitleList();
				ArrayList<WritableImage> imageList = variantReport.getImageList();
				for(int j=0;j<length;j++) {
					Label label = new Label (titleList.get(j));
					label.getStyleClass().add("report-caption");
					currentPage.getChildren().add(label);

					WritableImage wi = imageList.get(j);
					// A hetero-indel snapshot can be null when the view could not
					// be rendered; skip it rather than failing the whole report.
					if(wi == null) continue;
					ByteArrayOutputStream  byteOutput = new ByteArrayOutputStream();
					ImageIO.write( SwingFXUtils.fromFXImage(wi, null ), "png", byteOutput );
					ByteArrayInputStream  byteInput = new ByteArrayInputStream(byteOutput.toByteArray());
					Image image = new Image (byteInput);
					ImageView iv = new ImageView(image);
					iv.setPreserveRatio(true);
					iv.setFitWidth(imageWidth);

					currentPage.getChildren().add(iv);
				}
			}


		}
		catch(Exception ex) {
			if(rootController != null)
				rootController.popUp("Could not build the report.\n" + ex.getMessage());
		}
	}

	public void setRefFileName(String refFileName) {
		this.refFileName = refFileName;
		refSeqLabel.setText(refFileName);
	}
	
	





}
