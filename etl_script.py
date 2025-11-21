import requests
import sqlite3
import time

# Configuración
API_URL = "https://api.potterdb.com/v1/characters"
DB_FILE = "hogwarts.db"

def setup_database():
    """Crea la base de datos y la tabla con soporte para BLOB"""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()

    # Creamos la tabla si no existe
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS wizards (
            id TEXT PRIMARY KEY,
            name TEXT,
            house TEXT,
            wand TEXT,
            image_blob BLOB
        )
    ''')
    conn.commit()
    return conn

def get_image_bytes(url):
    """Descarga la imagen y devuelve los bytes crudos (BLOB)"""
    if not url:
        return None
    try:
        # Timeout de 10 segundos para no bloquearse
        response = requests.get(url, timeout=10)
        if response.status_code == 200:
            return response.content # Retorna bytes
    except Exception as e:
        print(f"    [!] Error descargando imagen: {e}")
    return None

def run_etl():
    conn = setup_database()
    cursor = conn.cursor()

    page = 1
    total_saved = 0

    print(f"⚡ Conectando a {API_URL}...")
    print("⚡ Descargando imágenes e insertando en SQLite (esto puede tardar un poco)...")

    while True:
        print(f" -> Procesando página {page}...")
        try:
            # Pedimos 50 personajes por página
            response = requests.get(f"{API_URL}?page[number]={page}&page[size]=50")

            if response.status_code != 200:
                break

            data = response.json()
            if not data['data']:
                break # Fin de datos

            for item in data['data']:
                attrs = item['attributes']
                image_url = attrs.get('image')

                # FILTRO: Solo guardamos si tiene imagen (requisito del anuario)
                if image_url:
                    name = attrs.get('name')
                    house = attrs.get('house') or "Sin Casa"

                    # Lógica para extraer la varita limpiamente
                    wand_data = attrs.get('wands')
                    wand_str = "Desconocida"
                    if wand_data and isinstance(wand_data, list) and len(wand_data) > 0:
                        wand_str = wand_data[0]
                    elif isinstance(wand_data, str):
                        wand_str = wand_data

                    # Descargamos el binario de la imagen
                    blob = get_image_bytes(image_url)

                    if blob:
                        # Insertamos o Reemplazamos en la BD
                        cursor.execute('''
                            INSERT OR REPLACE INTO wizards (id, name, house, wand, image_blob)
                            VALUES (?, ?, ?, ?, ?)
                        ''', (item['id'], name, house, wand_str, blob))

                        total_saved += 1
                        print(f"    Guardado: {name}")

            conn.commit() # Guardamos cambios por página

            # Comprobamos si hay página siguiente
            if not data.get('links') or not data['links'].get('next'):
                break

            page += 1
            time.sleep(0.2) # Pequeña pausa para ser educados con la API

        except Exception as e:
            print(f"Error crítico en página {page}: {e}")
            break

    conn.close()
    print(f"✅ FINALIZADO. {total_saved} personajes guardados en '{DB_FILE}'.")

if __name__ == "__main__":
    run_etl()