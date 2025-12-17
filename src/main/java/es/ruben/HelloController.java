package es.ruben;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;


public class HelloController {


    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);


    @FXML private Label titleLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private ComboBox<String> langCombo;
    @FXML private StackPane rootPane;
    @FXML private Button addBtn;
    @FXML private Button pdfBtn;


    private ObservableList<Wizard> wizardList = FXCollections.observableArrayList();
    private FilteredList<Wizard> filteredData;
    private Pagination pagination;
    private final int ITEMS_PER_PAGE = 8;


    private String currentLang = "ES";
    private final Image DEFAULT_IMAGE = new Image("https://img.icons8.com/ios-filled/150/000000/user-male-circle.png", true);


    private final ReportService reportService = new ReportService();


    @FXML
    public void initialize() {
        logger.info("Inicializando HelloController y cargando datos de archivos...");


        loadFromHeterogeneousFiles();


        filteredData = new FilteredList<>(wizardList, p -> true);


        // Configuración de Idiomas
        langCombo.setItems(FXCollections.observableArrayList("Español", "English"));
        langCombo.getSelectionModel().selectFirst();
        langCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            logger.debug("Idioma cambiado a: {}", newVal);
            currentLang = newVal.equals("English") ? "EN" : "ES";
            updateInterfaceLanguage();
            updatePagination();
        });


        // Configuración de Filtros
        updateFilterCombo();
        filterTypeCombo.getSelectionModel().selectFirst();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilter());
        filterTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter());


        // Botones Principales
        addBtn.setOnAction(e -> showAddWizardDialog());


        pdfBtn.setOnAction(e -> {
            logger.info("Iniciando generación de reporte PDF del anuario completo.");
            reportService.printYearbook(new ArrayList<>(wizardList), rootPane.getScene().getWindow());
        });


        if (wizardList.isEmpty()) {
            logger.warn("No se encontraron datos en los archivos locales (nombres.json, varitas.xml, imagenes.csv).");
            Label emptyLabel = new Label("⚠️ No hay datos. Ejecuta el script Python (ETL) primero.");
            rootPane.getChildren().add(emptyLabel);
        } else {
            logger.info("Se han cargado {} magos con éxito.", wizardList.size());
            setupPagination();
            updateInterfaceLanguage();
        }
    }


    // --- CARGA DE DATOS (LECTURA DE 3 FORMATOS) ---
    private void loadFromHeterogeneousFiles() {
        Map<String, Wizard> tempMap = new HashMap<>();
        try {
            // 1. JSON: Nombres e IDs
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
                logger.debug("Cargados {} registros desde nombres.json", tempMap.size());
            } else {
                logger.error("Archivo crítico 'nombres.json' no encontrado.");
            }


            // 2. XML: Varitas
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
                logger.debug("Varitas vinculadas desde varitas.xml");
            }


            // 3. CSV: Imágenes en Base64
            File csvFile = new File("imagenes.csv");
            if (csvFile.exists()) {
                int imgCount = 0;
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
                                    imgCount++;
                                } catch (Exception e) {
                                    logger.warn("Error decodificando Base64 para el ID: {}", parts[0]);
                                }
                            }
                        }
                    }
                }
                logger.debug("Cargadas {} imágenes desde imagenes.csv", imgCount);
            }


            wizardList.clear();
            List<Wizard> sortedList = new ArrayList<>(tempMap.values());
            sortedList.sort(Comparator.comparing(Wizard::getName));
            wizardList.addAll(sortedList);


        } catch (Exception e) {
            logger.error("Error fatal en el motor de carga heterogénea: ", e);
        }
    }


    // --- GUARDADO DE DATOS (ESCRITURA EN 3 FORMATOS) ---
    private void saveChangesToFiles() {
        logger.info("Guardando cambios en el sistema de archivos...");
        try {
            // Guardar JSON
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


            // Guardar XML
            try (PrintWriter pw = new PrintWriter(new FileWriter("varitas.xml", StandardCharsets.UTF_8))) {
                pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                pw.println("<WizardsWands>");
                for (Wizard w : wizardList) {
                    String safe = (w.getWand() == null ? "" : w.getWand()).replace("&", "&amp;").replace("<", "&lt;");
                    pw.println("  <Wizard id=\"" + w.getId() + "\"><Wand>" + safe + "</Wand></Wizard>");
                }
                pw.println("</WizardsWands>");
            }


            // Guardar CSV
            try (PrintWriter pw = new PrintWriter(new FileWriter("imagenes.csv", StandardCharsets.UTF_8))) {
                pw.println("id,imagen_base64");
                for (Wizard w : wizardList) {
                    String b64 = w.getBase64Image();
                    pw.println(w.getId() + "," + (b64 != null ? b64 : ""));
                }
            }
            logger.info("Persistencia completada: {} magos sincronizados.", wizardList.size());
        } catch (Exception e) {
            logger.error("Error al intentar persistir los cambios: ", e);
        }
    }


    private void showAddWizardDialog() {
        Dialog<Wizard> dialog = new Dialog<>();
        dialog.setTitle(currentLang.equals("ES") ? "Añadir Nuevo Mago" : "Add New Wizard");
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png")));
        dialog.setHeaderText(null);
        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("css/styles.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dialog-pane");
        } catch (Exception e) {
            logger.warn("No se pudo cargar el CSS del diálogo.");
        }


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
                    } catch (IOException ex) {
                        logger.error("Error al procesar la imagen seleccionada.");
                    }
                }
                String newId = UUID.randomUUID().toString();
                return new Wizard(newId, nameField.getText(), houseCombo.getValue(), wandField.getText(), imageObj, imageBytes);
            }
            return null;
        });


        Optional<Wizard> result = dialog.showAndWait();
        result.ifPresent(wizard -> {
            logger.info("Añadiendo nuevo mago: {}", wizard.getName());
            wizardList.add(0, wizard);
            saveChangesToFiles();
            updatePagination();
        });
    }


    private void deleteWizard(Wizard w) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Expediente Disciplinario"); // Título más temático
        alert.setHeaderText("¿Estás seguro de expulsar a " + w.getName() + "?");
        alert.setContentText("Esta acción es irreversible y se perderán todos los datos del alumno.");

        // 1. AÑADIR ICONO DE LA VENTANA (Tu petición anterior)
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png")));
        } catch (Exception e) { /* Ignorar si no carga */ }

        // 2. AÑADIR LA FOTO DEL MAGO DENTRO DE LA ALERTA
        if (w.getImage() != null) {
            ImageView imageView = new ImageView(w.getImage());
            imageView.setFitHeight(60);
            imageView.setFitWidth(60);

            // Hacemos la foto redonda para que quede más moderno
            javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(30, 30, 30);
            imageView.setClip(clip);

            alert.setGraphic(imageView);
        }

        // 3. BOTONES PERSONALIZADOS
        ButtonType btnEliminar = new ButtonType("Expulsar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnEliminar, btnCancelar);

        // 4. ESTILIZAR EL BOTÓN DE ELIMINAR (ROJO)
        Node deleteButton = alert.getDialogPane().lookupButton(btnEliminar);
        deleteButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");

        // LÓGICA DE RESPUESTA
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == btnEliminar) {
            // Asumo que tienes logger configurado, si no usa System.out
            // logger.info("Eliminando mago: {} (ID: {})", w.getName(), w.getId());

            wizardList.remove(w);
            saveChangesToFiles();

            // Refrescar UI
            rootPane.getChildren().clear();
            rootPane.getChildren().add(pagination);
            updatePagination();
        }
    }


    private void showDetails(Wizard w) {
        logger.debug("Mostrando detalles de: {}", w.getName());
        VBox details = new VBox(20); details.setAlignment(Pos.CENTER);
        ImageView iv = new ImageView();
        if (w.getImage() != null && !w.getImage().isError()) iv.setImage(w.getImage()); else iv.setImage(DEFAULT_IMAGE);
        iv.setFitHeight(250); iv.setPreserveRatio(true); iv.getStyleClass().add("detail-image");
        Label title = new Label(w.getName()); title.getStyleClass().add("detail-title");


        HBox buttonsBox = new HBox(20); buttonsBox.setAlignment(Pos.CENTER);
        Button backBtn = new Button(getText("btn_back")); backBtn.getStyleClass().add("button-back");
        backBtn.setOnAction(e -> { rootPane.getChildren().clear(); rootPane.getChildren().add(pagination); });


        Button pdfProfileBtn = new Button("PDF");
        pdfProfileBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold;");
        pdfProfileBtn.setOnAction(e -> {
            logger.info("Generando PDF individual para {}", w.getName());
            reportService.printWizardProfile(w, rootPane.getScene().getWindow());
        });


        Button deleteBtn = new Button("Eliminar"); deleteBtn.getStyleClass().add("button-delete");
        deleteBtn.setOnAction(e -> deleteWizard(w));


        buttonsBox.getChildren().addAll(backBtn, pdfProfileBtn, deleteBtn);
        details.getChildren().addAll(iv, title, new Label(getText("label_house") + w.getHouse()), new Label(getText("label_wand") + w.getWand()), buttonsBox);
        rootPane.getChildren().clear(); rootPane.getChildren().add(details);
    }


    // --- MÉTODOS DE SOPORTE (UI e IDIOMAS) ---
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
            switch(key){
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
            switch(key){
                case "app_title": return "ANUARIO MÁGICO";
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


    private void setupPagination() {
        pagination = new Pagination(1, 0);
        updatePagination();
        rootPane.getChildren().add(pagination);
    }


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
        if(h.contains("gryffindor")) c.getStyleClass().add("card-gryffindor");
        else if(h.contains("slytherin")) c.getStyleClass().add("card-slytherin");
        else if(h.contains("ravenclaw")) c.getStyleClass().add("card-ravenclaw");
        else if(h.contains("hufflepuff")) c.getStyleClass().add("card-hufflepuff");
        else c.getStyleClass().add("card-default");


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
}
