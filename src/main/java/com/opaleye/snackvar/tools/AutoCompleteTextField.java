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


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.opaleye.snackvar.RootController;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * This class is a TextField which implements an "autocomplete" functionality, based on a supplied list of entries.
 * @author Caleb Brinkman
 */
public class AutoCompleteTextField extends TextField
{
  /** Most suggestions shown at once. */
  private static final int MAX_ENTRIES = 10;

  /** The existing autocomplete entries. */
  private final SortedSet<String> entries;
  /** The popup used to select an entry. */
  private ContextMenu entriesPopup;
  private RootController rootController = null;

  /** Construct a new AutoCompleteTextField. */
  public AutoCompleteTextField(RootController rootController) {
    super();
    this.rootController = rootController;
    entries = new TreeSet<>();
    
    entriesPopup = new ContextMenu();
    textProperty().addListener(new ChangeListener<String>()
    {
      @Override
      public void changed(ObservableValue<? extends String> observableValue, String s, String s2) {
        String query = getText();
        if (query.isEmpty())
        {
          entriesPopup.hide();
          return;
        }

        List<String> searchResult = search(query);
        if (searchResult.isEmpty())
        {
          // Nothing matches, so there is no menu worth showing. The original
          // opened an empty popup whenever the entry list was non-empty.
          entriesPopup.hide();
          return;
        }

        populatePopup(searchResult);
        if (!entriesPopup.isShowing())
        {
          entriesPopup.show(AutoCompleteTextField.this, Side.BOTTOM, 0, 0);
        }
      }
    });

    focusedProperty().addListener(new ChangeListener<Boolean>() {
      @Override
      public void changed(ObservableValue<? extends Boolean> observableValue, Boolean aBoolean, Boolean aBoolean2) {
        entriesPopup.hide();
      }
    });

  }

  /**
   * Get the existing set of autocomplete entries.
   * @return The existing autocomplete entries.
   */
  public SortedSet<String> getEntries() { return entries; }

  /**
   * Finds up to {@link #MAX_ENTRIES} entries containing {@code query}.
   *
   * <p>Runs on every keystroke against the whole reference set, which is over
   * 53,000 entries, so it stops at the display limit rather than collecting
   * every match, and matches case-insensitively in place instead of lower-casing
   * both sides of the comparison for each entry.
   */
  private List<String> search(String query) {
    List<String> matches = new ArrayList<>(MAX_ENTRIES);
    for (String entry : entries) {
      if (containsIgnoreCase(entry, query)) {
        matches.add(entry);
        if (matches.size() == MAX_ENTRIES) {
          break;
        }
      }
    }
    return matches;
  }

  /** Case-insensitive substring test that allocates nothing. */
  private static boolean containsIgnoreCase(String haystack, String needle) {
    final int limit = haystack.length() - needle.length();
    if (limit < 0) {
      return false;
    }
    for (int i = 0; i <= limit; i++) {
      if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Populate the entry set with the given search results.  Display is limited to 10 entries, for performance.
   * @param searchResult The set of matching strings.
   */
  private void populatePopup(List<String> searchResult) {
    List<CustomMenuItem> menuItems = new LinkedList<>();
    int count = Math.min(searchResult.size(), MAX_ENTRIES);
    for (int i = 0; i < count; i++)
    {
      final String result = searchResult.get(i);
      Label entryLabel = new Label(result);
      CustomMenuItem item = new CustomMenuItem(entryLabel, true);
      item.setOnAction(new EventHandler<ActionEvent>()
      {
        @Override
        public void handle(ActionEvent actionEvent) {
          setText(result);
          entriesPopup.hide();
          rootController.handleOpenSavedRef();
        }
      });
      menuItems.add(item);
    }
    entriesPopup.getItems().clear();
    entriesPopup.getItems().addAll(menuItems);

  }
}