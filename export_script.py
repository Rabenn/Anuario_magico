import sqlite3
import json
import csv
import base64
import xml.etree.ElementTree as ET
from xml.dom import minidom

DB_FILE = "hogwarts.db"

def get_data_from_db():
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id, name, house, wand, image_blob FROM wizards")
        rows = cursor.fetchall()
    except sqlite3.OperationalError:
        return []

    processed_data = []
    for row in rows:
        img_base64 = ""
        if row["image_blob"]:
            img_base64 = base64.b64encode(row["image_blob"]).decode('utf-8')
        processed_data.append({
            "id": row["id"],
            "name": row["name"],
            "house": row["house"],
            "wand": row["wand"],
            "imageBase64": img_base64
        })
    conn.close()
    return processed_data

def export_json(data):
    with open("wizards_postman.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)
    print("✅ JSON Generado.")

def export_csv(data):
    if not data: return
    headers = data[0].keys()
    with open("wizards_sheet.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=headers)
        writer.writeheader()
        writer.writerows(data)
    print("✅ CSV Generado.")

def export_xml(data):
    root = ET.Element("Wizards")
    for item in data:
        wizard_el = ET.SubElement(root, "Wizard")
        ET.SubElement(wizard_el, "Id").text = str(item["id"])
        ET.SubElement(wizard_el, "Name").text = str(item["name"])
        ET.SubElement(wizard_el, "House").text = str(item["house"])
        ET.SubElement(wizard_el, "Wand").text = str(item["wand"])
        ET.SubElement(wizard_el, "ImageBase64").text = item["imageBase64"]
    xml_str = minidom.parseString(ET.tostring(root)).toprettyxml(indent="   ")
    with open("wizards_data.xml", "w", encoding="utf-8") as f:
        f.write(xml_str)
    print("✅ XML Generado.")

if __name__ == "__main__":
    print("🚀 Exportando datos...")
    data = get_data_from_db()
    if data:
        export_json(data)
        export_csv(data)
        export_xml(data)
    else:
        print("⚠️ Sin datos.")