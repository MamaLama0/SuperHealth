#!/usr/bin/env python3
"""USDA FoodData Central (SR Legacy, domaine public) -> app/src/superhealth/assets/usdafcdb.json.

Même format compact que swissfcdb.json, lu par UsdaFoods.kt :
  {"v": version, "n": [codes micros], "f": [[id, nom, synonymes, catégorie, kcal, prot, lip, gluc, fibres, sucres, AGS, sel, {micro: valeur}]]}
Valeurs pour 100 g. Les glucides sont « disponibles » (par différence moins fibres), comme la base suisse.

Usage : python3 -I tools/import_usda_fdc.py <dossier FoodData_Central_sr_legacy_food_csv_*> <sortie.json>
"""
import csv
import json
import sys
from pathlib import Path

MACROS = {1008: "kcal", 1003: "protein", 1004: "fat", 1005: "carbs", 1079: "fiber", 2000: "sugar", 1258: "sat", 1093: "sodium"}
MICROS = {
    1292: "mufa", 1051: "water", 1106: "vitA", 1107: "betaCarotene", 1165: "b1", 1175: "b6", 1167: "b3", 1177: "folate",
    1170: "b5", 1162: "vitC", 1109: "vitE", 1092: "potassium", 1093: "sodium", 1087: "calcium", 1090: "magnesium",
    1091: "phosphorus", 1089: "iron", 1095: "zinc", 1103: "selenium", 1404: "ala", 1166: "b2", 1293: "pufa",
    1278: "epa", 1272: "dha", 1253: "chol", 1178: "b12", 1114: "vitD", 1009: "starch", 1018: "alcohol",
}
FALLBACK = {1404: 1270}  # ALA : n-3 c,c,c sinon 18:3 total


def main(src: Path, out: Path):
    cats = {r["id"]: r["description"] for r in csv.DictReader(open(src / "food_category.csv", encoding="utf-8"))}
    foods = {int(r["fdc_id"]): (r["description"].strip(), cats.get(r["food_category_id"], ""))
             for r in csv.DictReader(open(src / "food.csv", encoding="utf-8"))}
    wanted = set(MACROS) | set(MICROS) | set(FALLBACK.values())
    values: dict[int, dict[int, float]] = {}
    for r in csv.DictReader(open(src / "food_nutrient.csv", encoding="utf-8")):
        nid = int(r["nutrient_id"])
        if nid in wanted and r["amount"]:
            values.setdefault(int(r["fdc_id"]), {})[nid] = float(r["amount"])
    codes = sorted(set(MICROS.values()))
    index = {c: i for i, c in enumerate(codes)}
    rows = []
    for fid, (name, cat) in sorted(foods.items(), key=lambda kv: kv[1][0].lower()):
        v = values.get(fid, {})
        if 1008 not in v:
            continue
        for main_id, alt in FALLBACK.items():
            if main_id not in v and alt in v:
                v[main_id] = v[alt]
        g = lambda k: round(v.get(k, 0.0), 2)
        fiber = g(1079)
        carbs = round(max(0.0, v.get(1005, 0.0) - fiber), 2)
        salt = round(v.get(1093, 0.0) * 2.5 / 1000, 2)
        micros = {str(index[code]): round(v[nid], 3) for nid, code in MICROS.items() if nid in v}
        rows.append([fid, name, "", cat, g(1008), g(1003), g(1004), carbs, fiber, g(2000), g(1258), salt, micros])
    out.write_text(json.dumps({"v": "SR Legacy 2018-04", "n": codes, "f": rows}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{len(rows)} aliments -> {out} ({out.stat().st_size // 1024} Ko)")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
