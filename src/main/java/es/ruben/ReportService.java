package es.ruben;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

/**
 * <h2>Servicio de Reportes - ReportService</h2>
 * Esta clase se encarga de la generación de documentos PDF utilizando la librería <b>JasperReports</b>.
 * Actúa como un puente entre los objetos de datos de la aplicación (clase Wizard) y las
 * plantillas de diseño visual (.jrxml).
 * * <p>Funcionalidades principales:</p>
 * <ul>
 * <li>Exportación del anuario completo con todos los magos registrados.</li>
 * <li>Generación de perfiles individuales (fichas de alumno).</li>
 * <li>Conversión de objetos Java a fuentes de datos compatibles con Jasper (Mapas).</li>
 * <li>Gestión de imágenes binarias para su visualización en el reporte.</li>
 * </ul>
 * * @author Unai
 * @author Igor
 * @author Ruben
 * @version 1.0
 */
public class ReportService {

    // Logger para registrar actividad y errores en logs.log
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    /**
     * Genera un archivo PDF con el listado completo de magos.
     * Compila la plantilla del anuario, vincula la lista de datos y solicita
     * al usuario una ubicación para guardar el archivo.
     * * @param wizards Lista de objetos Wizard que aparecerán en el reporte.
     * @param owner Ventana propietaria para centrar el diálogo de guardado.
     */
    public void printYearbook(List<Wizard> wizards, Window owner) {
        logger.info("Solicitud de impresión de ANUARIO COMPLETO iniciada. Magos: {}", wizards.size());
        try {
            File destFile = showSaveDialog(owner, "Hogwarts_Yearbook.pdf");
            if (destFile == null) {
                logger.info("Exportación cancelada por el usuario.");
                return;
            }

            // CORRECCIÓN IMPORTANTE: Ruta absoluta para evitar error en el JAR
            // Se usa "/es.ruben/" porque es la carpeta física que vimos en tu captura
            String reportPath = "/es/ruben/reports/yearbook_list.jrxml";
            InputStream reportStream = getClass().getResourceAsStream(reportPath);

            // Bloque de seguridad: Si no encuentra el archivo, avisa en el log y lanza error
            if (reportStream == null) {
                logger.error("PLANTILLA NO ENCONTRADA en la ruta: {}", reportPath);
                throw new FileNotFoundException("No se encuentra la plantilla .jrxml. Verifica la ruta: " + reportPath);
            }

            List<Map<String, ?>> dataList = convertWizardsToMap(wizards);

            logger.debug("Compilando reporte Jasper...");
            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataList);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, null, dataSource);

            JasperExportManager.exportReportToPdfFile(print, destFile.getAbsolutePath());

            logger.info("PDF generado exitosamente en: {}", destFile.getAbsolutePath());

        } catch (Exception e) {
            // El error se guarda en el log y se muestra al usuario
            logger.error("ERROR CRÍTICO generando el anuario PDF", e);
            showErrorAlert("Error al generar el PDF del anuario", e.getMessage());
        }
    }

    /**
     * Genera un reporte PDF enfocado en un único mago (Perfil Individual).
     * Utiliza una plantilla específica para detalles y convierte al mago en
     * una fuente de datos de un solo registro.
     * * @param wizard El objeto Wizard del cual se quiere generar la ficha.
     * @param owner Ventana propietaria para el diálogo de archivos.
     */
    public void printWizardProfile(Wizard wizard, Window owner) {
        logger.info("Solicitud de impresión de PERFIL INDIVIDUAL para: {}", wizard.getName());
        try {
            File destFile = showSaveDialog(owner, "Wizard_Profile.pdf");
            if (destFile == null) {
                logger.info("Exportación cancelada por el usuario.");
                return;
            }

            // CORRECCIÓN IMPORTANTE: Ruta absoluta
            String reportPath = "/es/ruben/reports/wizard_profile.jrxml";
            InputStream reportStream = getClass().getResourceAsStream(reportPath);

            if (reportStream == null) {
                logger.error("PLANTILLA NO ENCONTRADA en la ruta: {}", reportPath);
                throw new FileNotFoundException("No se encuentra la plantilla .jrxml del perfil. Verifica la ruta: " + reportPath);
            }

            List<Map<String, ?>> dataList = convertWizardsToMap(Collections.singletonList(wizard));

            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataList);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, null, dataSource);

            JasperExportManager.exportReportToPdfFile(print, destFile.getAbsolutePath());

            logger.info("Perfil PDF generado exitosamente para {}", wizard.getName());

        } catch (Exception e) {
            logger.error("ERROR CRÍTICO generando perfil de " + wizard.getName(), e);
            showErrorAlert("Error al generar el perfil", e.getMessage());
        }
    }

    /**
     * Método auxiliar para transformar la lista de objetos de dominio en una
     * estructura compatible con JasperReports.
     * Convierte cada Wizard en un Map donde las claves coinciden con los
     * nombres de los campos definidos en el archivo .jrxml.
     * * @param wizards Lista de magos a procesar.
     * @return Una lista de mapas con los datos y flujos de imagen (InputStream).
     */
    private List<Map<String, ?>> convertWizardsToMap(List<Wizard> wizards) {
        List<Map<String, ?>> dataList = new ArrayList<>();
        for (Wizard w : wizards) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", w.getName());
            row.put("house", w.getHouse());
            row.put("wand", w.getWand());
            if (w.getImageBytes() != null && w.getImageBytes().length > 0) {
                row.put("image_blob", new ByteArrayInputStream(w.getImageBytes()));
            } else {
                row.put("image_blob", null);
            }
            dataList.add(row);
        }
        return dataList;
    }

    /**
     * Muestra un cuadro de diálogo del sistema para que el usuario elija
     * dónde guardar el reporte generado.
     * * @param owner Ventana principal.
     * @param defaultName Nombre sugerido para el archivo.
     * @return El archivo seleccionado o null si el usuario cancela.
     */
    private File showSaveDialog(Window owner, String defaultName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar PDF");
        fileChooser.setInitialFileName(defaultName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        return fileChooser.showSaveDialog(owner);
    }

    /**
     * Método privado para mostrar alertas visuales cuando falla el reporte.
     * Esto evita que el programa se quede "mudo" si hay un error.
     */
    private void showErrorAlert(String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}