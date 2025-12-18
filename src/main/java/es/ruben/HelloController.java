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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration; // Importante para el tiempo del tooltip

/**
 * <h2>Controlador de la Vista Principal - HelloController</h2>
 * Esta clase actúa como el cerebro de la aplicación, gestionando la interacción
 * entre el usuario y los datos del Anuario Mágico.
 * * <p>Sus funciones principales incluyen:</p>
 * <ul>
 * <li><b>Persistencia Políglota:</b> Carga y guarda datos combinando JSON, XML y CSV.</li>
 * <li><b>Gestión de UI:</b> Controla la paginación, filtros de búsqueda y cambio de idioma (ES/EN).</li>
 * <li><b>CRUD:</b> Permite añadir y eliminar registros de magos con persistencia inmediata.</li>
 * <li><b>Reportes:</b> Conecta con el servicio de impresión de PDFs.</li>
 * </ul>
 * @author Unai
 * @author Igor
 * @author Ruben
 * @version 1.0
 */

public class HelloController {

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
    private Wizard currentWizard = null;

    /**
     * Inicializa el controlador al cargar la vista FXML.
     * Configura los listeners de los componentes, carga los archivos de datos
     * y prepara la paginación de la interfaz.
     */
    @FXML
    public void initialize() {
        loadFromHeterogeneousFiles();

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
        addBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);
        pdfBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);

        pdfBtn.setOnAction(e -> {
            reportService.printYearbook(new ArrayList<>(wizardList), rootPane.getScene().getWindow());
        });

        if (wizardList.isEmpty()) {
            Label emptyLabel = new Label("⚠️ No hay datos. Ejecuta el script Python (ETL) primero.");
            rootPane.getChildren().add(emptyLabel);
        } else {
            setupPagination();
            updateInterfaceLanguage();
        }
    }

    // --- NUEVO MÉTODO HELPER PARA TOOLTIPS ---
    private Tooltip createTooltip(String text) {
        Tooltip t = new Tooltip(text);
        // Estilo CSS directo: Letra pequeña (11px) y padding reducido
        t.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px; -fx-background-color: rgba(30,30,30,0.9); -fx-text-fill: white;");
        // Hacemos que aparezca más rápido (200ms en vez de 1 segundo)
        t.setShowDelay(Duration.millis(200));
        return t;
    }
    // -----------------------------------------

    // --- CARGA DE DATOS ---

    /**
     * Sistema de carga de archivos heterogéneos.
     * Combina datos de tres fuentes distintas para reconstruir los objetos Wizard:
     * <ul>
     * <li><b>JSON:</b> Atributos básicos (nombre, id, casa).</li>
     * <li><b>XML:</b> Información sobre las varitas.</li>
     * <li><b>CSV:</b> Imágenes codificadas en Base64.</li>
     * </ul>
     */
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
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
            }
            wizardList.clear();
            List<Wizard> sortedList = new ArrayList<>(tempMap.values());
            sortedList.sort(Comparator.comparing(Wizard::getName));
            wizardList.addAll(sortedList);
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Guarda los cambios actuales de la lista de magos en los tres archivos locales.
     * Sincroniza la información para mantener la integridad entre JSON, XML y CSV.
     */
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- AÑADIR ---
    /**
     * Despliega un diálogo interactivo para añadir un nuevo mago al sistema.
     * Incluye validación de campos y selector de archivos para la imagen.
     */
    private void showAddWizardDialog() {
        Dialog<Wizard> dialog = new Dialog<>();
        dialog.setTitle(currentLang.equals("ES") ? "Añadir Nuevo Mago" : "Add New Wizard");
        dialog.setHeaderText(null);
        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("css/styles.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dialog-pane");
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png")));
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
        // CAMBIO: Usamos createTooltip
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
            wizardList.add(0, wizard);
            saveChangesToFiles();
            updatePagination();
        });
    }

    // --- BORRAR ---
    /**
     * Gestiona el proceso de eliminación de un mago.
     * Muestra una alerta de confirmación personalizada con la imagen del alumno
     * y actualiza tanto la memoria como los archivos locales.
     * * @param w El objeto Wizard que se desea eliminar.
     */
    private void deleteWizard(Wizard w) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(currentLang.equals("ES") ? "Expediente Disciplinario" : "Expulsion Record");
        String msgHeader = currentLang.equals("ES") ? "¿Expulsar a " + w.getName() + "?" : "Expel " + w.getName() + "?";
        String msgContent = currentLang.equals("ES") ? "Esta acción es irreversible." : "This action cannot be undone.";
        alert.setHeaderText(msgHeader);
        alert.setContentText(msgContent);

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        try { stage.getIcons().add(new Image(getClass().getResourceAsStream("images/icono.png"))); } catch (Exception e) {}

        if (w.getImage() != null) {
            ImageView imageView = new ImageView(w.getImage());
            imageView.setFitHeight(60); imageView.setFitWidth(60);
            Circle clip = new Circle(30, 30, 30);
            imageView.setClip(clip);
            alert.setGraphic(imageView);
        }

        ButtonType btnEliminar = new ButtonType(currentLang.equals("ES") ? "Expulsar" : "Expel", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType(currentLang.equals("ES") ? "Cancelar" : "Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnEliminar, btnCancelar);

        Node deleteButton = alert.getDialogPane().lookupButton(btnEliminar);
        deleteButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == btnEliminar) {
            wizardList.remove(w);
            saveChangesToFiles();
            currentWizard = null;
            rootPane.getChildren().clear();
            rootPane.getChildren().add(pagination);
            updatePagination();
        }
    }

    // --- DETALLES ---
    /**
     * Cambia la vista principal por una vista detallada del mago seleccionado.
     * @param w El mago cuyos detalles se van a mostrar.
     */
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
        // CAMBIO: Tooltip pequeño
        backBtn.setTooltip(createTooltip(getText("tooltip_back")));

        backBtn.setOnAction(e -> {
            currentWizard = null;
            rootPane.getChildren().clear();
            rootPane.getChildren().add(pagination);
        });

        Button pdfProfileBtn = new Button("PDF");
        pdfProfileBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        // CAMBIO: Tooltip pequeño
        pdfProfileBtn.setTooltip(createTooltip(getText("tooltip_pdf_profile")));

        pdfProfileBtn.setOnAction(e -> reportService.printWizardProfile(w, rootPane.getScene().getWindow()));

        Button deleteBtn = new Button(getText("btn_delete"));
        deleteBtn.getStyleClass().add("button-delete");
        // CAMBIO: Tooltip pequeño
        deleteBtn.setTooltip(createTooltip(getText("tooltip_delete")));

        deleteBtn.setOnAction(e -> deleteWizard(w));

        buttonsBox.getChildren().addAll(backBtn, pdfProfileBtn, deleteBtn);
        details.getChildren().addAll(iv, title, new Label(getText("label_house") + w.getHouse()), new Label(getText("label_wand") + w.getWand()), buttonsBox);

        rootPane.getChildren().clear();
        rootPane.getChildren().add(details);
    }

    // --- INTERFAZ E IDIOMAS ---
    /**
     * Actualiza todos los textos de la interfaz según el idioma seleccionado.
     */
    private void updateInterfaceLanguage() {
        String titleText = getText("app_title");
        titleLabel.setText(titleText);
        searchField.setPromptText(getText("search_placeholder"));

        addBtn.setText(getText("btn_add"));
        addBtn.setGraphic(null);
        pdfBtn.setText(getText("btn_pdf"));
        pdfBtn.setGraphic(null);

        // --- CAMBIO: Usamos createTooltip aquí también ---
        addBtn.setTooltip(createTooltip(getText("tooltip_add")));
        pdfBtn.setTooltip(createTooltip(getText("tooltip_pdf_main")));
        searchField.setTooltip(createTooltip(getText("tooltip_search")));
        filterTypeCombo.setTooltip(createTooltip(getText("tooltip_filter")));
        langCombo.setTooltip(createTooltip(getText("tooltip_lang")));
        // -------------------------------------------------

        if (rootPane.getScene() != null && rootPane.getScene().getWindow() != null) {
            ((Stage) rootPane.getScene().getWindow()).setTitle(titleText);
        }
        int idx = filterTypeCombo.getSelectionModel().getSelectedIndex();
        updateFilterCombo();
        if (idx >= 0) filterTypeCombo.getSelectionModel().select(idx);

        if (currentWizard != null) {
            showDetails(currentWizard);
        }
    }

    private void updateFilterCombo() { filterTypeCombo.setItems(FXCollections.observableArrayList(getText("filter_name"), getText("filter_house"), getText("filter_wand"))); }

    /**
     * Diccionario interno para la internacionalización (I18N).
     * @param key Clave del texto solicitado.
     * @return Texto traducido en el idioma actual.
     */
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
                case "btn_add": return "Add";
                case "btn_pdf": return "PDF";
                case "tooltip_add": return "Add a new wizard to the database";
                case "tooltip_pdf_main": return "Generate full yearbook PDF";
                case "tooltip_search": return "Type to filter by text";
                case "tooltip_filter": return "Select filter criteria";
                case "tooltip_lang": return "Change application language";
                case "tooltip_back": return "Return to wizard list";
                case "tooltip_pdf_profile": return "Generate profile PDF for this wizard";
                case "tooltip_delete": return "Permanently expel this wizard";
                case "tooltip_photo": return "Select an image file (JPG/PNG)";
                case "tooltip_card": return "Click to view details";
                default: return key;
            }
        } else {
            switch(key){
                case "app_title": return "ANUARIO HOGWARTS";
                case "search_placeholder": return "Buscar alumno...";
                case "filter_name": return "Nombre";
                case "filter_house": return "Casa";
                case "filter_wand": return "Varita";
                case "label_house": return "Casa: ";
                case "label_wand": return "Varita: ";
                case "btn_back": return "Volver al listado";
                case "btn_delete": return "Expulsar";
                case "btn_add": return "Añadir";
                case "btn_pdf": return "PDF";
                case "tooltip_add": return "Añadir nuevo mago a la base de datos";
                case "tooltip_pdf_main": return "Generar anuario completo en PDF";
                case "tooltip_search": return "Escribe para filtrar por texto";
                case "tooltip_filter": return "Seleccionar criterio de filtro";
                case "tooltip_lang": return "Cambiar idioma de la aplicación";
                case "tooltip_back": return "Volver al listado de alumnos";
                case "tooltip_pdf_profile": return "Generar perfil PDF de este mago";
                case "tooltip_delete": return "Expulsar permanentemente a este alumno";
                case "tooltip_photo": return "Seleccionar archivo de imagen (JPG/PNG)";
                case "tooltip_card": return "Haz click para ver detalles";
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

        // CAMBIO: Usamos createTooltip para el tooltip de la carta
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
}