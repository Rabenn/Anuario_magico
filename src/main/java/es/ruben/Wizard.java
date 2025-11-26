package es.ruben;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javafx.scene.image.Image;
import java.util.Base64;

// Ordenamos las columnas para que el CSV salga bonito
@JsonPropertyOrder({ "id", "name", "house", "wand", "imageBase64" })
public class Wizard {
    private String id;
    private String name;
    private String house;
    private String wand;

    @JsonIgnore // JavaFX Image no se puede exportar a texto
    private Image image;

    @JsonIgnore // Los bytes crudos tampoco, usaremos el getter Base64
    private byte[] imageBytes;

    public Wizard(String id, String name, String house, String wand, Image image, byte[] imageBytes) {
        this.id = id;
        this.name = name;
        this.house = house;
        this.wand = wand;
        this.image = image;
        this.imageBytes = imageBytes;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getHouse() { return house; }
    public String getWand() { return wand; }
    public Image getImage() { return image; }
    public byte[] getImageBytes() { return imageBytes; }

    // --- ESTO ES LO QUE USARÁ POSTMAN / XML / CSV ---
    @JsonProperty("imageBase64")
    public String getBase64Image() {
        if (imageBytes != null && imageBytes.length > 0) {
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        return "";
    }
}