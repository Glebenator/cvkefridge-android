#!/usr/bin/env python3
"""Makes spec/foods.json from the USDA FSIS FoodKeeper data.

    python spec/tools/build-foods.py <path to foodkeeper.json> <date of the download, YYYY-MM-DD>

Get the source file with a browser from https://www.fsis.usda.gov/shared/data/EN/foodkeeper.json. The server refuses
requests from scripts. The data set is "FSIS - FoodKeeper Data" at https://catalog.data.gov/dataset/fsis-foodkeeper-data,
with the licence CC0 1.0. The source file is not in this repository.

The rules of the conversion:
- A food gets the identifier "fk-" plus the FoodKeeper ID.
- The days for a storage kind come from the "date of purchase" values (DOP_Pantry, DOP_Refrigerate, DOP_Freeze). If a
  food has none, they come from the general values (Pantry, Refrigerate, Freeze). Values for "after opening" and
  "after thawing" are not used.
- The days are the minimum of the range, which is the careful value. If only the maximum has a value, that is used.
- Weeks are 7 days, months are 30 days, and years are 365 days. A value in hours, and a value below 1 day, gives no
  days.
- "Package use-by date", "Indefinitely", "When Ripe", and "Not Recommended" give no days. The app then makes no
  estimate, and the person enters the printed date.
"""
import json
import sys
from pathlib import Path

SPEC = Path(__file__).resolve().parent.parent

DAYS_PER = {"days": 1, "weeks": 7, "months": 30, "year": 365, "years": 365}

# FoodKeeper Category_ID to the category of this product.
CATEGORY = {
    1: "other",       # Baby Food
    2: "bakery",      # Baked Goods: Bakery
    3: "other",       # Baked Goods: Baking and Cooking
    4: "bakery",      # Baked Goods: Refrigerated Dough
    5: "beverage",    # Beverages
    6: "condiment",   # Condiments, Sauces & Canned Goods
    7: "dairy",       # Dairy Products & Eggs
    8: "other",       # Food Purchased Frozen
    9: "other",       # Grains, Beans & Pasta
    10: "meat", 11: "meat", 12: "meat", 13: "meat",   # Meat
    14: "meat", 15: "meat", 16: "meat", 17: "meat",   # Poultry
    18: "produce",    # Produce: Fresh Fruits
    19: "produce",    # Produce: Fresh Vegetables
    20: "meat", 21: "meat", 22: "meat",               # Seafood
    23: "other",      # Shelf Stable Foods
    24: "other",      # Vegetarian Proteins
    25: "leftovers",  # Deli & Prepared Foods
}

# The source groups for each storage kind, the preferred group first.
GROUPS = {
    "pantry": ["DOP_Pantry", "Pantry"],
    "fridge": ["DOP_Refrigerate", "Refrigerate"],
    "freezer": ["DOP_Freeze", "Freeze"],
}


def clean(text):
    """Text without space at the ends, and with the damaged character of the source file repaired."""
    if text is None:
        return None
    text = " ".join(str(text).split())
    return text.replace("consomm�", "consommé") or None


def days_of(row, group):
    metric = clean(row.get(group + "_Metric"))
    value = row.get(group + "_Min")
    if not isinstance(value, (int, float)):
        value = row.get(group + "_Max")
    if metric is None or not isinstance(value, (int, float)) or metric.lower() not in DAYS_PER:
        return None
    days = int(value * DAYS_PER[metric.lower()])
    return days if days >= 1 else None


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    source = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    sheet = next(s for s in source["sheets"] if s["name"] == "Product")
    foods = []
    for cells in sheet["data"]:
        row = {}
        for cell in cells:
            row.update(cell)
        days = {}
        for kind, groups in GROUPS.items():
            for group in groups:
                value = days_of(row, group)
                if value is not None:
                    days[kind] = value
                    break
        food = {"id": "fk-%d" % int(row["ID"]), "name": clean(row["Name"])}
        subtitle = clean(row.get("Name_subtitle"))
        if subtitle:
            food["subtitle"] = subtitle
        food["category"] = CATEGORY[int(row["Category_ID"])]
        keywords = []
        for word in (clean(row.get("Keywords")) or "").split(","):
            word = word.strip().lower()
            if word and word not in keywords:
                keywords.append(word)
        food["keywords"] = keywords
        food["days"] = days
        foods.append(food)
    foods.sort(key=lambda food: int(food["id"][3:]))

    if "�" in json.dumps(foods, ensure_ascii=False):
        print("The source has a damaged character that clean() does not repair.")
        return 1

    lines = ["{"]
    header = {
        "source": "USDA FSIS FoodKeeper Data, https://catalog.data.gov/dataset/fsis-foodkeeper-data",
        "sourceFile": source.get("fileName"),
        "sourceLicence": "CC0 1.0",
        "downloaded": sys.argv[2],
        "note": "Made by spec/tools/build-foods.py. Do not edit by hand.",
    }
    for key, value in header.items():
        lines.append("  %s: %s," % (json.dumps(key), json.dumps(value, ensure_ascii=False)))
    lines.append('  "foods": [')
    lines.extend("    %s%s" % (json.dumps(food, ensure_ascii=False), "," if index < len(foods) - 1 else "")
                 for index, food in enumerate(foods))
    lines.append("  ]")
    lines.append("}")
    (SPEC / "foods.json").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("%d foods; with days: %s" % (len(foods), {
        kind: sum(1 for food in foods if kind in food["days"]) for kind in GROUPS}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
