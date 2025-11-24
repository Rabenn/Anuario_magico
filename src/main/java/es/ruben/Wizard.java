package es.ruben;
import javafx.scene.image.Image;

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

    public String getName() { return name; }
    public String getHouse() { return house; }
    public String getWand() { return wand; }
    public Image getImage() { return image; }
}