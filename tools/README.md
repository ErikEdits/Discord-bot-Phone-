# Werkzeuge

- `strings.py`: Quelle aller App-Texte (DE + EN). Erzeugt `app/src/main/res/values*/strings.xml` und prüft,
  dass jeder im Code verwendete Schlüssel existiert:
  `cat $(find app/src/main/kotlin -name '*.kt') | python3 tools/strings.py`
- `catalog_bootstrap.py`: hat den ersten Katalog erzeugt. Danach wird `catalog/` direkt als JSON gepflegt.
