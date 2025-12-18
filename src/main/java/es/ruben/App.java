package es.ruben;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;

/**
 * <h2>Clase Principal - Anuario Mágico</h2>
 * Esta clase representa el punto de entrada principal de la aplicación JavaFX.
 * Se encarga de configurar la ventana principal (Stage), cargar la interfaz
 * desde un archivo FXML y establecer los recursos visuales como el icono.
 * * @author Unai
 * @author Igor
 * @author Ruben
 * @version 1.0
 */
public class App extends Application {

    /**
     * Método de inicio de la aplicación JavaFX.
     * Se ejecuta automáticamente tras llamar al método launch().
     * * <p>Pasos que realiza:</p>
     * <ul>
     * <li>Localiza el archivo FXML con el diseño de la vista.</li>
     * <li>Crea una nueva escena con dimensiones predefinidas (1000x700).</li>
     * <li>Configura el título y el icono de la ventana.</li>
     * <li>Muestra la ventana al usuario.</li>
     * </ul>
     * * @param stage El escenario principal (ventana) proporcionado por la plataforma JavaFX.
     * @throws IOException Si ocurre un error al intentar cargar el archivo FXML o la imagen.
     */
    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlUrl = App.class.getResource("xml/main-view.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

        stage.setTitle("ANUARIO MÁGICO");
        stage.getIcons().add(new Image(App.class.getResourceAsStream("images/icono.png")));

        stage.setScene(scene);
        stage.show();
    }

    /**
     * Método principal de Java (punto de entrada del sistema).
     * Llama al método launch() heredado de la clase Application para
     * iniciar el ciclo de vida de JavaFX.
     * * @param args Argumentos de la línea de comandos (no utilizados).
     */
    public static void main(String[] args) {
        launch();
    }
}