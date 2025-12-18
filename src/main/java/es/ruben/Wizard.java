package es.ruben;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.scene.image.Image;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * <h2>Modelo de Datos - Wizard</h2>
 * Esta clase representa la entidad "Mago" dentro de la aplicación.
 * Actúa como un objeto de transferencia de datos (DTO) que almacena la
 * información personal, académica y visual de cada alumno de Hogwarts.
 * * <p>Características especiales:</p>
 * <ul>
 * <li><b>Persistencia Selectiva:</b> Utiliza {@code @JsonIgnore} para evitar que
 * los datos binarios pesados se dupliquen en el archivo JSON.</li>
 * <li><b>Lazy Loading:</b> El objeto {@link Image} de JavaFX solo se construye
 * cuando se solicita por primera vez, optimizando el uso de memoria.</li>
 * <li><b>Codificación Base64:</b> Incluye lógica para convertir bytes de imagen
 * a texto, facilitando el almacenamiento en formato CSV.</li>
 * </ul>
 * * @author Unai
 * @author Igor
 * @author Ruben
 * @version 1.0
 */
public class Wizard {
    private String id;
    private String name;
    private String house;
    private String wand;

    @JsonIgnore
    private Image image;

    @JsonIgnore
    private byte[] imageBytes;

    /**
     * Constructor vacío requerido para la deserialización de frameworks
     * como Jackson (JSON).
     */
    public Wizard() {}

    /**
     * Constructor parametrizado para crear instancias completas.
     * * @param id Identificador único (UUID).
     * @param name Nombre del mago.
     * @param house Casa de Hogwarts asignada.
     * @param wand Descripción de su varita mágica.
     * @param image Objeto de imagen para la interfaz JavaFX.
     * @param imageBytes Representación binaria de la imagen para persistencia.
     */
    public Wizard(String id, String name, String house, String wand, Image image, byte[] imageBytes) {
        this.id = id;
        this.name = name;
        this.house = house;
        this.wand = wand;
        this.image = image;
        this.imageBytes = imageBytes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHouse() { return house; }
    public void setHouse(String house) { this.house = house; }

    public String getWand() { return wand; }
    public void setWand(String wand) { this.wand = wand; }

    /**
     * Establece los bytes de la imagen y resetea el objeto Image
     * para forzar su regeneración en la siguiente llamada a {@link #getImage()}.
     * @param b Array de bytes de la imagen.
     */
    public void setImageBytes(byte[] b) {
        this.imageBytes = b;
        this.image = null;
    }

    public byte[] getImageBytes() { return imageBytes; }

    /**
     * Obtiene la imagen procesada para JavaFX.
     * Si la imagen aún no ha sido creada, la genera a partir de los bytes almacenados.
     * * @return Objeto {@link Image} listo para ser mostrado en un {@code ImageView}.
     */
    public Image getImage() {
        if (image == null && imageBytes != null && imageBytes.length > 0) {
            image = new Image(new ByteArrayInputStream(imageBytes), 250, 0, true, true);
        }
        return image;
    }

    /**
     * Convierte los bytes de la imagen a una cadena de texto en formato Base64.
     * Útil para la exportación de datos a archivos de texto como CSV.
     * * @return String con la imagen codificada o cadena vacía si no hay datos.
     */
    public String getBase64Image() {
        if (imageBytes != null && imageBytes.length > 0) {
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        return "";
    }
}