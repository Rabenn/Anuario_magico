package es.ruben;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;

public class ReportService {

    public void printYearbook(List<Wizard> wizards, Window owner) {
        try {
            File destFile = showSaveDialog(owner, "Hogwarts_Yearbook.pdf");
            if (destFile == null) return;

            InputStream reportStream = getClass().getResourceAsStream("reports/yearbook_list.jrxml");

            // CONVERTIR LISTA A MAPAS
            List<Map<String, ?>> dataList = convertWizardsToMap(wizards);

            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataList);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, null, dataSource);
            JasperExportManager.exportReportToPdfFile(print, destFile.getAbsolutePath());
            System.out.println("✅ Anuario generado");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void printWizardProfile(Wizard wizard, Window owner) {
        try {
            File destFile = showSaveDialog(owner, "Wizard_Profile.pdf");
            if (destFile == null) return;

            InputStream reportStream = getClass().getResourceAsStream("reports/wizard_profile.jrxml");

            // LISTA DE UN SOLO ELEMENTO
            List<Map<String, ?>> dataList = convertWizardsToMap(Collections.singletonList(wizard));

            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataList);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, null, dataSource);
            JasperExportManager.exportReportToPdfFile(print, destFile.getAbsolutePath());
            System.out.println("✅ Perfil generado");
        } catch (Exception e) { e.printStackTrace(); }
    }

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

    private File showSaveDialog(Window owner, String defaultName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar PDF");
        fileChooser.setInitialFileName(defaultName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        return fileChooser.showSaveDialog(owner);
    }
}