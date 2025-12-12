package es.ruben;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.scene.image.Image;
import java.io.ByteArrayInputStream;
import java.util.Base64;

public class Wizard {
    private String id;
    private String name;
    private String house;
    private String wand;
    @JsonIgnore private Image image;
    @JsonIgnore private byte[] imageBytes;

    public Wizard() {}
    public Wizard(String id, String name, String house, String wand, Image image, byte[] imageBytes) {
        this.id = id; this.name = name; this.house = house; this.wand = wand;
        this.image = image; this.imageBytes = imageBytes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHouse() { return house; }
    public void setHouse(String house) { this.house = house; }
    public String getWand() { return wand; }
    public void setWand(String wand) { this.wand = wand; }

    public void setImageBytes(byte[] b) {
        this.imageBytes = b;
        this.image = null;
    }

    public byte[] getImageBytes() { return imageBytes; }

    public Image getImage() {
        if (image == null && imageBytes != null && imageBytes.length > 0) {
            image = new Image(new ByteArrayInputStream(imageBytes), 250, 0, true, true);
        }
        return image;
    }

    public String getBase64Image() {
        if (imageBytes != null && imageBytes.length > 0) {
            // Convierte los bytes a String Base64 para el CSV
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        return "";
    }
}