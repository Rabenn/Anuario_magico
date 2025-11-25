module es.ruben {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jasperreports;

    opens es.ruben to javafx.fxml;
    exports es.ruben;
}