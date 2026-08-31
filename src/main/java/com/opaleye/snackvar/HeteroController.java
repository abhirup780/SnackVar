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

import java.net.URL;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.TreeSet;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import com.opaleye.snackvar.tools.TooltipDelay;

/**
 * Title : HeteroController
 * FXML Controller class for Hetero.fxml
 * @author Young-gon Kim
 * 2018.7.
 */
public class HeteroController implements Initializable {
	@FXML private BorderPane root;
	@FXML private ScrollPane tracePane;
	@FXML private ScrollPane resultPane;
	@FXML private Button zoomInButton;
	@FXML private Button zoomOutButton;


	private Label[][] labels = null;

	private HeteroTrace heteroTrace;

	private int direction;
	private int insOrDel;
	private int indelSize;
	private char[] refSeq;
	private char[] subtractedSeq;
	private char[] subtractedSeq2;
	private String s_refSeq;
	private String s_subtractedSeq;
	private String s_subtractedSeq2;
	private String HGVS = "";
	private int indelStartGIndex;
	private int indelEndGIndex;
	
	private RootController rootController;
	private ImageView imageView;
	

	// Columns of the deconvoluted alignment that fall inside the indel.
	TreeSet<Integer> highlightSet = new TreeSet<Integer>();

	// Base positions to shade on the chromatogram.
	TreeSet<Integer> highlightRefSeq = new TreeSet<Integer>();
	TreeSet<Integer> highlightSubSeq = new TreeSet<Integer>();


	TreeMap<Integer, Integer> coordiMap = null;
	private int length;
	private Stage primaryStage;
	public void setPrimaryStage(Stage primaryStage) {
		this.primaryStage = primaryStage;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}


	public void setRootController(RootController rootController) {
		this.rootController = rootController;
	}
	

	
	/**
	 * Shows image of heteroTrace and hetero indel alignment 
	 */
	public void showImage() {

		java.awt.image.BufferedImage awtImage = heteroTrace.getHeteroImage(rootController.formatter, highlightRefSeq, highlightSubSeq);
		imageView = new ImageView(SwingFXUtils.toFXImage(awtImage, null));
		imageView.setMouseTransparent(true);
		tracePane.setContent(imageView);

	}

	/**
	 * shows the alignment of deconvoluted trace that contain heterozygous indel   
	 */
	public void showResult() {
		
		int indelStartIndex = heteroTrace.getAlignedIndelStartIndex();
		int indelEndIndex = heteroTrace.getAlignedIndelEndIndex();
		AlignedPoint ap1 = rootController.alignedPoints.get(indelStartIndex - 1);
		AlignedPoint ap2 = rootController.alignedPoints.get(indelEndIndex - 1);
		indelStartGIndex = ap1.getGIndex();
		indelEndGIndex = ap2.getGIndex();
		
		direction = heteroTrace.getDirection();
		insOrDel = heteroTrace.getInsOrDel();
		indelSize = heteroTrace.getIndelSize();
		refSeq = heteroTrace.getRefSeq();
		subtractedSeq = heteroTrace.getSubtractedSeq();
		subtractedSeq2 = heteroTrace.getSubtractedSeq2();
		s_refSeq = new String(refSeq);
		s_subtractedSeq = new String(subtractedSeq);
		s_subtractedSeq2 = new String(subtractedSeq2);
		

		int doublePeakStartIndex = heteroTrace.getDoublePeakStartIndex(); // 1-based

		if(direction==1) {
			coordiMap = rootController.formatter.fwdCoordinateMap;
		}
		else {
			coordiMap = rootController.formatter.revCoordinateMap;
		}


		if(direction == 1) {	//forward
			if(insOrDel == 1) {	//insertion
				String firstPart = s_refSeq.substring(0,doublePeakStartIndex-1);
				String secondPart = s_refSeq.substring(doublePeakStartIndex-1);
				s_refSeq = firstPart + "*".repeat(indelSize) + secondPart;
			}
			else if(insOrDel == -1) {	//deletion
				s_subtractedSeq = "*".repeat(indelSize) + s_subtractedSeq;
			}
			/*
			for(int i=0;i<indelStartIndex-1;i++)
				s_subtractedSeq = " " + s_subtractedSeq;
			 */
			s_subtractedSeq = s_subtractedSeq2 + s_subtractedSeq;
		}

		else if(direction == -1) {		//reverse
			if(insOrDel == -1) { //deletion
				s_refSeq = " ".repeat(indelSize) + s_refSeq;
				s_subtractedSeq = s_subtractedSeq + "*".repeat(indelSize);
			}
			else if (insOrDel == 1){	//insertion
				String firstPart = s_refSeq.substring(0,doublePeakStartIndex);
				String secondPart = s_refSeq.substring(doublePeakStartIndex);
				s_refSeq = firstPart + "*".repeat(indelSize) + secondPart;
				s_subtractedSeq = " ".repeat(indelSize) + s_subtractedSeq;
			}
			s_subtractedSeq += s_subtractedSeq2;
		}

		length = Integer.max(s_refSeq.length(), s_subtractedSeq.length());

		labels = new Label[2][length];
		GridPane gridPane = new GridPane();
		gridPane.setPrefSize(10*length, 40);

		Label firstSeqLabel = new Label("Reference strand");
		Label secondSeqLabel = new Label("Subtracted strand");
		firstSeqLabel.getStyleClass().add("grid-row-label");
		secondSeqLabel.getStyleClass().add("grid-row-label");
		firstSeqLabel.setPrefSize(170, 14);
		secondSeqLabel.setPrefSize(170, 14);


		gridPane.add(firstSeqLabel, 0, 0);
		gridPane.add(secondSeqLabel, 0, 1);

		// Draw the reference strand.
		int traceIndex = 0;
		for(int i=0;i<s_refSeq.length();i++) {
			String refSeqChar = s_refSeq.substring(i,i+1);
			labels[0][i] = new Label(refSeqChar);
			labels[0][i].getStyleClass().add("gridPane");
			labels[0][i].setPrefSize(10, 10);
			
			
			// '*' padding is part of the indel too, so highlight it as well.
			if(refSeqChar.equals("*")) {
				labels[0][i].getStyleClass().add("hetero-indel");
				highlightSet.add(i);
			}

			if(!(refSeqChar.equals(" ") || refSeqChar.equals("*"))) {
				traceIndex++;

				// Agreement between the two strands is shaded.
				if(i<s_subtractedSeq.length())
					if("ATGC".contains(refSeqChar))	 	// an ambiguity code or N never counts as agreement
						if(refSeqChar.equals(s_subtractedSeq.substring(i,i+1))) 
							labels[0][i].getStyleClass().add("hetero-match");

				// Look up the alignment column; both key and value are 1-based.
				Integer i_index = coordiMap.get(traceIndex);
				if(i_index!= null) {
					int index = i_index.intValue();
					AlignedPoint point = rootController.alignedPoints.get(index-1);

					// Coordinates shown on hover.
					String tooltipText = (index) + "\nCoding DNA : " + point.getStringCIndex() + "\nBase # in gene : " + point.getGIndex() + "\n";
					Tooltip tooltip = new Tooltip(tooltipText);
					tooltip.setAutoHide(false);
					TooltipDelay.activateTooltipInstantly(tooltip);
					labels[0][i].setTooltip(tooltip);

					// Inside the called indel, so highlight it.
					if(point.getGIndex()>=indelStartGIndex && point.getGIndex()<=indelEndGIndex) {
						labels[0][i].getStyleClass().add("hetero-indel");
						highlightSet.add(i);
						highlightRefSeq.add(traceIndex);
					}

				}
			}
			gridPane.add(labels[0][i],  i+1,  0);
			
		}

		// Draw the subtracted strand.
		traceIndex = 0;
		for(int i=0;i<s_subtractedSeq.length();i++) {
			String subSeqChar = s_subtractedSeq.substring(i,i+1);
			labels[1][i] = new Label(subSeqChar);
			labels[1][i].getStyleClass().add("gridPane");
			if(!(subSeqChar.equals(" ") || subSeqChar.equals("*"))) 
				traceIndex++;
				
			if(i<s_refSeq.length())
				if("ATGC".contains(s_refSeq.substring(i,i+1)))  // an ambiguity code or N never counts as agreement
					if(s_refSeq.substring(i,i+1).equals(subSeqChar)) 
						labels[1][i].getStyleClass().add("hetero-match");

			if(highlightSet.contains(i)) {
				labels[1][i].getStyleClass().add("hetero-indel");
				if(!subSeqChar.equals("*")) highlightSubSeq.add(traceIndex);
			}
			
			gridPane.add(labels[1][i],  i+1,  1);
		}

		//Label resultLabel = new Label(HGVS);
		//gridPane.add(resultLabel, 0, 4, length,1);
		resultPane.setContent(gridPane);

		
		adjustTracePane(doublePeakStartIndex);
		adjustAlignmentPane();
		showImage();
	}

	/**
	 * Focuses the designated point on the alignment pane
	 * @param index : the point to be focused
	 */
	private void adjustAlignmentPane() {
		int index = highlightSet.first();
		if(labels==null) return;
		if(labels[0]==null) return;

		//double length = labels[0][s_refSeq.length()-1].getLayoutX();
		double length = 250+s_refSeq.length()*10;
		if(length<=1280) return;
		double coordinate = 250 +index*10;
		double hValue = (coordinate - 640.0) / (length - 1280.0);
		resultPane.setHvalue(hValue);

	}
	/**
	 * Focuses the designated index in the trace pane
	 * @param traceIndex : designated index
	 */
	
	private void adjustTracePane(int traceIndex) {
		int newLength = 0;
		int startOffset = 0;

		int[] baseCalls = heteroTrace.getBaseCalls();

		if(heteroTrace.getDirection() == 1) {
			newLength = rootController.formatter.fwdNewLength;
			startOffset = rootController.formatter.fwdStartOffset;
		}
		else {
			newLength = rootController.formatter.revNewLength;
			startOffset = rootController.formatter.revStartOffset;
		}

		newLength = heteroTrace.getTraceLength()*2;
		startOffset = 0;

		if(newLength <= 1280) return;
		if(traceIndex > baseCalls.length)
			traceIndex = baseCalls.length;

		double coordinate = startOffset + baseCalls[traceIndex-1]*2;
		double hValue = (coordinate - 640.0) / (newLength - 1280);
		tracePane.setHvalue(hValue);

	}

		/** setter for heteroTrace
	 * @param heteroTrace
	 */
	public void setHeteroTrace(HeteroTrace heteroTrace) {
		this.heteroTrace = heteroTrace;
	}

	public void handleZoomIn() {
		heteroTrace.zoomIn();
		java.awt.image.BufferedImage awtImage = heteroTrace.getHeteroImage(rootController.formatter, highlightRefSeq, highlightSubSeq);
		javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(awtImage, null);
		imageView.setImage(fxImage);
		tracePane.setContent(imageView);
		tracePane.layout();
		tracePane.setVvalue(1.0);
	}

	public void handleZoomOut() {
		heteroTrace.zoomOut();
		java.awt.image.BufferedImage awtImage = heteroTrace.getHeteroImage(rootController.formatter, highlightRefSeq, highlightSubSeq);
		javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(awtImage, null);
		imageView.setImage(fxImage);
		tracePane.setContent(imageView);
		tracePane.layout();
		tracePane.setVvalue(1.0);
	}

	public BorderPane getRoot() {
		return root;
	}

}
