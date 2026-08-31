/*
 * Modified from the original SnackVar (https://github.com/Young-gonKim/SnackVar)
 * as part of the SnackVar 3.0 modernisation fork. See NOTICE.
 */

package com.opaleye.snackvar.report;

import java.util.ArrayList;

import javafx.scene.image.WritableImage;

public class VariantReport {
	private String variantDescription;
	private ArrayList<String> titleList;
	private ArrayList<WritableImage> imageList;
	private int type;	//hetero indel: 1, otherwise : 0
	
	public VariantReport(String variantDescription, ArrayList<String> titleList, ArrayList<WritableImage> imageList, int type) {
		super();
		this.variantDescription = variantDescription;
		this.titleList = titleList;
		this.imageList = imageList;
		this.type = type;
	}
	public String getVariantDescription() {
		return variantDescription;
	}
	public ArrayList<String> getTitleList() {
		return titleList;
	}
	public ArrayList<WritableImage> getImageList() {
		return imageList;
	}
	public int getType() {
		return type;
	}

}
