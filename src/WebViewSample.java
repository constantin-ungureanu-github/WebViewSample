/**
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener.Change;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.web.PopupFeatures;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebHistory.Entry;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class WebViewSample extends Application {
    private Scene scene;

    @Override
    public void start(final Stage stage) {
        stage.setTitle("Web View Sample");
        scene = new Scene(new Browser(stage), 800, 600, Color.web("#666970"));
        stage.setScene(scene);
        scene.getStylesheets().add("BrowserToolbar.css");
        stage.show();
    }

    public static void main(final String[] args) {
        launch(args);
    }
}

class Browser extends Region {
    private final HBox toolBar;
    final private static String[] imageFiles = new String[] { "product.png", "blog.png", "documentation.png", "partners.png", "help.png" };
    final private static String[] captions = new String[] { "Products", "Blogs", "Documentation", "Partners", "Help" };
    final private static String[] urls = new String[] { "http://www.oracle.com/products/index.html", "http://blogs.oracle.com/",
            "http://docs.oracle.com/javase/index.html", "http://www.oracle.com/partners/index.html",
            WebViewSample.class.getResource("help.html").toExternalForm() };

    final ImageView selectedImage = new ImageView();
    final Hyperlink[] hpls = new Hyperlink[captions.length];
    final Image[] images = new Image[imageFiles.length];
    final WebView browser = new WebView();
    final WebEngine webEngine = browser.getEngine();
    final Button receiveParametersButton = new Button("Receive Parameters");
    final Button toggleHelpTopicsButton = new Button("Toggle Help Topics");
    final WebView smallView = new WebView();
    final ComboBox<String> comboBox = new ComboBox<>();
    private boolean needDocumentationButton = false;

    public Browser(final Stage stage) {
        // apply the styles
        getStyleClass().add("browser");

        for (int i = 0; i < captions.length; i++) {
            final Hyperlink hyperLinks = hpls[i] = new Hyperlink(captions[i]);
            final Image image = images[i] = new Image(getClass().getResourceAsStream(imageFiles[i]));
            hyperLinks.setGraphic(new ImageView(image));
            final String url = urls[i];
            final boolean addButton = (hyperLinks.getText().equals("Help"));

            // process event
            hyperLinks.setOnAction((final ActionEvent e) -> {
                needDocumentationButton = addButton;
                webEngine.load(url);
            });
        }

        comboBox.setPrefWidth(60);

        // create the toolbar
        toolBar = new HBox();
        toolBar.setAlignment(Pos.CENTER);
        toolBar.getStyleClass().add("browser-toolbar");
        toolBar.getChildren().add(comboBox);
        toolBar.getChildren().addAll(hpls);
        toolBar.getChildren().add(createSpacer());

        // set action for the buttons
        receiveParametersButton.setOnAction((final ActionEvent actionEvent) -> {
            System.out.println(webEngine.executeScript("receive_parameters('help_topics', 'id_1')"));
        });

        toggleHelpTopicsButton.setOnAction((final ActionEvent actionEvent) -> {
            webEngine.executeScript("toggle_visibility('help_topics')");
        });

        smallView.setPrefSize(120, 80);

        // handle popup windows
        webEngine.setCreatePopupHandler((final PopupFeatures config) -> {
            smallView.setFontScale(0.8);
            if (!toolBar.getChildren().contains(smallView)) {
                toolBar.getChildren().add(smallView);
            }
            return smallView.getEngine();
        });

        // process history
        final WebHistory history = webEngine.getHistory();
        history.getEntries().addListener((final Change<? extends Entry> listner) -> {
            listner.next();
            listner.getRemoved().stream().forEach((e) -> {
                comboBox.getItems().remove(e.getUrl());
            });
            listner.getAddedSubList().stream().forEach((e) -> {
                comboBox.getItems().add(e.getUrl());
            });
        });

        // set the behavior for the history combobox
        comboBox.setOnAction((final ActionEvent event) -> {
            final int offset = comboBox.getSelectionModel().getSelectedIndex() - history.getCurrentIndex();
            history.go(offset);
        });

        // process page loading
        webEngine.getLoadWorker().stateProperty().addListener((final ObservableValue<? extends State> ov, final State oldState, final State newState) -> {
            toolBar.getChildren().remove(receiveParametersButton);
            toolBar.getChildren().remove(toggleHelpTopicsButton);
            if (newState == State.SUCCEEDED) {
                final JSObject win = (JSObject) webEngine.executeScript("window");
                win.setMember("app", new JavaApp());
                if (needDocumentationButton) {
                    toolBar.getChildren().add(receiveParametersButton);
                    toolBar.getChildren().add(toggleHelpTopicsButton);
                }
            }
        });

        // adding context menu
        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem item1 = new MenuItem("Snapshot");
        final MenuItem item2 = new MenuItem("Print");
        contextMenu.getItems().add(item1);
        contextMenu.getItems().add(item2);

        toolBar.addEventHandler(MouseEvent.MOUSE_CLICKED, (final MouseEvent mouseEvent) -> {
            if (mouseEvent.getButton() == MouseButton.SECONDARY) {
                contextMenu.show(toolBar, mouseEvent.getScreenX(), mouseEvent.getScreenY());
            }
        });

        // saving snapshot
        item1.setOnAction((final ActionEvent actionEvent) -> {
            final WritableImage image = browser.snapshot(new SnapshotParameters(), null);
            final FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export PNG File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image files (*.png)", "*.png"));

            final File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                } catch (final IOException e) {
                    System.out.println("Failed to save snapshoot");
                }
            }
        });

        // processing print job
        item2.setOnAction((final ActionEvent actionEvent) -> {
            final PrinterJob job = PrinterJob.createPrinterJob();
            if (job != null) {
                webEngine.print(job);
                job.endJob();
            }
        });

        // load the home page
        webEngine.load("http://www.oracle.com/products/index.html");

        // add components
        getChildren().add(toolBar);
        getChildren().add(browser);
    }

    // JavaScript interface object
    public class JavaApp {
        public void test(final String parameter) {
            System.out.println("Test method called with parameter " + parameter);
        }

        public void exit() {
            Platform.exit();
        }
    }

    private Node createSpacer() {
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        final double tbHeight = toolBar.prefHeight(w);
        layoutInArea(browser, 0, 0, w, h - tbHeight, 0, HPos.CENTER, VPos.CENTER);
        layoutInArea(toolBar, 0, h - tbHeight, w, tbHeight, 0, HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected double computePrefWidth(final double height) {
        return 800;
    }

    @Override
    protected double computePrefHeight(final double width) {
        return 600;
    }
}
