# The shared specification

This folder has the product rules of CVKE Fridge in a form that both apps test against. It is the reference. An app
that does not agree with it has a defect.

| File | Contents |
|---|---|
| [rules.md](rules.md) | The core rules in words |
| [vectors/](vectors/) | Test vectors: inputs and expected results for each rule, as JSON |
| [foods.json](foods.json) | The table of foods for the expiry estimate |
| [tools/vectors.py](tools/vectors.py) | The reference model. It calculates the expected results of the vectors. |
| [tools/build-foods.py](tools/build-foods.py) | Makes `foods.json` from the USDA FSIS FoodKeeper data |

## How an app uses this folder

- Each app repository has a copy of this folder at `spec/`, and a file `spec.ref` with the commit of this repository
  from which the copy comes. The copy is exact. Do not edit it in an app repository.
- The core tests of each app read the vectors from `spec/vectors/` and run each case through the core module.
- The app puts `spec/foods.json` in its build as a resource.
- The CI of each app gets this folder at the commit in `spec.ref` and compares it with the copy. A difference fails
  the build.
- To get a new version, run `python scripts/update-spec.py <commit>` in the app repository. It replaces the copy and
  writes `spec.ref`.

## How to change a rule

1. Change `rules.md`.
2. Add or change the inputs in the vector file. Change `tools/vectors.py` if the rule itself changed.
3. Run `python spec/tools/vectors.py --write`. It writes the expected results. Read the changes.
4. Commit and push. The workflow `spec.yml` runs `python spec/tools/vectors.py`, which fails if a vector file and
   the reference model do not agree.
5. Update the copy in each app repository and make both core modules pass.

## The vector files

Each case is a JSON object. Its `expect` value is the expected result. All other values are the input.

| File | Rule | Notes |
|---|---|---|
| `calendar-date.json` | 2 | Sections `parse`, `plusDays`, `daysUntil`, `order`, `dateOfInstant` |
| `freshness.json` | 3 | |
| `expiry-estimate.json` | 4 | It has its own small table of foods, so that a change to `foods.json` does not change it |
| `use-first.json` | 5 | |
| `plan-use.json` | 6 | |
| `change-log.json` | 7 | Each case is a full log and the result of its replay |
| `food-table.json` | 4 | Facts about `foods.json`: the count and some foods. It proves that an app reads the whole table. |

Dates are text in the form `YYYY-MM-DD`. Quantities are whole thousandths. Instants are whole milliseconds from
1970-01-01T00:00:00Z. A value of `null` means that there is no value.

## The table of foods

`foods.json` comes from "FSIS - FoodKeeper Data" of the United States Department of Agriculture, Food Safety and
Inspection Service: <https://catalog.data.gov/dataset/fsis-foodkeeper-data>. The catalog gives the licence as CC0 1.0.
`tools/build-foods.py` has the rules of the conversion. A food can have no days for a storage kind, for example when
the source says "Package use-by date". The app then makes no estimate.
