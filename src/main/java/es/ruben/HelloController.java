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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.sql.*;

public class HelloController {

    // --- VARIABLES DE LA VISTA (FXML) ---
    @FXML private Label titleLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private ComboBox<String> langCombo;
    @FXML private StackPane rootPane;

    // --- VARIABLES DE DATOS ---
    private ObservableList<Wizard> wizardList = FXCollections.observableArrayList();
    private FilteredList<Wizard> filteredData;
    private Pagination pagination;
    private final int ITEMS_PER_PAGE = 8;

    // Idioma actual (ES por defecto)
    private String currentLang = "ES";

    // Imagen segura por si falla la descarga o no hay foto
    private final Image DEFAULT_IMAGE = new Image("https://img.icons8.com/ios-filled/150/000000/user-male-circle.png", true);

    // --- INICIALIZACIÓN ---
    @FXML
    public void initialize() {
        loadFromDatabase();

        filteredData = new FilteredList<>(wizardList, p -> true);

        // 1. Configurar Idiomas
        langCombo.setItems(FXCollections.observableArrayList("Español", "English"));
        langCombo.getSelectionModel().selectFirst();
        langCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentLang = newVal.equals("English") ? "EN" : "ES";
            updateInterfaceLanguage();
            updatePagination(); // Recargar tarjetas para traducir sus textos
        });

        // 2. Configurar Filtros
        updateFilterCombo(); // Llenar opciones traducidas
        filterTypeCombo.getSelectionModel().selectFirst();

        // 3. Listeners de búsqueda
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilter());
        filterTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter());

        // 4. Cargar datos
        if (wizardList.isEmpty()) {
            Label emptyLabel = new Label("⚠️ No hay datos. Ejecuta el script de Python.");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: red;");
            rootPane.getChildren().add(emptyLabel);
        } else {
            setupPagination();
            updateInterfaceLanguage(); // Aplicar idioma inicial
        }
    }

    // --- TRADUCCIÓN E INTERFAZ ---
    private void updateInterfaceLanguage() {
        // Texto interno
        String titleText = getText("app_title");
        titleLabel.setText(titleText);
        searchField.setPromptText(getText("search_placeholder"));

        // CAMBIO DE TÍTULO DE VENTANA (Window Title)
        if (rootPane.getScene() != null && rootPane.getScene().getWindow() != null) {
            ((Stage) rootPane.getScene().getWindow()).setTitle(titleText);
        }

        // Actualizar combo de filtros manteniendo la selección
        int selectedIndex = filterTypeCombo.getSelectionModel().getSelectedIndex();
        updateFilterCombo();
        if (selectedIndex >= 0) filterTypeCombo.getSelectionModel().select(selectedIndex);
    }

    private void updateFilterCombo() {
        filterTypeCombo.setItems(FXCollections.observableArrayList(
                getText("filter_name"),
                getText("filter_house"),
                getText("filter_wand")
        ));
    }

    private String getText(String key) {
        if (currentLang.equals("EN")) {
            switch (key) {
                case "app_title": return "HOGWARTS YEARBOOK";
                case "search_placeholder": return "Search wizard...";
                case "filter_name": return "Name";
                case "filter_house": return "House";
                case "filter_wand": return "Wand";
                case "label_house": return "House: ";
                case "label_wand": return "Wand: ";
                case "btn_back": return "Back to list";
                default: return key;
            }
        } else {
            switch (key) {
                case "app_title": return "ANUARIO HOGWARTS";
                case "search_placeholder": return "Buscar alumno...";
                case "filter_name": return "Nombre";
                case "filter_house": return "Casa";
                case "filter_wand": return "Varita";
                case "label_house": return "Casa: ";
                case "label_wand": return "Varita: ";
                case "btn_back": return "Volver al listado";
                default: return key;
            }
        }
    }

    // --- LÓGICA DE FILTRADO ---
    private void updateFilter() {
        String searchText = searchField.getText();
        int searchTypeIndex = filterTypeCombo.getSelectionModel().getSelectedIndex();

        filteredData.setPredicate(wizard -> {
            if (searchText == null || searchText.isEmpty()) return true;
            String lower = searchText.toLowerCase();

            switch (searchTypeIndex) {
                case 0: return wizard.getName().toLowerCase().contains(lower);  // Nombre
                case 1: return wizard.getHouse().toLowerCase().contains(lower); // Casa
                case 2: return wizard.getWand().toLowerCase().contains(lower);  // Varita
                default: return true;
            }
        });
        updatePagination();
    }

    // --- PAGINACIÓN Y TARJETAS ---
    private void setupPagination() {
        pagination = new Pagination(1, 0);
        updatePagination();
        rootPane.getChildren().add(pagination);
    }

    private void updatePagination() {
        int pageCount = (int) Math.ceil((double) filteredData.size() / ITEMS_PER_PAGE);
        pagination.setPageCount(pageCount > 0 ? pageCount : 1);
        pagination.setPageFactory(this::createPage);
    }

    private Node createPage(int pageIndex) {
        TilePane tilePane = new TilePane(20, 20);
        tilePane.setPadding(new Insets(20));
        tilePane.setPrefColumns(4);
        tilePane.setAlignment(Pos.TOP_CENTER);

        int start = pageIndex * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filteredData.size());

        for (int i = start; i < end; i++) {
            tilePane.getChildren().add(createCard(filteredData.get(i)));
        }

        ScrollPane sp = new ScrollPane(tilePane);
        sp.setFitToWidth(true);
        // Hacemos el ScrollPane transparente para ver el fondo "pergamino" del CSS
        sp.setStyle("-fx-background-color:transparent; -fx-background: transparent;");
        return sp;
    }

    // --- CREACIÓN DE TARJETAS (LÓGICA DE COLORES) ---
    private VBox createCard(Wizard w) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefSize(200, 260);
        card.setMaxSize(200, 260);

        // 1. Asignamos la clase base CSS
        card.getStyleClass().add("card");

        // 2. LÓGICA DE COLORES CORPORATIVOS
        // Comprobamos la casa y asignamos la clase CSS correspondiente
        String house = (w.getHouse() != null) ? w.getHouse().toLowerCase() : "";

        if (house.contains("gryffindor")) {
            card.getStyleClass().add("card-gryffindor");
        } else if (house.contains("slytherin")) {
            card.getStyleClass().add("card-slytherin");
        } else if (house.contains("ravenclaw")) {
            card.getStyleClass().add("card-ravenclaw");
        } else if (house.contains("hufflepuff")) {
            card.getStyleClass().add("card-hufflepuff");
        } else {
            card.getStyleClass().add("card-default");
        }

        // IMAGEN
        ImageView iv = new ImageView();
        iv.setFitHeight(140); iv.setFitWidth(140); iv.setPreserveRatio(true);
        if (w.getImage() != null && !w.getImage().isError()) iv.setImage(w.getImage());
        else iv.setImage(DEFAULT_IMAGE);

        StackPane imgContainer = new StackPane(iv);
        imgContainer.setPrefHeight(140); imgContainer.setMinHeight(140);

        // TEXTOS
        Label nameLbl = new Label(w.getName());
        nameLbl.getStyleClass().add("card-title"); // Clase CSS para fuente
        nameLbl.setWrapText(true);
        nameLbl.setTextAlignment(TextAlignment.CENTER);

        Label houseLbl = new Label(getText("label_house") + w.getHouse());
        houseLbl.getStyleClass().add("card-subtitle"); // Clase CSS para subtítulo

        card.getChildren().addAll(imgContainer, nameLbl, houseLbl);

        // El evento Click
        card.setOnMouseClicked(e -> showDetails(w));

        return card;
    }

    // --- VISTA DETALLE ---
    private void showDetails(Wizard w) {
        VBox details = new VBox(20);
        details.setAlignment(Pos.CENTER);

        ImageView iv = new ImageView();
        if (w.getImage() != null && !w.getImage().isError()) iv.setImage(w.getImage());
        else iv.setImage(DEFAULT_IMAGE);
        iv.setFitHeight(250); iv.setPreserveRatio(true);
        iv.getStyleClass().add("detail-image"); // Clase CSS (sombra)

        Label title = new Label(w.getName());
        title.getStyleClass().add("detail-title"); // Clase CSS (fuente grande)

        // Botón con estilo corporativo (Rojo/Dorado definido en CSS)
        Button backBtn = new Button(getText("btn_back"));
        backBtn.getStyleClass().add("button-back");

        backBtn.setOnAction(e -> {
            rootPane.getChildren().clear();
            rootPane.getChildren().add(pagination);
        });

        details.getChildren().addAll(
                iv,
                title,
                new Label(getText("label_house") + w.getHouse()),
                new Label(getText("label_wand") + w.getWand()),
                backBtn
        );
        rootPane.getChildren().clear();
        rootPane.getChildren().add(details);
    }

    // --- BASE DE DATOS ---
    private void loadFromDatabase() {
        String url = "jdbc:sqlite:hogwarts.db";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, house, wand, image_blob FROM wizards")) {

            while (rs.next()) {
                byte[] imgBytes = rs.getBytes("image_blob");
                Image img = null;
                if (imgBytes != null && imgBytes.length > 0) {
                    try { img = new Image(new ByteArrayInputStream(imgBytes)); } catch (Exception ex) {}
                }
                wizardList.add(new Wizard(rs.getString("name"), rs.getString("house"), rs.getString("wand"), img));
            }
        } catch (SQLException e) { System.out.println("❌ Error DB: " + e.getMessage()); }
    }
}