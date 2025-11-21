package es.ruben;
import javafx.scene.image.Image;

/**
 * Clase de java que actua como modelo de alumno
 */

public class Wizard {
    private String name;
    private String house;
    private String wand;
    private Image image;

    public Wizard(String name, String house, String wand, Image image) {
        this.name = name;
        this.house = house;
        this.wand = wand;
        this.image = image;
    }

    // Getters
    public String getName() { return name; }
    public String getHouse() { return house; }
    public String getWand() { return wand; }
    public Image getImage() { return image; }
}