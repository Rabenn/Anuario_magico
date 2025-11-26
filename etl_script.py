import requests
import sqlite3
import time
import io
from PIL import Image

# Configuración
API_URL = "https://api.potterdb.com/v1/characters"
DB_FILE = "hogwarts.db"
RATE_LIMIT_DELAY = 4.0

def setup_database():
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    # Añadimos columna image_url para guardar la referencia y descargar luego
    try:
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS wiz
            ards (
                id TEXT PRIMARY KEY,
                name TEXT,
                house TEXT,
                wand TEXT,
                image_blob BLOB,
                image_url_text TEXT
            )
        ''')
    except:
        # Si la tabla ya existe sin la columna nueva, no pasa nada por ahora
        pass

    # Parche por si la tabla ya existía sin la columna de URL
    try:
        cursor.execute("ALTER TABLE wizards ADD COLUMN image_url_text TEXT")
    except:
        pass # Ya existía

    conn.commit()
    return conn

def get_existing_ids(cursor):
    cursor.execute("SELECT id FROM wizards")
    return {row[0] for row in cursor.fetchall()}

def get_image_bytes(url):
    """Descarga la imagen, convierte a JPEG y devuelve bytes"""
    if not url: return None
    try:
        time.sleep(RATE_LIMIT_DELAY) # Respetar API
        response = requests.get(url, timeout=10)
        if response.status_code == 200:
            img_io = io.BytesIO(response.content)
            pil_img = Image.open(img_io)
            if pil_img.mode in ("RGBA", "P"): pil_img = pil_img.convert("RGB")
            out_io = io.BytesIO()
            pil_img.save(out_io, format="JPEG", quality=85)
            return out_io.getvalue()
    except Exception as e:
        print(f"    [!] Error imagen: {e}")
    return None

def run_etl():
    conn = setup_database()
    cursor = conn.cursor()
    existing_ids = get_existing_ids(cursor)

    print("🚀 FASE 1: Descarga RÁPIDA de datos (Solo texto)...")

    page = 1
    total_text_saved = 0

    # --- FASE 1: TEXTO (Muy rápido) ---
    while True:
        try:
            print(f" -> Leyendo página {page} de la API...")
            resp = requests.get(f"{API_URL}?page[number]={page}&page[size]=100")

            if resp.status_code != 200: break
            data = resp.json()
            if not data['data']: break

            for item in data['data']:
                char_id = item['id']
                # Si ya existe, saltamos (para no sobrescribir fotos ya descargadas)
                if char_id in existing_ids: continue

                attrs = item['attributes']
                img_url = attrs.get('image')

                # Solo nos interesan si tienen URL de imagen (requisito del anuario)
                if img_url:
                    name = attrs.get('name')
                    house = attrs.get('house') or "Sin Casa"
                    wand_data = attrs.get('wands')
                    wand_str = wand_data[0] if (wand_data and isinstance(wand_data, list)) else "Desconocida"

                    # GUARDAMOS SIN LA IMAGEN (BLOB es None) PERO GUARDAMOS LA URL
                    cursor.execute('''
                        INSERT OR REPLACE INTO wizards (id, name, house, wand, image_blob, image_url_text)
                        VALUES (?, ?, ?, ?, ?, ?)
                    ''', (char_id, name, house, wand_str, None, img_url))

                    total_text_saved += 1

            conn.commit()
            if not data.get('links') or not data['links'].get('next'): break
            page += 1
            time.sleep(1) # Pequeña pausa entre páginas de texto

        except Exception as e:
            print(f"Error en Fase 1: {e}")
            break

    print(f"✅ FASE 1 COMPLETADA. {total_text_saved} magos añadidos.")
    print("💡 YA PUEDES ABRIR TU APP JAVA. Los datos están ahí (con foto por defecto).")
    print("-" * 50)
    print("🐢 FASE 2: Descargando imágenes en segundo plano...")
    print("   (Puedes cerrar esto cuando quieras y continuar otro día)")

    # --- FASE 2: IMÁGENES (Lento, rellena los huecos) ---
    # Buscamos magos que tengan URL pero NO tengan BLOB (imagen descargada)
    cursor.execute("SELECT id, name, image_url_text FROM wizards WHERE image_blob IS NULL AND image_url_text IS NOT NULL")
    pending_images = cursor.fetchall()

    print(f"📸 Faltan {len(pending_images)} imágenes por descargar.")

    for row in pending_images:
        char_id, name, url = row
        print(f"    Descargando foto de: {name}...")

        blob = get_image_bytes(url)

        if blob:
            cursor.execute("UPDATE wizards SET image_blob = ? WHERE id = ?", (blob, char_id))
            conn.commit()
        else:
            print(f"    ⚠️ No se pudo bajar foto de {name}")

    conn.close()
    print("✅ ¡TODO COMPLETADO AL 100%!")

if __name__ == "__main__":
    run_etl()