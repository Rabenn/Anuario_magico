package es.ruben;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javafx.scene.image.Image;
import java.io.ByteArrayInputStream;
import java.util.Base64;

@JsonPropertyOrder({ "id", "name", "house", "wand", "imageBase64" })
public class Wizard {
    private String id;
    private String name;
    private String house;
    private String wand;

    @JsonIgnore
    private Image image; // Imagen visual (pesada en RAM)

    @JsonIgnore
    private byte[] imageBytes; // Datos binarios (ligeros)

    // Constructor
    public Wizard(String id, String name, String house, String wand, Image image, byte[] imageBytes) {
        this.id = id;
        this.name = name;
        this.house = house;
        this.wand = wand;
        this.image = image; // Puede ser null al inicio
        this.imageBytes = imageBytes;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getHouse() { return house; }
    public String getWand() { return wand; }
    public byte[] getImageBytes() { return imageBytes; }

    // --- LAZY LOADING (CARGA PEREZOSA) ---
    // La magia ocurre aquí: Si la imagen es null, la crea al vuelo desde los bytes.
    public Image getImage() {
        if (this.image == null && this.imageBytes != null && this.imageBytes.length > 0) {
            try {
                // Truco extra: cargamos la imagen con un tamaño ajustado (250px) para ahorrar más memoria
                this.image = new Image(new ByteArrayInputStream(this.imageBytes), 250, 0, true, true);
            } catch (Exception e) {
                System.out.println("Error generando imagen para: " + name);
            }
        }
        return this.image;
    }

    // Para el script de exportación (aunque uses Python, esto mantiene compatibilidad)
    @JsonProperty("imageBase64")
    public String getBase64Image() {
        if (imageBytes != null && imageBytes.length > 0) {
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        return "";
    }
}