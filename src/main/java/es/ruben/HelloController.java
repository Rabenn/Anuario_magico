package es.ruben;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * <h2>Controlador de la Vista Principal - HelloController</h2>
 * Versión Final: Ejecuta Python en CMD visible.
 */
public class HelloController {

    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);

    @FXML private Label titleLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private ComboBox<String> langCombo;
    @FXML private StackPane rootPane;
    @FXML private Button addBtn;
    @FXML private Button pdfBtn;

    // --- Elementos del Menú ---
    @FXML private Menu menuFile;
    @FXML private MenuItem menuImportItem;
    @FXML private Menu menuHelp;
    @FXML private MenuItem menuManualItem;

    private ObservableList<Wizard> wizardList = FXCollections.observableArrayList();
    private FilteredList<Wizard> filteredData;
    private Pagination pagination;
    private final int ITEMS_PER_PAGE = 8;

    private String currentLang = "ES";
    private final Image DEFAULT_IMAGE = new Image("https://img.icons8.com/ios-filled/150/000000/user-male-circle.png", true);

    private final ReportService reportService = new ReportService();
    private Wizard currentWizard = null;

    // CAMBIO 1: Apuntamos al script de Python en lugar del EXE
    private final String IMPORT_SCRIPT_NAME = "etl_files.py";

    @FXML
    public void initialize() {
        loadFromHeterogeneousFiles();
        filteredData = new FilteredList<>(wizardList, p -> true);

        // --- Configuración Menú ---
        menuImportItem.setOnAction(e -> runImportProcess(null));
        menuManualItem.setOnAction(e -> openUserManual());

        // Configuración de Idiomas
        langCombo.setItems(FXCollections.observableArrayList("Español", "English"));
        langCombo.getSelectionModel().selectFirst();

        langCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentLang = newVal.equals("English") ? "EN" : "ES";
            updateInterfaceLanguage();
            if (wizardList.isEmpty()) {
                showEmptyState();
            } else {
                updatePagination();
            }
        });

        updateFilterCombo();
        filterTypeCombo.getSelectionModel().selectFirst();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilter());
        filterTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter());

        addBtn.setOnAction(e -> showAddWizardDialog());
        pdfBtn.setOnAction(e -> reportService.printYearbook(new ArrayList<>(wizardList), rootPane.getScene().getWindow()));

        if (wizardList.isEmpty()) {
            showEmptyState();
            updateInterfaceLanguage();
        } else {
            setupPagination();
            updateInterfaceLanguage();
        }
    }

    // --- LÓGICA MANUAL DE USUARIO (VISOR INTERNO) ---
    private void openUserManual() {
        try {
            Stage helpStage = new Stage();
            helpStage.setTitle(getText("menu_manual"));

            try {
                InputStream iconStream = getClass().getResourceAsStream("images/icono.png");
                if (iconStream != null) {
                    helpStage.getIcons().add(new Image(iconStream));
                }
            } catch (Exception e) {
                logger.warn("No se pudo cargar el icono para la ventana de ayuda", e);
            }

            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();

            String resourcePath = "/es/ruben/MANUAL DE USUARIO/Manual de Usuario RETO3/index.html";
            URL url = getClass().getResource(resourcePath);

            if (url == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "No se encuentra el archivo del manual:\n" + resourcePath);
                return;
            }

            webEngine.load(url.toExternalForm());

            Scene scene = new Scene(webView, 1000, 700);
            helpStage.setScene(scene);
            helpStage.show();

        } catch (Exception e) {
            logger.error("Error al abrir el manual interno", e);
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo abrir el visor de ayuda.\n" + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        try {
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png")));
        } catch(Exception ex){}
        alert.showAndWait();
    }

    // --- LÓGICA IMPORTACIÓN (CAMBIO PRINCIPAL) ---
    private void runImportProcess(Button sourceBtn) {
        if (sourceBtn != null) {
            sourceBtn.setDisable(true);
            sourceBtn.setText(currentLang.equals("ES") ? "Abriendo CMD..." : "Opening CMD...");
        }
        menuImportItem.setDisable(true);

        Thread taskThread = new Thread(() -> {
            try {
                File scriptFile = new File(IMPORT_SCRIPT_NAME);
                if (!scriptFile.exists()) throw new FileNotFoundException("Script no encontrado: " + IMPORT_SCRIPT_NAME);

                // --- COMANDO PARA ABRIR CMD VISIBLE ---
                // "cmd /c start /wait" -> Abre una ventana nueva y ESPERA a que la cierres
                // "cmd /k python..." -> Ejecuta python y MANTIENE la ventana abierta (para ver errores)
                ProcessBuilder pb = new ProcessBuilder(
                        "cmd", "/c", "start", "/wait", "cmd", "/k", "python " + IMPORT_SCRIPT_NAME
                );

                Process process = pb.start();

                // Java se quedará congelado en esta línea hasta que TÚ cierres la ventana negra del CMD
                int exitCode = process.waitFor();

                Platform.runLater(() -> {
                    // Al cerrar la ventana, asumimos que el usuario ya terminó
                    loadFromHeterogeneousFiles();

                    if (!wizardList.isEmpty()) {
                        rootPane.getChildren().clear();
                        setupPagination();
                        updateInterfaceLanguage();
                        showAlert(Alert.AlertType.INFORMATION, "Importación", getText("msg_import_success"));
                    } else {
                        // Si cerro la ventana y sigue vacío, quizás dio error el python
                        if (sourceBtn != null) resetImportButton(sourceBtn);
                    }
                    menuImportItem.setDisable(false);
                });

            } catch (Exception e) {
                logger.error("Error lanzando proceso de importación", e);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error", "Error al abrir el script:\n" + e.getMessage());
                    if (sourceBtn != null) resetImportButton(sourceBtn);
                    menuImportItem.setDisable(false);
                });
            }
        });
        taskThread.start();
    }

    private void resetImportButton(Button btn) {
        btn.setDisable(false);
        btn.setText(getText("btn_import_big"));
    }

    // --- ACTUALIZACIÓN IDIOMAS ---
    private void updateInterfaceLanguage() {
        String titleText = getText("app_title");
        titleLabel.setText(titleText);
        searchField.setPromptText(getText("search_placeholder"));

        menuFile.setText(getText("menu_file"));
        menuImportItem.setText(getText("menu_import"));
        menuHelp.setText(getText("menu_help"));
        menuManualItem.setText(getText("menu_manual"));

        addBtn.setText(getText("btn_add"));
        addBtn.setGraphic(createIconLabel("✚"));
        addBtn.setContentDisplay(ContentDisplay.LEFT);

        pdfBtn.setText(getText("btn_pdf"));
        pdfBtn.setGraphic(createIconLabel("📄"));
        pdfBtn.setContentDisplay(ContentDisplay.LEFT);

        addBtn.setTooltip(createTooltip(getText("tooltip_add")));
        pdfBtn.setTooltip(createTooltip(getText("tooltip_pdf_main")));
        searchField.setTooltip(createTooltip(getText("tooltip_search")));
        filterTypeCombo.setTooltip(createTooltip(getText("tooltip_filter")));
        langCombo.setTooltip(createTooltip(getText("tooltip_lang")));

        if (rootPane.getScene() != null && rootPane.getScene().getWindow() != null) {
            ((Stage) rootPane.getScene().getWindow()).setTitle(titleText);
        }

        int idx = filterTypeCombo.getSelectionModel().getSelectedIndex();
        updateFilterCombo();
        if (idx >= 0) filterTypeCombo.getSelectionModel().select(idx);

        if (currentWizard != null) showDetails(currentWizard);
    }

    // --- TEXTOS E IDIOMAS ---
    private String getText(String key) {
        if (currentLang.equals("EN")) {
            switch(key){
                case "app_title": return "HOGWARTS YEARBOOK";
                case "search_placeholder": return "Search wizard...";
                case "filter_name": return "Name";
                case "filter_house": return "House";
                case "filter_wand": return "Wand";
                case "label_house": return "House: ";
                case "label_wand": return "Wand: ";
                case "btn_back": return "Back to list";
                case "btn_delete": return "Expel";
                case "btn_add": return "ADD";
                case "btn_pdf": return "PDF";

                case "menu_file": return "File";
                case "menu_import": return "Run Import Script (Python)...";
                case "menu_help": return "Help";
                case "menu_manual": return "User Manual";

                case "btn_import_big": return "RUN PYTHON SCRIPT";
                case "msg_empty_db": return "No wizards found in the database.";
                case "msg_click_import": return "Click below to open the Python ETL script in CMD:";

                case "tooltip_add": return "Add a new wizard to the database";
                case "tooltip_pdf_main": return "Generate full yearbook PDF";
                case "tooltip_import": return "Open external Python script";
                case "tooltip_search": return "Type to filter by text";
                case "tooltip_filter": return "Select filter criteria";
                case "tooltip_lang": return "Change application language";
                case "tooltip_back": return "Return to wizard list";
                case "tooltip_pdf_profile": return "Generate profile PDF for this wizard";
                case "tooltip_delete": return "Permanently expel this wizard";
                case "tooltip_photo": return "Select an image file (JPG/PNG)";
                case "tooltip_card": return "Click to view details";
                case "msg_no_results": return "🔍 No matches found for these criteria.";
                case "msg_import_success": return "Data reloaded successfully!";
                case "msg_import_error": return "Error importing data.";
                default: return key;
            }
        } else {
            switch(key){
                case "app_title": return "ANUARIO MÁGICO";
                case "search_placeholder": return "Buscar alumno...";
                case "filter_name": return "Nombre";
                case "filter_house": return "Casa";
                case "filter_wand": return "Varita";
                case "label_house": return "Casa: ";
                case "label_wand": return "Varita: ";
                case "btn_back": return "Volver al listado";
                case "btn_delete": return "Expulsar";
                case "btn_add": return "AÑADIR";
                case "btn_pdf": return "PDF";

                case "menu_file": return "Archivo";
                case "menu_import": return "Ejecutar Script Python...";
                case "menu_help": return "Ayuda";
                case "menu_manual": return "Manual de Usuario";

                case "btn_import_big": return "EJECUTAR SCRIPT PYTHON";
                case "msg_empty_db": return "No hay alumnos en la base de datos.";
                case "msg_click_import": return "Haz clic abajo para abrir el script Python en CMD:";

                case "tooltip_add": return "Añadir nuevo mago a la base de datos";
                case "tooltip_pdf_main": return "Generar anuario completo en PDF";
                case "tooltip_import": return "Abrir script externo de Python";
                case "tooltip_search": return "Escribe para filtrar por texto";
                case "tooltip_filter": return "Seleccionar criterio de filtro";
                case "tooltip_lang": return "Cambiar idioma de la aplicación";
                case "tooltip_back": return "Volver al listado de alumnos";
                case "tooltip_pdf_profile": return "Generar perfil PDF de este mago";
                case "tooltip_delete": return "Expulsar permanentemente a este alumno";
                case "tooltip_photo": return "Seleccionar archivo de imagen (JPG/PNG)";
                case "tooltip_card": return "Haz click para ver detalles";
                case "msg_no_results": return "🔍 No existen coincidencias con esos criterios.";
                case "msg_import_success": return "¡Datos recargados correctamente!";
                case "msg_import_error": return "Error al importar datos.";
                default: return key;
            }
        }
    }

    private void updatePagination() {
        if (pagination == null) return;
        int pc = (int) Math.ceil((double) filteredData.size() / ITEMS_PER_PAGE);
        pagination.setPageCount(pc > 0 ? pc : 1);
        pagination.setPageFactory(this::createPage);
    }

    private void showEmptyState() {
        rootPane.getChildren().clear();
        pagination = null;

        Label emptyLabel = new Label(getText("msg_empty_db"));
        emptyLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #7f8c8d;");

        Label instructionLabel = new Label(getText("msg_click_import"));
        instructionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #95a5a6;");

        Button btnImport = new Button(getText("btn_import_big"));
        btnImport.setStyle("-fx-font-size: 16px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-cursor: hand;");
        btnImport.setTooltip(createTooltip(getText("tooltip_import")));

        btnImport.setGraphic(createIconLabel("📥"));
        btnImport.setContentDisplay(ContentDisplay.LEFT);

        btnImport.setOnAction(e -> runImportProcess(btnImport));

        VBox box = new VBox(20, emptyLabel, instructionLabel, btnImport);
        box.setAlignment(Pos.CENTER);
        rootPane.getChildren().add(box);
    }

    private Label createIconLabel(String symbol) {
        Label l = new Label(symbol);
        l.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 5 0 0;");
        return l;
    }

    private Tooltip createTooltip(String text) {
        Tooltip t = new Tooltip(text);
        t.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px; -fx-background-color: rgba(30,30,30,0.9); -fx-text-fill: white;");
        t.setShowDelay(Duration.millis(200));
        return t;
    }

    private void updateFilterCombo() {
        filterTypeCombo.setItems(FXCollections.observableArrayList(getText("filter_name"), getText("filter_house"), getText("filter_wand")));
    }

    private void setupPagination() {
        pagination = new Pagination(1, 0);
        updatePagination();
        rootPane.getChildren().add(pagination);
    }

    private void updateFilter() {
        String txt = searchField.getText();
        int type = filterTypeCombo.getSelectionModel().getSelectedIndex();
        filteredData.setPredicate(w -> {
            if (txt == null || txt.isEmpty()) return true;
            String l = txt.toLowerCase();
            switch(type){
                case 0: return w.getName().toLowerCase().contains(l);
                case 1: return w.getHouse().toLowerCase().contains(l);
                case 2: return w.getWand().toLowerCase().contains(l);
                default: return true;
            }
        });
        updatePagination();
    }

    // --- LÓGICA DE CARGA Y RENDERIZADO (Sin cambios) ---
    private Node createPage(int idx) {
        if (filteredData.isEmpty()) {
            Label noResultsLabel = new Label(getText("msg_no_results"));
            noResultsLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: #95a5a6; -fx-font-weight: bold;");
            VBox emptyBox = new VBox(noResultsLabel);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(50));
            return emptyBox;
        }

        TilePane tp = new TilePane(20, 20);
        tp.setPadding(new Insets(20));
        tp.setPrefColumns(4);
        tp.setAlignment(Pos.TOP_CENTER);
        tp.setPrefWidth(200 * 4 + 20 * 3 + 40);
        tp.setMaxWidth(Region.USE_PREF_SIZE);

        int start = idx * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filteredData.size());
        for (int i = start; i < end; i++) {
            tp.getChildren().add(createCard(filteredData.get(i)));
        }

        HBox centeringWrapper = new HBox(tp);
        centeringWrapper.setAlignment(Pos.CENTER);
        centeringWrapper.setFillHeight(true);

        ScrollPane sp = new ScrollPane(centeringWrapper);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent; -fx-background: transparent;");
        return sp;
    }

    private VBox createCard(Wizard w) {
        VBox c = new VBox(10); c.setAlignment(Pos.TOP_CENTER); c.setPrefSize(200, 260); c.getStyleClass().add("card");
        String h = (w.getHouse() != null) ? w.getHouse().toLowerCase() : "";
        if(h.contains("gryffindor")) c.getStyleClass().add("card-gryffindor");
        else if(h.contains("slytherin")) c.getStyleClass().add("card-slytherin");
        else if(h.contains("ravenclaw")) c.getStyleClass().add("card-ravenclaw");
        else if(h.contains("hufflepuff")) c.getStyleClass().add("card-hufflepuff");
        else c.getStyleClass().add("card-default");

        Tooltip.install(c, createTooltip(getText("tooltip_card")));

        ImageView iv = new ImageView();
        if(w.getImage()!=null && !w.getImage().isError()) iv.setImage(w.getImage()); else iv.setImage(DEFAULT_IMAGE);
        iv.setFitHeight(140); iv.setFitWidth(140); iv.setPreserveRatio(true);

        StackPane ic = new StackPane(iv); ic.setPrefHeight(140);
        Label n = new Label(w.getName()); n.getStyleClass().add("card-title"); n.setWrapText(true); n.setTextAlignment(TextAlignment.CENTER);
        Label hl = new Label(getText("label_house") + w.getHouse()); hl.getStyleClass().add("card-subtitle");

        c.getChildren().addAll(ic, n, hl);
        c.setOnMouseClicked(e -> showDetails(w));
        return c;
    }

    private void loadFromHeterogeneousFiles() {
        Map<String, Wizard> tempMap = new HashMap<>();
        try {
            File jsonFile = new File("nombres.json");
            if (jsonFile.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootArray = mapper.readTree(jsonFile);
                if (rootArray.isArray()) {
                    for (JsonNode node : rootArray) {
                        Wizard w = new Wizard();
                        w.setId(node.get("id").asText());
                        w.setName(node.get("name").asText());
                        w.setHouse(node.get("house").asText());
                        tempMap.put(w.getId(), w);
                    }
                }
            }

            File xmlFile = new File("varitas.xml");
            if (xmlFile.exists()) {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(xmlFile);
                doc.getDocumentElement().normalize();
                NodeList nList = doc.getElementsByTagName("Wizard");
                for (int i = 0; i < nList.getLength(); i++) {
                    Element el = (Element) nList.item(i);
                    Wizard w = tempMap.get(el.getAttribute("id"));
                    if (w != null) w.setWand(el.getElementsByTagName("Wand").item(0).getTextContent());
                }
            }

            File csvFile = new File("imagenes.csv");
            if (csvFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                    String line;
                    boolean header = true;
                    while ((line = br.readLine()) != null) {
                        if (header) { header = false; continue; }
                        String[] parts = line.split(",", 2);
                        if (parts.length == 2) {
                            Wizard w = tempMap.get(parts[0].trim());
                            if (w != null && !parts[1].trim().isEmpty()) {
                                try {
                                    w.setImageBytes(Base64.getDecoder().decode(parts[1].trim()));
                                } catch (Exception e) {
                                    logger.warn("Error decodificando Base64 para el ID: {}", parts[0]);
                                }
                            }
                        }
                    }
                }
            }
            wizardList.clear();
            List<Wizard> sortedList = new ArrayList<>(tempMap.values());
            sortedList.sort(Comparator.comparing(Wizard::getName));
            wizardList.addAll(sortedList);
        } catch (Exception e) {
            logger.error("Error en carga heterogénea: ", e);
        }
    }

    private void saveChangesToFiles() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            ArrayNode jsonArray = mapper.createArrayNode();
            for (Wizard w : wizardList) {
                ObjectNode node = mapper.createObjectNode();
                node.put("id", w.getId());
                node.put("name", w.getName());
                node.put("house", w.getHouse());
                jsonArray.add(node);
            }
            mapper.writeValue(new File("nombres.json"), jsonArray);

            try (PrintWriter pw = new PrintWriter(new FileWriter("varitas.xml", StandardCharsets.UTF_8))) {
                pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                pw.println("<WizardsWands>");
                for (Wizard w : wizardList) {
                    String safe = (w.getWand() == null ? "" : w.getWand()).replace("&", "&amp;").replace("<", "&lt;");
                    pw.println("  <Wizard id=\"" + w.getId() + "\"><Wand>" + safe + "</Wand></Wizard>");
                }
                pw.println("</WizardsWands>");
            }

            try (PrintWriter pw = new PrintWriter(new FileWriter("imagenes.csv", StandardCharsets.UTF_8))) {
                pw.println("id,imagen_base64");
                for (Wizard w : wizardList) {
                    String b64 = w.getBase64Image();
                    pw.println(w.getId() + "," + (b64 != null ? b64 : ""));
                }
            }
        } catch (Exception e) {
            logger.error("Error al persistir cambios: ", e);
        }
    }

    private void showAddWizardDialog() {
        Dialog<Wizard> dialog = new Dialog<>();
        dialog.setTitle(currentLang.equals("ES") ? "Añadir Nuevo Mago" : "Add New Wizard");
        try {
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png")));
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("css/styles.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dialog-pane");
        } catch (Exception e) {}

        ButtonType saveButtonType = new ButtonType(currentLang.equals("ES") ? "Guardar" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        String noHouseOption = currentLang.equals("ES") ? "Sin Casa" : "No House";
        ComboBox<String> houseCombo = new ComboBox<>();
        houseCombo.setItems(FXCollections.observableArrayList(noHouseOption, "Gryffindor", "Slytherin", "Ravenclaw", "Hufflepuff"));
        houseCombo.setValue(noHouseOption);
        TextField wandField = new TextField();

        Button imgBtn = new Button(currentLang.equals("ES") ? "Seleccionar Foto..." : "Select Photo...");
        imgBtn.setTooltip(createTooltip(getText("tooltip_photo")));
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
                        imageObj = new Image(new FileInputStream(selectedFile[0]));
                    } catch (IOException ex) {}
                }
                String newId = UUID.randomUUID().toString();
                return new Wizard(newId, nameField.getText(), houseCombo.getValue(), wandField.getText(), imageObj, imageBytes);
            }
            return null;
        });

        Optional<Wizard> result = dialog.showAndWait();
        result.ifPresent(wizard -> {
            boolean wasEmpty = wizardList.isEmpty();
            wizardList.add(0, wizard);
            saveChangesToFiles();
            if (wasEmpty) {
                rootPane.getChildren().clear();
                setupPagination();
                updateInterfaceLanguage();
            } else {
                updatePagination();
            }
        });
    }

    private void deleteWizard(Wizard w) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(currentLang.equals("ES") ? "Expediente Disciplinario" : "Expulsion Record");
        alert.setHeaderText(currentLang.equals("ES") ? "¿Expulsar a " + w.getName() + "?" : "Expel " + w.getName() + "?");
        alert.setContentText(currentLang.equals("ES") ? "Esta acción es irreversible." : "This action cannot be undone.");

        try {
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png")));
        } catch (Exception e) {}

        if (w.getImage() != null) {
            ImageView imageView = new ImageView(w.getImage());
            imageView.setFitHeight(60); imageView.setFitWidth(60);
            imageView.setClip(new Circle(30, 30, 30));
            alert.setGraphic(imageView);
        }

        ButtonType btnEliminar = new ButtonType(currentLang.equals("ES") ? "Expulsar" : "Expel", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType(currentLang.equals("ES") ? "Cancelar" : "Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnEliminar, btnCancelar);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == btnEliminar) {
            wizardList.remove(w);
            saveChangesToFiles();
            currentWizard = null;
            rootPane.getChildren().clear();

            if (wizardList.isEmpty()) {
                showEmptyState();
                updateInterfaceLanguage();
            } else {
                setupPagination();
                updatePagination();
            }
        }
    }

    private void showDetails(Wizard w) {
        currentWizard = w;
        VBox details = new VBox(20); details.setAlignment(Pos.CENTER);
        ImageView iv = new ImageView();
        if (w.getImage() != null && !w.getImage().isError()) iv.setImage(w.getImage()); else iv.setImage(DEFAULT_IMAGE);
        iv.setFitHeight(250); iv.setPreserveRatio(true); iv.getStyleClass().add("detail-image");

        Label title = new Label(w.getName()); title.getStyleClass().add("detail-title");
        HBox buttonsBox = new HBox(20); buttonsBox.setAlignment(Pos.CENTER);

        Button backBtn = new Button(getText("btn_back"));
        backBtn.getStyleClass().add("button-back");
        backBtn.setTooltip(createTooltip(getText("tooltip_back")));
        backBtn.setOnAction(e -> {
            currentWizard = null;
            rootPane.getChildren().clear();
            rootPane.getChildren().add(pagination);
        });

        Button pdfProfileBtn = new Button("PDF");
        pdfProfileBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold;");
        pdfProfileBtn.setTooltip(createTooltip(getText("tooltip_pdf_profile")));
        pdfProfileBtn.setOnAction(e -> reportService.printWizardProfile(w, rootPane.getScene().getWindow()));

        Button deleteBtn = new Button(getText("btn_delete"));
        deleteBtn.getStyleClass().add("button-delete");
        deleteBtn.setTooltip(createTooltip(getText("tooltip_delete")));
        deleteBtn.setOnAction(e -> deleteWizard(w));

        buttonsBox.getChildren().addAll(backBtn, pdfProfileBtn, deleteBtn);
        details.getChildren().addAll(iv, title, new Label(getText("label_house") + w.getHouse()), new Label(getText("label_wand") + w.getWand()), buttonsBox);

        rootPane.getChildren().clear();
        rootPane.getChildren().add(details);
    }
}