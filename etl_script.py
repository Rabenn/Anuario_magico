import requests
import sqlite3
import time
import io
from PIL import Image  # Necesita: pip install Pillow

# Configuración
API_URL = "https://api.potterdb.com/v1/characters"
DB_FILE = "hogwarts.db"
RATE_LIMIT_DELAY = 4.0

def setup_database():
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
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

def get_existing_ids(cursor):
    cursor.execute("SELECT id FROM wizards")
    return {row[0] for row in cursor.fetchall()}

def get_image_as_jpeg_bytes(url):
    """Descarga la imagen, la convierte a JPEG y devuelve los bytes"""
    if not url: return None
    try:
        time.sleep(RATE_LIMIT_DELAY)
        response = requests.get(url, timeout=10)

        if response.status_code == 200:
            # --- LA MAGIA OCURRE AQUÍ ---
            # 1. Leemos los bytes originales (WebP, PNG, etc.)
            image_stream = io.BytesIO(response.content)
            pil_image = Image.open(image_stream)

            # 2. Convertimos a RGB (necesario si la imagen original tiene transparencia)
            if pil_image.mode in ("RGBA", "P"):
                pil_image = pil_image.convert("RGB")

            # 3. Guardamos como JPEG en un buffer de memoria
            output_stream = io.BytesIO()
            pil_image.save(output_stream, format="JPEG", quality=90)

            # 4. Devolvemos los bytes del JPEG
            return output_stream.getvalue()

    except Exception as e:
        print(f"    [!] Error procesando imagen: {e}")
    return None

def run_etl():
    conn = setup_database()
    cursor = conn.cursor()
    existing_ids = get_existing_ids(cursor)

    page = 1
    total_new = 0
    print(f"⚡ Iniciando ETL (Modo Conversión JPEG). Magos en BD: {len(existing_ids)}")

    while True:
        try:
            print(f" -> Buscando en página {page}...")
            resp = requests.get(f"{API_URL}?page[number]={page}&page[size]=100")
            time.sleep(RATE_LIMIT_DELAY)

            if resp.status_code != 200: break
            data = resp.json()
            if not data['data']: break

            for item in data['data']:
                char_id = item['id']
                if char_id in existing_ids: continue

                attrs = item['attributes']
                img_url = attrs.get('image')

                if img_url:
                    name = attrs.get('name')
                    house = attrs.get('house') or "Sin Casa"

                    wand_data = attrs.get('wands')
                    wand_str = wand_data[0] if (wand_data and isinstance(wand_data, list)) else "Desconocida"

                    print(f"    Guardando y convirtiendo: {name}")

                    # Usamos la nueva función de conversión
                    blob = get_image_as_jpeg_bytes(img_url)

                    if blob:
                        cursor.execute('INSERT OR REPLACE INTO wizards VALUES (?,?,?,?,?)',
                                       (char_id, name, house, wand_str, blob))
                        conn.commit()
                        existing_ids.add(char_id)
                        total_new += 1

            if not data.get('links') or not data['links'].get('next'):
                break
            page += 1

        except Exception as e:
            print(f"Error: {e}")
            break

    conn.close()
    print(f"✅ Fin. Nuevos guardados y convertidos: {total_new}")

if __name__ == "__main__":
    run_etl()