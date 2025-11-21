package es.ruben;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.InputStream;
import java.sql.*;


/**
 * JavaFX App
 */

public class App extends Application {

    private ObservableList<Wizard> wizardList = FXCollections.observableArrayList();
    private FilteredList<Wizard> filteredData;
    private Pagination pagination;
    private StackPane rootPane;

    private final int ITEMS_PER_PAGE = 8;

    @Override
    public void start(Stage stage) {
        loadFromDatabase(); // Cargar datos al inicio

        filteredData = new FilteredList<>(wizardList, p -> true);

        BorderPane layout = new BorderPane();
        layout.setTop(createHeader());

        rootPane = new StackPane();
        setupPagination(); // Configurar paginación inicial
        rootPane.getChildren().add(pagination);

        layout.setCenter(rootPane);

        Scene scene = new Scene(layout, 1000, 700);
        stage.setTitle("Hogwarts Yearbook");
        stage.show();
    }

    // --- CONEXIÓN SQLITE (Lectura de BLOB) ---
    private void loadFromDatabase() {
        // La URL apunta al archivo en la raíz del proyecto
        String url = "jdbc:sqlite:hogwarts.db";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, house, wand, image_blob FROM wizards")) {

            while (rs.next()) {
                String name = rs.getString("name");
                String house = rs.getString("house");
                String wand = rs.getString("wand");

                // LEEMOS EL BLOB COMO STREAM BINARIO
                InputStream is = rs.getBinaryStream("image_blob");
                Image img = null;
                if (is != null) {
                    img = new Image(is);
                }

                wizardList.add(new Wizard(name, house, wand, img));
            }
        } catch (Exception e) {
            System.out.println("Error leyendo DB: " + e.getMessage());
        }
    }

    // --- INTERFAZ ---
    private VBox createHeader() {
        TextField searchField = new TextField();
        searchField.setPromptText("Buscar...");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(wizard -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lower = newVal.toLowerCase();
                return wizard.getName().toLowerCase().contains(lower) ||
                        wizard.getHouse().toLowerCase().contains(lower);
            });
            updatePagination();
        });

        VBox header = new VBox(10, new Label("ANUARIO HOGWARTS"), searchField);
        header.setPadding(new Insets(15));
        header.setAlignment(Pos.CENTER);
        return header;
    }

    private void updatePagination() {
        int pageCount = (int) Math.ceil((double) filteredData.size() / ITEMS_PER_PAGE);
        pagination.setPageCount(pageCount > 0 ? pageCount : 1);
        pagination.setPageFactory(this::createPage);
    }

    private void setupPagination() {
        pagination = new Pagination(1, 0);
        updatePagination();
    }

    private Node createPage(int pageIndex) {
        TilePane tilePane = new TilePane(20, 20);
        tilePane.setPadding(new Insets(20));
        tilePane.setPrefColumns(4);

        int start = pageIndex * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filteredData.size());

        for (int i = start; i < end; i++) {
            Wizard w = filteredData.get(i);
            VBox card = new VBox(10);
            card.setAlignment(Pos.CENTER);
            card.setStyle("-fx-border-color: gray; -fx-padding: 10; -fx-background-color: white;");

            ImageView iv = new ImageView(w.getImage());
            iv.setFitHeight(120);
            iv.setPreserveRatio(true);

            Label nameLbl = new Label(w.getName());
            nameLbl.setStyle("-fx-font-weight: bold;");

            card.getChildren().addAll(iv, nameLbl, new Label(w.getHouse()));

            // Click para detalles
            card.setOnMouseClicked(e -> showDetails(w));

            tilePane.getChildren().add(card);
        }
        return new ScrollPane(tilePane);
    }

    private void showDetails(Wizard w) {
        VBox details = new VBox(20);
        details.setAlignment(Pos.CENTER);
        details.getChildren().addAll(
                new ImageView(w.getImage()),
                new Label("Nombre: " + w.getName()),
                new Label("Casa: " + w.getHouse()),
                new Label("Varita: " + w.getWand()),
                new Button("Atrás") {{
                    setOnAction(e -> {
                        rootPane.getChildren().clear();
                        rootPane.getChildren().add(pagination);
                    });
                }}
        );
        rootPane.getChildren().clear();
        rootPane.getChildren().add(details);
    }

    public static void main(String[] args) {
        launch(args);
    }
}