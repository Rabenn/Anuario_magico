# 🏰 Anuario Mágico - Sistema de Gestión de Hogwarts

> **Versión:** 1.0  
> **Asignatura:** Acceso a Datos / Interfaces de Usuario 2025

![Java](https://img.shields.io/badge/Java-17%2B-orange)
![JavaFX](https://img.shields.io/badge/Framework-JavaFX-blue)
![Persistencia](https://img.shields.io/badge/Persistencia-Pol%C3%ADglota-green)

## 📖 Descripción

El **Anuario Mágico** es una aplicación de escritorio diseñada para gestionar el censo de estudiantes de Hogwarts, sus varitas mágicas y sus registros visuales.

La característica más destacada de este proyecto es su **Arquitectura de Persistencia Heterogénea**, demostrando la capacidad de sincronizar operaciones CRUD (Crear, Leer, Actualizar, Eliminar) a través de múltiples formatos de archivo simultáneamente sin utilizar una base de datos tradicional.

## 🚀 Características Principales

* **Interfaz Material Design:** UI moderna construida con JavaFX, con tarjetas dinámicas que reaccionan a la casa del alumno (Gryffindor, Slytherin, Ravenclaw, Hufflepuff).
* **Gestión de Datos:** Altas, bajas y modificaciones sincronizadas.
* **Buscador Inteligente:** Filtrado en tiempo real por nombre, casa o material de varita.
* **Motor de Reportes:** Generación de PDFs oficiales (Listados y Fichas de Alumno) utilizando **JasperReports**.
* **Soporte Multimedia:** Carga y visualización de imágenes de alumnos.

## 💾 Arquitectura de Datos

El sistema fragmenta la información de un objeto "Mago" en tres fuentes de datos distintas para optimizar el almacenamiento:

| Archivo | Formato | Contenido |
| :--- | :--- | :--- |
| `nombres.json` | **JSON** | Identificadores (UUID), nombres y casas. Utiliza Jackson Databind. |
| `varitas.xml` | **XML** | Detalles técnicos de las varitas mágicas. |
| `imagenes.csv` | **CSV** | Fotografías convertidas a cadenas Base64. |

## 🛠️ Requisitos e Instalación

### Prerrequisitos
* **Java JDK:** Versión 17 o superior.
* **Dependencias:**
    * JavaFX (Módulos: Graphics, Controls, FXML).
    * Jackson Databind.
    * JasperReports.

### Instalación y Ejecución

1.  **Clonar/Descargar:** Descarga el proyecto en tu equipo local.
2.  **Archivos Base:** Asegúrate de que los archivos de datos (`nombres.json`, `varitas.xml`, `imagenes.csv`) se encuentren en el mismo directorio que el ejecutable `.jar` o en la raíz del proyecto si lo ejecutas desde un IDE.
3.  **Ejecutar:**
    ```bash
    java -jar AnuarioMagico.jar
    ```

## 📚 Documentación

Para ver el manual de usuario completo, incluyendo el **videotutorial** de uso, abre el archivo local:

> `manual_usuario.html`

## 👥 Autores

Proyecto desarrollado por el equipo de desarrollo de Hogwarts:

* **Unai**
* **Igor**
* **Ruben**

---
© 2025 Anuario Mágico - Proyecto Educativo.