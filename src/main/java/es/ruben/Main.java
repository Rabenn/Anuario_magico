package es.ruben;

/**
 * <h2>Lanzador de la Aplicación - Main</h2>
 * Esta clase sirve como el punto de entrada principal (Entry Point) fuera del
 * ciclo de vida directo de JavaFX.
 * <p>
 * Su existencia es necesaria para facilitar el empaquetado de la aplicación en
 * archivos ejecutables (JAR), evitando errores comunes de configuración del
 * Toolkit de JavaFX al iniciar la máquina virtual.
 * </p>
 * * @author Unai
 * @author Igor
 * @author Ruben
 * @version 1.0
 */
public class Main {

    /**
     * Método main estándar que actúa como puente.
     * Simplemente redirige la ejecución al método main de la clase {@link App},
     * la cual inicializa el entorno gráfico.
     * * @param args Argumentos de la línea de comandos pasados al inicio.
     */
    public static void main(String[] args) {
        App.main(args);
    }
}