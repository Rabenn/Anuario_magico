module es.ruben {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jasperreports;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires java.desktop;
    requires javafx.web;

    opens es.ruben to javafx.fxml;
    exports es.ruben;
}