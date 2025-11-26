module es.ruben {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jasperreports;
    requires com.fasterxml.jackson.annotation;

    opens es.ruben to javafx.fxml;
    exports es.ruben;
}