/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ro.cs.products.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import ro.cs.products.Executor;
import ro.cs.products.base.SensorType;
import ro.cs.products.base.TileMap;
import ro.cs.products.landsat.LandsatTilesMap;
import ro.cs.products.sentinel2.ProductStore;
import ro.cs.products.sentinel2.SentinelTilesMap;
import ro.cs.products.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Simple UI for searching products
 *
 * @author Cosmin Cara
 */
public class ProductSearchDialog extends Application {

    private static final String INVALID_VALUE_STYLE = "-fx-border-color: red;";
    private ChoiceBox<SensorType> productType;
    private ChoiceBox<ProductStore> searchProvider;
    private ChoiceBox<ProductStore> downloadStore;
    private TextField txtTileFilter;
    private ListView<String> lstTiles;
    private ListView<String> lstSelection;
    private TextField txtTarget;
    private Button btnBrowse;
    private TextField txtUser;
    private PasswordField txtPwd;
    private Slider sldClouds;
    private TextField txtRelOrbit;
    private DatePicker dpStart;
    private DatePicker dpEnd;
    private TextField txtResults;
    private TextArea console;
    private Button btnExecute;
    private Label progressLabel;
    private ProgressBar progressBar;
    private Label fileProgressLabel;
    private ProgressBar fileProgressBar;

    private ObservableList<String> currentTiles;
    private Handler handler;
    private ExecutorService executorService;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        executorService = Executors.newSingleThreadExecutor();
        buildUI(primaryStage);
        primaryStage.setResizable(false);
        registerHandler();
        try {
            load();
        } catch (IOException e) {
            console.setText("[err ] " + e.getMessage());
        }
        Platform.setImplicitExit(true);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        cancel();
        super.stop();
        Platform.exit();
    }

    private void cancel() {
        executorService.shutdownNow();
    }

    private void registerHandler() {
        if (handler == null) {
            handler = new Handler() {
                final Map<Level, String> levels = new HashMap<Level, String>() {{
                    put(Level.INFO, "info");
                    put(Level.WARNING, "warn");
                    put(Level.SEVERE, "err ");
                    put(Level.FINE, "dbg ");
                }};
                @Override
                public void publish(LogRecord record) {
                    Platform.runLater(() -> {
                        console.appendText("[" + levels.get(record.getLevel()) + "] " + record.getMessage() + "\n");
                    });
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() throws SecurityException {
                }
            };
            Logger.registerHandler(handler);
        }
    }

    private void buildUI(Stage stage) {
        Group group = new Group();
        Scene scene = new Scene(group, 560, 440);
        stage.setScene(scene);
        stage.setTitle("Product Download");

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));
        ColumnConstraints col1 = new ColumnConstraints(40);
        ColumnConstraints col2 = new ColumnConstraints(40);
        ColumnConstraints col3 = new ColumnConstraints(50);
        ColumnConstraints col4 = new ColumnConstraints(50);
        ColumnConstraints col5 = new ColumnConstraints(40);
        ColumnConstraints col6 = new ColumnConstraints(40);
        ColumnConstraints col7 = new ColumnConstraints(50);
        ColumnConstraints col8 = new ColumnConstraints(50);
        ColumnConstraints col9 = new ColumnConstraints(40);
        ColumnConstraints col10 = new ColumnConstraints(40);
        ColumnConstraints col11 = new ColumnConstraints(40);

        grid.getColumnConstraints().addAll(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11);
        lstTiles = new ListView<>();
        grid.add(new Label("Product Type:"), 0, 0, 2, 1);
        productType = new ChoiceBox<>();
        productType.setItems(FXCollections.observableArrayList(SensorType.S2, SensorType.L8));
        productType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            TileMap tileMap = null;
            switch (newValue) {
                case S2:
                    tileMap = SentinelTilesMap.getInstance();
                    break;
                case L8:
                    txtUser.setStyle(null);
                    txtPwd.setStyle(null);
                    tileMap = LandsatTilesMap.getInstance();
                    break;
            }
            final TileMap theMap = tileMap;
            Platform.runLater(() -> {
                    try {
                        theMap.read(Executor.class.getResourceAsStream(newValue.toString() + "tilemap.dat"));
                        currentTiles = FXCollections.observableArrayList(theMap.getTileNames());
                        lstTiles.setItems(currentTiles);
                    } catch (IOException e) {
                        console.appendText("[err ] " + e.getMessage());
                    }
                });
        });
        productType.getSelectionModel().selectFirst();
        grid.add(productType, 2, 0, 1, 1);

        grid.add(new Label("Tiles:"), 4, 0, 1, 1);
        txtTileFilter = new TextField();
        txtTileFilter.setPromptText("filter");
        txtTileFilter.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && !newValue.isEmpty()) {
                    lstTiles.setItems(currentTiles.filtered(s -> s.startsWith(newValue)));
                } else {
                    lstTiles.setItems(currentTiles);
                }
            });
        grid.add(txtTileFilter, 5, 0, 2, 1);
        lstTiles.setPrefWidth(80);
        lstTiles.setPrefHeight(70);
        lstTiles.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String current = lstTiles.getSelectionModel().getSelectedItem();
                if (!lstSelection.getItems().contains(current)) {
                    lstSelection.getItems().add(current);
                }
            }
        });
        grid.add(lstTiles, 5, 1, 2, 2);

        grid.add(new Label("Selected:"), 7, 0, 1, 1);
        lstSelection = new ListView<>();
        lstSelection.setPrefWidth(80);
        lstSelection.setPrefHeight(70);
        lstSelection.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                lstSelection.getItems().remove(lstSelection.getSelectionModel().getSelectedItem());
            }
        });
        grid.add(lstSelection, 8, 0, 2, 3);

        grid.add(new Label("Search On:"), 0, 1, 2, 1);
        searchProvider = new ChoiceBox<>();
        searchProvider.setItems(FXCollections.observableArrayList(ProductStore.SCIHUB, ProductStore.AWS));
        searchProvider.getSelectionModel().selectFirst();
        grid.add(searchProvider, 2, 1, 2, 1);

        grid.add(new Label("Download From:"), 0, 2, 2, 1);
        downloadStore = new ChoiceBox<>();
        downloadStore.setItems(FXCollections.observableArrayList(ProductStore.AWS, ProductStore.SCIHUB));
        downloadStore.getSelectionModel().selectFirst();
        grid.add(downloadStore, 2, 2, 2, 1);

        grid.add(new Label("Target Folder:"), 0, 3, 2, 1);
        txtTarget = new TextField();
        txtTarget.setPromptText("Browse for the download folder");
        txtTarget.setEditable(false);
        txtTarget.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && Files.exists(Paths.get(newValue))) {
                txtTarget.setStyle(null);
            }
        });
        grid.add(txtTarget, 2, 3, 7, 1);
        btnBrowse = new Button("...");
        btnBrowse.setPrefWidth(22);
        btnBrowse.setOnAction(event -> {
            DirectoryChooser folderChooser = new DirectoryChooser();
            folderChooser.setTitle("Select the target folder");
            File folder = folderChooser.showDialog(stage);
            if (folder != null) {
                txtTarget.setText(folder.getAbsolutePath());
            }
        });
        grid.add(btnBrowse, 9, 3, 1, 1);

        grid.add(new Label("SciHub User:"), 0, 4, 2, 1);
        txtUser = new TextField();
        txtUser.setPromptText("User name");
        txtUser.textProperty().addListener((observable, oldValue, newValue) -> {
            if (searchProvider.getValue().equals(ProductStore.AWS) || newValue != null && !newValue.isEmpty()) {
                txtUser.setStyle(null);
            }
        });
        grid.add(txtUser, 2, 4, 2, 1);
        grid.add(new Label("Password:"), 4, 4, 2, 1);
        txtPwd = new PasswordField();
        txtPwd.setPromptText("Password");
        txtPwd.textProperty().addListener((observable, oldValue, newValue) -> {
            if (searchProvider.getValue().equals(ProductStore.AWS) || newValue != null && !newValue.isEmpty()) {
                txtPwd.setStyle(null);
            }
        });
        grid.add(txtPwd, 6, 4, 2, 1);

        grid.add(new Label("Max. Clouds:"), 0, 5, 2, 1);
        sldClouds = new Slider(0, 100, 50);
        sldClouds.setShowTickLabels(true);
        sldClouds.setShowTickMarks(true);
        sldClouds.setMajorTickUnit(50);
        sldClouds.setMinorTickCount(10);
        sldClouds.setBlockIncrement(5);
        grid.add(sldClouds, 2, 5, 2, 1);
        grid.add(new Label("Relative Orbit:"), 4, 5, 2, 1);
        txtRelOrbit = new TextField();
        txtRelOrbit.setPromptText("Relative orbit");
        txtRelOrbit.setAlignment(Pos.CENTER_RIGHT);
        txtRelOrbit.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!txtRelOrbit.getText().isEmpty() && !txtRelOrbit.getText().matches("\\d{1,3}")) {
                txtRelOrbit.setStyle(INVALID_VALUE_STYLE);
                txtRelOrbit.setPromptText("value must be a number");
            } else {
                txtRelOrbit.setStyle(null);
            }
        });
        grid.add(txtRelOrbit, 6, 5, 2, 1);
        txtResults = new TextField("100");
        txtResults.setPromptText("Limit");
        txtResults.setAlignment(Pos.CENTER_RIGHT);
        txtResults.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!txtResults.getText().isEmpty() && !txtResults.getText().matches("\\d{1,4}")) {
                txtResults.setStyle(INVALID_VALUE_STYLE);
                txtResults.setPromptText("NaN");
            } else {
                txtResults.setStyle(null);
            }
        });
        grid.add(new Label("Results:"), 8, 5, 1, 1);
        grid.add(txtResults, 9, 5, 1, 1);

        grid.add(new Label("Sensing Start:"), 0, 6, 2, 1);
        dpStart = new DatePicker(LocalDate.now().minusDays(7));
        dpStart.setPromptText("Start date");
        dpStart.setConverter(new DateConverter());
        grid.add(dpStart, 2, 6, 2, 1);
        grid.add(new Label("Sensing End:"), 4, 6, 2, 1);

        dpEnd = new DatePicker(LocalDate.now());
        dpEnd.setPromptText("End date");
        dpEnd.setConverter(new DateConverter());
        grid.add(dpEnd, 6, 6, 2, 1);

        console = new TextArea();
        console.setPromptText("output of the execution");
        console.setFont(Font.font(9));
        console.setEditable(false);
        grid.add(console, 0, 7, 10, 2);

        progressLabel = new Label("Overall Progress:");
        progressLabel.setVisible(false);
        grid.add(progressLabel, 0, 9, 2, 1);
        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setPrefWidth(120);
        grid.add(progressBar, 2, 9, 3, 1);

        fileProgressLabel = new Label("Curr. File:");
        fileProgressLabel.setVisible(false);
        grid.add(fileProgressLabel, 5, 9, 2, 1);
        fileProgressBar = new ProgressBar();
        fileProgressBar.setVisible(false);
        fileProgressBar.setPrefWidth(120);
        grid.add(fileProgressBar, 7, 9, 2, 1);

        btnExecute = new Button("Execute");
        btnExecute.setUserData(Boolean.TRUE);
        btnExecute.setOnAction(event -> {
            if ((Boolean) btnExecute.getUserData()) {
                if (canExecute()) {
                    try {
                        btnExecute.setText("Cancel");
                        btnExecute.setUserData(Boolean.FALSE);
                        progressBar.setProgress(0);
                        fileProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                        toggleControls(true);
                        console.clear();
                        Executor.setProgressListener(value -> Platform.runLater(() -> {
                            progressBar.setProgress(value);
                        }));
                        Executor.setFileProgressListener((value, rate) -> Platform.runLater(() -> {
                            fileProgressBar.setProgress(value);
                            double dlRate = rate;
                            if (Double.isInfinite(dlRate) || Double.isNaN(dlRate)) {
                                dlRate = 0;
                            }
                            fileProgressLabel.setText(String.format("Cur. File: [%.2fM/s]", dlRate));
                        }));
                        executorService.submit(new DownloadTask(prepareArguments(), ProductSearchDialog.this::onExecutionCompleted));
                    } catch (Exception e) {
                        console.appendText("[err ] " + e.getMessage() + "\n");
                    }
                }
            } else {
                try {
                    cancel();
                } finally {
                    console.clear();
                    toggleControls(false);
                    btnExecute.setText("Execute");
                    btnExecute.setUserData(Boolean.TRUE);
                }
            }
        });

        grid.add(btnExecute, 9, 9, 2, 1);
        scene.setRoot(grid);
    }

    private void toggleControls(boolean executing) {
        productType.setDisable(executing);
        searchProvider.setDisable(executing);
        downloadStore.setDisable(executing);
        lstTiles.setDisable(executing);
        lstSelection.setDisable(executing);
        txtTarget.setDisable(executing);
        btnBrowse.setDisable(executing);
        txtUser.setDisable(executing);
        txtPwd.setDisable(executing);
        sldClouds.setDisable(executing);
        txtRelOrbit.setDisable(executing);
        dpStart.setDisable(executing);
        dpEnd.setDisable(executing);
        //btnExecute.setDisable(executing);
        progressLabel.setVisible(executing);
        progressBar.setVisible(executing);
        fileProgressLabel.setVisible(executing);
        fileProgressBar.setVisible(executing);
        txtResults.setDisable(executing);
    }

    private void onExecutionCompleted(int returnCode) {
        Platform.runLater(() -> {
            toggleControls(false);
            progressBar.setProgress(0);
            btnExecute.setText("Execute");
            btnExecute.setUserData(Boolean.TRUE);
        });
        try {
            save();
        } catch (IOException e) {
            console.appendText("[err] " + e.getMessage() + "\n");
        }
    }

    private boolean canExecute() {
        boolean isValid = true;
        if (ProductStore.SCIHUB.equals(searchProvider.getValue())) {
            String user = txtUser.getText();
            String pwd = txtPwd.getText();
            isValid = user != null && !user.isEmpty();
            if (!isValid) {
                txtUser.setStyle(INVALID_VALUE_STYLE);
            }
            isValid = pwd != null && !pwd.isEmpty();
            if (!isValid) {
                txtPwd.setStyle(INVALID_VALUE_STYLE);
            }
        }
        if (isValid) {
            String folder = txtTarget.getText();
            isValid = folder != null && Files.exists(Paths.get(folder));
            if (!isValid) {
                txtTarget.setStyle(INVALID_VALUE_STYLE);
            }
        }

        return isValid;
    }

    private void save() throws IOException {
        Properties properties = new Properties();
        properties.put("sensor", productType.getSelectionModel().getSelectedItem().toString());
        if (ProductStore.AWS.equals(searchProvider.getSelectionModel().getSelectedItem())) {
            properties.put("aws", "true");
        } else {
            properties.put("user", txtUser.getText());
            properties.put("password", txtPwd.getText());
        }
        properties.put("store", downloadStore.getSelectionModel().getSelectedItem().toString());
        properties.put("out", txtTarget.getText());
        if (!lstSelection.getItems().isEmpty()) {
            properties.put("tiles", String.join(" ", lstSelection.getItems()));
        }
        properties.put("cloudpercentage", String.valueOf(sldClouds.getValue()));
        try {
            Integer.parseInt(txtRelOrbit.getText());
            properties.put("relative.orbit", txtRelOrbit.getText());
        } catch (NumberFormatException ignored) {
        }
        if (dpStart.getValue() != null) {
            properties.put("startdate", dpStart.getValue().toString());
        }
        if (dpEnd.getValue() != null) {
            properties.put("enddate", dpEnd.getValue().toString());
        }
        try {
            Integer.parseInt(txtResults.getText());
            properties.put("limit", txtResults.getText());
        } catch (NumberFormatException ignored) {
        }
        properties.store(Files.newOutputStream(Paths.get("").toAbsolutePath().resolve("parameters.properties")), "");
    }

    private void load() throws IOException {
        Path path = Paths.get("").toAbsolutePath().resolve("parameters.properties");
        if (Files.exists(path)) {
            Properties properties = new Properties();
            properties.load(Files.newInputStream(path));
            Object value = null;
            if ((value = properties.getOrDefault("sensor", null)) != null) {
                productType.getSelectionModel().select(Enum.valueOf(SensorType.class, value.toString()));
            }
            if ((value = properties.getOrDefault("aws", null)) != null) {
                searchProvider.getSelectionModel().select(ProductStore.AWS);
            }
            if ((value = properties.getOrDefault("user", null)) != null) {
                txtUser.setText(value.toString());
            }
            if ((value = properties.getOrDefault("password", null)) != null) {
                txtPwd.setText(value.toString());
            }
            if ((value = properties.getOrDefault("store", null)) != null) {
                downloadStore.getSelectionModel().select(Enum.valueOf(ProductStore.class, value.toString()));
            }
            if ((value = properties.getOrDefault("out", null)) != null) {
                txtTarget.setText(value.toString());
            }
            if ((value = properties.getOrDefault("tiles", null)) != null) {
                lstSelection.setItems(FXCollections.observableArrayList(value.toString().split(" ")));
            }
            if ((value = properties.getOrDefault("cloudpercentage", null)) != null) {
                sldClouds.setValue(Double.parseDouble(value.toString()));
            }
            if ((value = properties.getOrDefault("relative.orbit", null)) != null) {
                txtRelOrbit.setText(value.toString());
            }
            if ((value = properties.getOrDefault("startdate", null)) != null) {
                dpStart.setValue(new DateConverter().fromString(value.toString()));
            }
            if ((value = properties.getOrDefault("enddate", null)) != null) {
                dpEnd.setValue(new DateConverter().fromString(value.toString()));
            }
            if ((value = properties.getOrDefault("limit", null)) != null) {
                txtResults.setText(value.toString());
            }
        }
    }

    private String[] prepareArguments() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--sensor");
        SensorType sensorType = productType.getSelectionModel().getSelectedItem();
        args.add(sensorType.toString());
        if (sensorType == SensorType.L8) {
            //--l8col C1 --l8pt T1
            args.add("--l8col");
            args.add("C1");
            args.add("--l8pt");
            args.add("T1");
        }
        if (ProductStore.AWS.equals(searchProvider.getSelectionModel().getSelectedItem())) {
            args.add("--aws");
        } else {
            args.add("--user");
            args.add(txtUser.getText());
            args.add("--password");
            args.add(txtPwd.getText());
        }
        args.add("--store");
        args.add(downloadStore.getSelectionModel().getSelectedItem().toString());
        args.add("--out");
        args.add(txtTarget.getText());
        if (!lstSelection.getItems().isEmpty()) {
            args.add("--tiles");
            ObservableList<String> selectedItems = lstSelection.getItems();
            for (String item : selectedItems) {
                args.add(item);
            }
        }
        args.add("--cloudpercentage");
        args.add(String.valueOf(sldClouds.getValue()));
        try {
            Integer.parseInt(txtRelOrbit.getText());
            args.add("--relative.orbit");
            args.add(txtRelOrbit.getText());
        } catch (NumberFormatException ignored) {
        }
        if (dpStart.getValue() != null) {
            args.add("--startdate");
            args.add(new DateConverter().toString(dpStart.getValue()));
        }
        if (dpEnd.getValue() != null) {
            args.add("--enddate");
            args.add(new DateConverter().toString(dpEnd.getValue()));
        }
        try {
            Integer.parseInt(txtResults.getText());
            args.add("--limit");
            args.add(txtResults.getText());
        } catch (NumberFormatException ignored) {
        }
        return args.toArray(new String[args.size()]);
    }

    private class DateConverter extends StringConverter<LocalDate> {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        @Override
        public String toString(LocalDate object) {
            if (object != null) {
                return formatter.format(object);
            } else {
                return "";
            }
        }

        @Override
        public LocalDate fromString(String string) {
            if (string != null && !string.isEmpty()) {
                return LocalDate.parse(string, formatter);
            } else {
                return null;
            }
        }
    }

    private class DownloadTask implements Runnable {

        private Consumer<Integer> callback;
        private String[] arguments;

        DownloadTask(String[] args, Consumer<Integer> callback) {
            this.arguments = args;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                callback.accept(Executor.execute(arguments));
            } catch (Throwable ignored) {
                callback.accept(-1);
            }
        }
    }

}

