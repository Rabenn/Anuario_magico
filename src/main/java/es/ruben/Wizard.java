package es.ruben;
import javafx.scene.image.Image;

public class Wizard {
    private String id;
    private String name;
    private String house;
    private String wand;
    private Image image;

    public Wizard(String id, String name, String house, String wand, Image image) {
        this.id = id;
        this.name = name;
        this.house = house;
        this.wand = wand;
        this.image = image;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getHouse() { return house; }
    public String getWand() { return wand; }
    public Image getImage() { return image; }
}