package es.ruben;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class HelloController {

    @FXML private Label titleLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private ComboBox<String> langCombo;
    @FXML private StackPane rootPane;
    @FXML private Button addBtn;
    @FXML private Button pdfBtn;
    // (Sin botón de exportación Java, usaremos Python)

    private ObservableList<Wizard> wizardList = FXCollections.observableArrayList();
    private FilteredList<Wizard> filteredData;
    private Pagination pagination;
    private final int ITEMS_PER_PAGE = 8;

    private String currentLang = "ES";
    private final Image DEFAULT_IMAGE = new Image("https://img.icons8.com/ios-filled/150/000000/user-male-circle.png", true);

    private final ReportService reportService = new ReportService();

    @FXML
    public void initialize() {
        loadFromDatabase(); // Carga optimizada

        filteredData = new FilteredList<>(wizardList, p -> true);

        // Idiomas
        langCombo.setItems(FXCollections.observableArrayList("Español", "English"));
        langCombo.getSelectionModel().selectFirst();
        langCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentLang = newVal.equals("English") ? "EN" : "ES";
            updateInterfaceLanguage();
            updatePagination();
        });

        // Filtros
        updateFilterCombo();
        filterTypeCombo.getSelectionModel().selectFirst();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilter());
        filterTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter());

        // Botones
        addBtn.setOnAction(e -> showAddWizardDialog());

        pdfBtn.setOnAction(e -> {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hogwarts.db")) {
                reportService.printYearbook(conn, rootPane.getScene().getWindow());
            } catch (SQLException ex) { ex.printStackTrace(); }
        });

        if (wizardList.isEmpty()) {
            Label emptyLabel = new Label("⚠️ No hay datos. Ejecuta el script ETL.");
            rootPane.getChildren().add(emptyLabel);
        } else {
            setupPagination();
            updateInterfaceLanguage();
        }
    }

    // --- CARGA DE DATOS OPTIMIZADA (SOLUCIÓN MEMORIA) ---
    private void loadFromDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hogwarts.db");
             Statement stmt = conn.createStatement();
             // Solo traemos lo necesario
             ResultSet rs = stmt.executeQuery("SELECT id, name, house, wand, image_blob FROM wizards")) {

            System.out.println("Iniciando carga optimizada...");
            while (rs.next()) {
                byte[] imgBytes = rs.getBytes("image_blob");

                // AQUÍ ESTÁ EL TRUCO: Pasamos 'null' como imagen visual.
                // La clase Wizard creará la imagen solo cuando haga falta (Lazy Loading).
                wizardList.add(new Wizard(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("house"),
                        rs.getString("wand"),
                        null,      // <--- IMAGEN NULL PARA AHORRAR RAM
                        imgBytes
                ));
            }
            System.out.println("✅ Datos cargados: " + wizardList.size());
        } catch (SQLException e) { System.out.println("❌ Error DB: " + e.getMessage()); }
    }

    // --- AÑADIR NUEVO MAGO ---
    private void showAddWizardDialog() {
        Dialog<Wizard> dialog = new Dialog<>();
        dialog.setTitle(currentLang.equals("ES") ? "Añadir Nuevo Mago" : "Add New Wizard");
        dialog.setHeaderText(null);
        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("css/styles.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dialog-pane");
        } catch (Exception e) {}

        ButtonType saveButtonType = new ButtonType(currentLang.equals("ES") ? "Guardar" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(); nameField.setPromptText("Ej: Harry Potter");
        String noHouseOption = currentLang.equals("ES") ? "Sin Casa" : "No House";
        ComboBox<String> houseCombo = new ComboBox<>();
        houseCombo.setItems(FXCollections.observableArrayList(noHouseOption, "Gryffindor", "Slytherin", "Ravenclaw", "Hufflepuff"));
        houseCombo.setValue(noHouseOption);
        TextField wandField = new TextField(); wandField.setPromptText("Ej: 11', Holly");

        Button imgBtn = new Button(currentLang.equals("ES") ? "Seleccionar Foto..." : "Select Photo...");
        Label imgLabel = new Label(currentLang.equals("ES") ? "Sin archivo" : "No file");
        final File[] selectedFile = {null};

        imgBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.png"));
            File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
            if (file != null) {
                selectedFile[0] = file;
                imgLabel.setText(file.getName());
                imgLabel.setStyle("-fx-text-fill: green;");
            }
        });

        grid.add(new Label(currentLang.equals("ES") ? "Nombre:" : "Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(currentLang.equals("ES") ? "Casa:" : "House:"), 0, 1);
        grid.add(houseCombo, 1, 1);
        grid.add(new Label(currentLang.equals("ES") ? "Varita:" : "Wand:"), 0, 2);
        grid.add(wandField, 1, 2);
        grid.add(new Label(currentLang.equals("ES") ? "Foto:" : "Photo:"), 0, 3);
        grid.add(imgBtn, 1, 3);
        grid.add(imgLabel, 1, 4);
        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        nameField.textProperty().addListener((o, old, newV) -> saveButton.setDisable(newV.trim().isEmpty()));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                byte[] imageBytes = null;
                Image imageObj = null;
                if (selectedFile[0] != null) {
                    try {
                        imageBytes = Files.readAllBytes(selectedFile[0].toPath());
                        // Aquí SÍ creamos la imagen porque es solo una y el usuario quiere verla ya
                        imageObj = new Image(new FileInputStream(selectedFile[0]));
                    } catch (IOException ex) {}
                }
                String newId = UUID.randomUUID().toString();
                saveWizardToDB(newId, nameField.getText(), houseCombo.getValue(), wandField.getText(), imageBytes);
                return new Wizard(newId, nameField.getText(), houseCombo.getValue(), wandField.getText(), imageObj, imageBytes);
            }
            return null;
        });
        Optional<Wizard> result = dialog.showAndWait();
        result.ifPresent(wizard -> { wizardList.add(0, wizard); updatePagination(); });
    }

    private void saveWizardToDB(String id, String name, String house, String wand, byte[] imageBytes) {
        String sql = "INSERT INTO wizards(id, name, house, wand, image_blob) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hogwarts.db");
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id); pstmt.setString(2, name); pstmt.setString(3, house); pstmt.setString(4, wand); pstmt.setBytes(5, imageBytes);
            pstmt.executeUpdate();
        } catch (SQLException e) { System.out.println("❌ Error SQL: " + e.getMessage()); }
    }

    private void deleteWizard(Wizard w) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar");
        alert.setContentText("¿Eliminar a " + w.getName() + "?");
        try {
            alert.getDialogPane().getStylesheets().add(getClass().getResource("css/styles.css").toExternalForm());
            alert.getDialogPane().getStyleClass().add("dialog-pane");
        } catch(Exception e) {}

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hogwarts.db");
                 PreparedStatement pstmt = conn.prepareStatement("DELETE FROM wizards WHERE id = ?")) {
                pstmt.setString(1, w.getId());
                pstmt.executeUpdate();
                wizardList.remove(w);
                rootPane.getChildren().clear(); rootPane.getChildren().add(pagination); updatePagination();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    private void showDetails(Wizard w) {
        VBox details = new VBox(20); details.setAlignment(Pos.CENTER);
        ImageView iv = new ImageView();
        // Llamamos a w.getImage(), que activará la carga perezosa si no existe
        if (w.getImage() != null && !w.getImage().isError()) iv.setImage(w.getImage()); else iv.setImage(DEFAULT_IMAGE);
        iv.setFitHeight(250); iv.setPreserveRatio(true); iv.getStyleClass().add("detail-image");
        Label title = new Label(w.getName()); title.getStyleClass().add("detail-title");

        HBox buttonsBox = new HBox(20); buttonsBox.setAlignment(Pos.CENTER);
        Button backBtn = new Button(getText("btn_back")); backBtn.getStyleClass().add("button-back");
        backBtn.setOnAction(e -> { rootPane.getChildren().clear(); rootPane.getChildren().add(pagination); });

        Button pdfProfileBtn = new Button("PDF");
        pdfProfileBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        pdfProfileBtn.setOnAction(e -> {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:hogwarts.db")) {
                reportService.printWizardProfile(w.getId(), conn, rootPane.getScene().getWindow());
            } catch (SQLException ex) { ex.printStackTrace(); }
        });

        Button deleteBtn = new Button("Eliminar"); deleteBtn.getStyleClass().add("button-delete");
        deleteBtn.setOnAction(e -> deleteWizard(w));

        buttonsBox.getChildren().addAll(backBtn, pdfProfileBtn, deleteBtn);
        details.getChildren().addAll(iv, title, new Label(getText("label_house") + w.getHouse()), new Label(getText("label_wand") + w.getWand()), buttonsBox);
        rootPane.getChildren().clear(); rootPane.getChildren().add(details);
    }

    // --- UI Helpers ---
    private void updateInterfaceLanguage() {
        String titleText = getText("app_title");
        titleLabel.setText(titleText);
        searchField.setPromptText(getText("search_placeholder"));
        if (rootPane.getScene() != null && rootPane.getScene().getWindow() != null) {
            ((Stage) rootPane.getScene().getWindow()).setTitle(titleText);
        }
        int idx = filterTypeCombo.getSelectionModel().getSelectedIndex();
        updateFilterCombo();
        if (idx >= 0) filterTypeCombo.getSelectionModel().select(idx);
    }
    private void updateFilterCombo() { filterTypeCombo.setItems(FXCollections.observableArrayList(getText("filter_name"), getText("filter_house"), getText("filter_wand"))); }
    private String getText(String key) {
        if (currentLang.equals("EN")) {
            switch(key){ case "app_title": return "HOGWARTS YEARBOOK"; case "search_placeholder": return "Search wizard..."; case "filter_name": return "Name"; case "filter_house": return "House"; case "filter_wand": return "Wand"; case "label_house": return "House: "; case "label_wand": return "Wand: "; case "btn_back": return "Back to list"; default: return key; }
        } else {
            switch(key){ case "app_title": return "ANUARIO HOGWARTS"; case "search_placeholder": return "Buscar alumno..."; case "filter_name": return "Nombre"; case "filter_house": return "Casa"; case "filter_wand": return "Varita"; case "label_house": return "Casa: "; case "label_wand": return "Varita: "; case "btn_back": return "Volver al listado"; default: return key; }
        }
    }
    private void updateFilter() {
        String txt = searchField.getText();
        int type = filterTypeCombo.getSelectionModel().getSelectedIndex();
        filteredData.setPredicate(w -> {
            if (txt == null || txt.isEmpty()) return true;
            String l = txt.toLowerCase();
            switch(type){ case 0: return w.getName().toLowerCase().contains(l); case 1: return w.getHouse().toLowerCase().contains(l); case 2: return w.getWand().toLowerCase().contains(l); default: return true; }
        });
        updatePagination();
    }
    private void setupPagination() { pagination = new Pagination(1, 0); updatePagination(); rootPane.getChildren().add(pagination); }
    private void updatePagination() {
        int pc = (int) Math.ceil((double) filteredData.size() / ITEMS_PER_PAGE);
        pagination.setPageCount(pc > 0 ? pc : 1);
        pagination.setPageFactory(this::createPage);
    }
    private Node createPage(int idx) {
        TilePane tp = new TilePane(20, 20); tp.setPadding(new Insets(20)); tp.setPrefColumns(4); tp.setAlignment(Pos.TOP_CENTER);
        int start = idx * ITEMS_PER_PAGE; int end = Math.min(start + ITEMS_PER_PAGE, filteredData.size());
        for (int i = start; i < end; i++) tp.getChildren().add(createCard(filteredData.get(i)));
        ScrollPane sp = new ScrollPane(tp); sp.setFitToWidth(true); sp.setStyle("-fx-background-color:transparent;");
        return sp;
    }
    private VBox createCard(Wizard w) {
        VBox c = new VBox(10); c.setAlignment(Pos.TOP_CENTER); c.setPrefSize(200, 260); c.getStyleClass().add("card");
        String h = (w.getHouse() != null) ? w.getHouse().toLowerCase() : "";
        if(h.contains("gryffindor")) c.getStyleClass().add("card-gryffindor"); else if(h.contains("slytherin")) c.getStyleClass().add("card-slytherin"); else if(h.contains("ravenclaw")) c.getStyleClass().add("card-ravenclaw"); else if(h.contains("hufflepuff")) c.getStyleClass().add("card-hufflepuff"); else c.getStyleClass().add("card-default");
        ImageView iv = new ImageView();
        // Carga Perezosa: Llamamos a w.getImage() solo para las 8 tarjetas visibles
        if(w.getImage()!=null && !w.getImage().isError()) iv.setImage(w.getImage()); else iv.setImage(DEFAULT_IMAGE);
        iv.setFitHeight(140); iv.setFitWidth(140); iv.setPreserveRatio(true);
        StackPane ic = new StackPane(iv); ic.setPrefHeight(140);
        Label n = new Label(w.getName()); n.getStyleClass().add("card-title"); n.setWrapText(true); n.setTextAlignment(TextAlignment.CENTER);
        Label hl = new Label(getText("label_house") + w.getHouse()); hl.getStyleClass().add("card-subtitle");
        c.getChildren().addAll(ic, n, hl);
        c.setOnMouseClicked(e -> showDetails(w));
        return c;
    }
}