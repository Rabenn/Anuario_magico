package es.ruben;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;

public class App extends Application {
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
    public static void main(String[] args) { launch(); }
}