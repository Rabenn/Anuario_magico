package es.ruben;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    // --- IMPRIMIR ANUARIO (Lista completa) ---
    public void printYearbook(Connection conn, Window owner) {
        try {
            // 1. Elegir archivo
            File destFile = showSaveDialog(owner, "Hogwarts_Yearbook.pdf");
            if (destFile == null) return;

            // 2. Cargar diseño
            InputStream reportStream = getClass().getResourceAsStream("reports/yearbook_list.jrxml");
            if (reportStream == null) {
                System.out.println("❌ Error: No encuentro reports/yearbook_list.jrxml");
                return;
            }

            // 3. CONSULTA MANUAL EN JAVA (Para evitar el error de SQLite)
            List<Map<String, ?>> dataList = new ArrayList<>();
            String sql = "SELECT name, house, wand, image_blob FROM wizards ORDER BY house, name";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("house", rs.getString("house"));
                    row.put("wand", rs.getString("wand"));

                    // Leemos los bytes manualmente y creamos el InputStream
                    byte[] imgBytes = rs.getBytes("image_blob");
                    if (imgBytes != null && imgBytes.length > 0) {
                        row.put("image_blob", new ByteArrayInputStream(imgBytes));
                    } else {
                        row.put("image_blob", null);
                    }
                    dataList.add(row);
                }
            }

            // 4. Compilar y Llenar
            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

            // Usamos nuestra propia fuente de datos, NO la conexión SQL directa
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataList);

            JasperPrint print = JasperFillManager.fillReport(jasperReport, null, dataSource);

            // 5. Exportar
            JasperExportManager.exportReportToPdfFile(print, destFile.getAbsolutePath());
            System.out.println("✅ Anuario generado: " + destFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- IMPRIMIR PERFIL INDIVIDUAL ---
    public void printWizardProfile(String wizardId, Connection conn, Window owner) {
        try {
            File destFile = showSaveDialog(owner, "Wizard_Profile.pdf");
            if (destFile == null) return;

            InputStream reportStream = getClass().getResourceAsStream("reports/wizard_profile.jrxml");

            // Consulta manual para un solo mago
            List<Map<String, ?>> dataList = new ArrayList<>();
            String sql = "SELECT name, house, wand, image_blob FROM wizards WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, wizardId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("house", rs.getString("house"));
                        row.put("wand", rs.getString("wand"));

                        byte[] imgBytes = rs.getBytes("image_blob");
                        if (imgBytes != null && imgBytes.length > 0) {
                            row.put("image_blob", new ByteArrayInputStream(imgBytes));
                        } else {
                            row.put("image_blob", null);
                        }
                        dataList.add(row);
                    }
                }
            }

            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataList);

            // Ya no necesitamos pasar parámetros porque filtramos en Java
            JasperPrint print = JasperFillManager.fillReport(jasperReport, null, dataSource);

            JasperExportManager.exportReportToPdfFile(print, destFile.getAbsolutePath());
            System.out.println("✅ Perfil generado.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File showSaveDialog(Window owner, String defaultName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar PDF");
        fileChooser.setInitialFileName(defaultName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        return fileChooser.showSaveDialog(owner);
    }
}