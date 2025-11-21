module es.ruben {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens es.ruben to javafx.fxml;
    exports es.ruben;
}