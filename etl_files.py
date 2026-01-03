import requests
import json
import csv
import base64
import io
import os
import sys
from PIL import Image
from concurrent.futures import ThreadPoolExecutor, as_completed

# --- CONFIGURACIÓN ---
API_URL = "https://api.potterdb.com/v1/characters"
FILE_JSON = "nombres.json"
FILE_XML = "varitas.xml"
FILE_CSV = "imagenes.csv"
MAX_WORKERS = 10  # Número de descargas simultáneas

# --- FIX PARA EL ERROR DE TAMAÑO CSV ---
# Aumenta el límite del tamaño de campo para manejar strings Base64 muy largos
csv.field_size_limit(sys.maxsize)

def process_image(char_data, session):
    """
    Descarga, redimensiona y codifica una imagen en Base64.
    Está optimizada para ejecutarse dentro de un hilo (thread).

    Args:
        char_data (dict): Diccionario con datos del personaje. Debe contener 'id' e 'img' (URL).
        session (requests.Session): Sesión HTTP reutilizable para mejorar el rendimiento de conexión.

    Returns:
        tuple: Una tupla (id, base64_string) si el proceso es exitoso.
        None: Si no hay URL, falla la descarga o hay un error de procesamiento.
    """
    url = char_data['img']
    c_id = char_data['id']

    if not url:
        return None

    try:
        # Realizamos la petición GET usando la sesión pasada como argumento
        response = session.get(url, timeout=10)
        if response.status_code == 200:
            # Procesamiento de imagen en memoria sin guardar en disco intermedio
            img_io = io.BytesIO(response.content)
            pil_img = Image.open(img_io)

            # Convertimos a RGB si es necesario (para evitar errores con PNGs transparentes)
            if pil_img.mode in ("RGBA", "P"):
                pil_img = pil_img.convert("RGB")

            # Redimensionamos manteniendo la relación de aspecto (thumbnail)
            pil_img.thumbnail((300, 300))

            # Guardamos en un buffer de memoria como JPEG
            out_io = io.BytesIO()
            pil_img.save(out_io, format="JPEG", quality=80)

            # Codificamos a Base64
            b64 = base64.b64encode(out_io.getvalue()).decode('utf-8')
            return (c_id, b64)
    except Exception as e:
        # Se captura cualquier error de red o de imagen para no detener el hilo
        pass
    return None

def load_existing_ids():
    """
    Lee el archivo CSV existente para identificar qué imágenes ya han sido descargadas.
    Esto permite reanudar el script sin duplicar trabajo.

    Returns:
        set: Un conjunto (set) con los IDs (strings) que ya existen en el CSV.
    """
    ids = set()
    if os.path.exists(FILE_CSV):
        try:
            with open(FILE_CSV, "r", encoding="utf-8") as f:
                reader = csv.reader(f)
                next(reader, None)  # Saltar encabezado
                for r in reader:
                    if r: ids.add(r[0])
        except:
            pass
    return ids

def run_etl():
    """
    Función principal que orquesta todo el flujo ETL (Extract, Transform, Load).

    Fases:
    1. Extracción (API): Itera por todas las páginas de la API de PotterDB.
    2. Guardado Metadatos: Genera archivos JSON (nombres/casas) y XML (varitas).
    3. Procesamiento Imágenes: Descarga imágenes faltantes usando concurrencia (ThreadPoolExecutor)
       y las guarda en un CSV codificadas en Base64.
    """
    # --- FASE 1: OBTENCIÓN DE DATOS (API) ---
    print(" FASE 1: Metadatos API...")
    chars = []
    page = 1

    # Usamos una sesión para reutilizar la conexión con la API de Potter
    with requests.Session() as api_session:
        while True:
            try:
                print(f" -> Página {page}...", end="\r")
                r = api_session.get(f"{API_URL}?page[number]={page}&page[size]=100")
                if r.status_code != 200: break
                d = r.json()
                if not d.get('data'): break

                for i in d['data']:
                    a = i['attributes']
                    if a.get('image'): # Solo nos interesan si tienen imagen
                        chars.append({
                            "id": i['id'],
                            "name": a.get('name'),
                            "house": a.get('house') or "Sin Casa",
                            "wand": a.get('wands')[0] if a.get('wands') else "Desconocida",
                            "img": a.get('image')
                        })

                if not d.get('links') or not d['links'].get('next'): break
                page += 1
            except: break

    print(f"\n {len(chars)} magos encontrados. Guardando JSON/XML...")

    # Guardado rápido de JSON
    with open(FILE_JSON, "w", encoding="utf-8") as f:
        json.dump([{"id":c["id"],"name":c["name"],"house":c["house"]} for c in chars], f, indent=2)

    # Guardado rápido de XML
    with open(FILE_XML, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n<WizardsWands>\n')
        # Crear string gigante y escribir una sola vez es más rápido que escribir línea a línea
        xml_content = []
        for c in chars:
            # Escapamos caracteres especiales para XML válido
            w = str(c["wand"]).replace("&","&amp;").replace("<","&lt;")
            xml_content.append(f' <Wizard id="{c["id"]}"><Wand>{w}</Wand></Wizard>')
        f.write("\n".join(xml_content))
        f.write('\n</WizardsWands>')

    # --- FASE 2: DESCARGA DE IMÁGENES (PARALELO) ---
    print("\n FASE 2: Imágenes a CSV (Modo Turbo)...")
    done_ids = load_existing_ids()

    # Filtramos los que ya tenemos para no procesarlos
    chars_to_process = [c for c in chars if c["id"] not in done_ids]
    total = len(chars_to_process)

    if total == 0:
        print("Todas las imágenes ya estaban descargadas.")
        return

    mode = "a" if os.path.exists(FILE_CSV) else "w"

    # Abrimos el CSV
    with open(FILE_CSV, mode, newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if mode == "w": writer.writerow(["id", "base64"])

        # Usamos Session para descarga de imágenes también
        with requests.Session() as img_session:
            # ThreadPoolExecutor maneja los hilos para descargas simultáneas
            with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
                # Lanzamos todas las tareas
                future_to_char = {
                    executor.submit(process_image, char, img_session): char
                    for char in chars_to_process
                }

                completed = 0
                for future in as_completed(future_to_char):
                    result = future.result()
                    completed += 1
                    print(f"[{completed}/{total}] Procesando...", end="\r")

                    if result:
                        writer.writerow(result)
                        # No hacemos f.flush() cada vez, dejamos que Python maneje el buffer para velocidad

    print(f"\n ¡Terminado! Procesados {total} nuevos registros.")

if __name__ == "__main__":
    run_etl()