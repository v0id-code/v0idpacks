# Verifikation

Die Build-Matrix steht in [versions.json](../versions.json). Jede Minecraft-Version erhält eine eigene JAR mit einer exakten Versionsanforderung. Die folgenden Prüfungen lassen sich aus dem Repository reproduzieren.

## Core-Tests

```powershell
.\gradlew.bat :core:test
```

Die JUnit-Suite prüft unter anderem:

- Persistenz, Favoriten, Variantenersetzung und entfernte Quell-Packs;
- Pack-Name und Logo: alte Projektdateien, lokale PNG-Kopie, Validierung, Ersetzen/Entfernen, Speichern über den Anpassen-Dialog und unveränderte frühere Exporte;
- Mehrere Projekte: Wechsel, unabhängige Texturauswahl und Logos, gemeinsame Favoriten, Migration, Sicherung/Wiederherstellung und getrennte Exporte bei gleichen Namen;
- ZIP- und Pfadvalidierung, beschädigte Daten und Animationsmetadaten;
- Panorama-Export: vollständige Sätze, quadratische Seiten mit gleicher Auflösung, Erhalt bestehender Exporte bei Fehlern und Abbruch vor der Aktivierung;
- Pack-Informationen, Minecraft-Formatierungen und Texturkategorien;
- Rüstungs- und Werkzeugfilter, Suche und Tastaturbedienung;
- Modellvorschauen, Rendering, UI-Einstellungen und Animationen.
- Modell-Export: verkleinerte Goldäpfel mit geerbten Transformationen, Schild- und Blockiermodelle, moderne bedingte Item-Definitionen, Crystal-Item-Geometrie, animierte Abhängigkeiten, unveränderte Snapshots nach Quellenentfernung, getrennte Elternmodelle beim Mischen von Packs sowie beschädigte/unsichere Ressourcen und Variantenersetzung.
- End Crystal: gemeinsame Auswahl aus Ordner- und ZIP-Packs, Ersetzen/Entfernen beider Texturen, unveränderte Auswahl bei fehlenden/beschädigten Partnern, Reparatur alter Auswahlen sowie Projektvorschau und Export aus denselben gespeicherten Dateien auch bei geänderten/entfernten Quellen.

Der HTML-Bericht entsteht unter `core/build/reports/tests/test/index.html`. UI-Tests erzeugen Prüfbilder mit Demo-Texturen in `core/build/previews`. Beide Verzeichnisse sind generierte Dateien und werden nicht eingecheckt.

## Builds und Artefakte

```powershell
# Einzelne Minecraft-Version einschließlich Core-Tests bauen
.\gradlew.bat release '-PmcVersion=1.21.4'

# Vollständige Matrix bauen
.\scripts\build-all.ps1

# Alle erzeugten JARs prüfen; benötigt Python 3 und den lokalen Loom-Cache
python scripts/verify-artifacts.py
```

Unter Linux/macOS lautet der Einzelaufruf `bash gradlew release -PmcVersion=1.21.4`. Die [GitHub-Actions-Matrix](../.github/workflows/build.yml) baut alle konfigurierten Versionen separat und stellt die JARs als Workflow-Artefakte bereit.

`build-all.ps1` speichert Einzelprotokolle und Ergebnisse unter `build/compatibility/`. Die fertigen JARs liegen in `artifacts/<Mod-Version>/`.

Der Artefaktprüfer kontrolliert ZIP-Integrität, Mod-Version, Client-Umgebung, exakte Minecraft-Abhängigkeit, Java-Zielversion, benötigte Klassen und das Fehlen des Smoke-Test-Entrypoints. Er gleicht das Ressourcenpaket-Format mit der `version.json` des lokalen Minecraft-Clientarchivs ab. Außerdem prüft er den Icon-Pfad, die quadratische PNG-Abmessung und die bytegleiche Einbettung der beiden Branding-Grafiken.

## Optionaler Smoke-Test

```powershell
.\gradlew.bat :fabric:runClient '-PmcVersion=1.21.4' -Psmoke
```

Dieser Aufruf startet Minecraft in einer separaten Entwicklungsinstanz unter `run/1.21.4-smoke`. Der Test erstellt dort eigene Testdaten, öffnet den Editor, prüft Favoriten, Auswahl, Export und Aktivierung und beendet den Client anschließend. Die Erfolgsmeldung im Log lautet `V0IDPACKS_SMOKE_PASS`.

Dabei werden auch der tatsächliche Titel im Minecraft-Pack-Repository, das exportierte `pack.png` und die gespeicherten Pack-Einstellungen geprüft. Der Test legt außerdem ein zweites Projekt an, aktiviert beide und bearbeitet das erste erneut: genau ein Export pro Projekt muss aktiv bleiben. Die native Dateiauswahl wird manuell über **Anpassen** → **PNG-Logo wählen…** geprüft.

Der Editor nutzt Tabs oben, ein Raster über die volle Breite und die untere Pack-Leiste. **resourcepacks reloaden** steht neben Suche und Kategorie. Die große Texturvorschau öffnet sich erst durch Anklicken einer Textur; sie sperrt Hintergrundaktionen und behält beim Schließen Rasterseite, Suche und Filter bei. Die nativen Prüfbilder decken 1120×660 und 760×440 Pixel, Werkzeug-/Rüstungsfilter, leere Raster sowie Vorschau-, Pack- und Projektdialoge ab. Hit-Target-Tests prüfen Filter und Pack-Einstellungen anhand der tatsächlich gerenderten Steuerelemente; Tastaturtests prüfen Escape, Tab und die Leertaste im Vorschaudialog. Feather-Icons werden lokal als 16-Pixel-PNGs ausgeliefert; Original-SVGs und MIT-Lizenz liegen unter `assets/ui/icons`.

Der Smoke-Entrypoint wird ausschließlich mit `-Psmoke` eingebunden. Normale Builds enthalten ihn nicht; der `release`-Task lehnt `-Psmoke` ab.

## Aussagekraft

Logo und Schriftzug liegen als transparente RGBA-PNGs vor. Die Kopfzeile richtet beide anhand ihrer sichtbaren Bildgrenzen auf 22 Pixel Höhe aus; transparente Außenränder beeinflussen die Ausrichtung nicht. Die Prüfbilder `workshop.png` und `tools-small.png` zeigen das Branding bei beiden Fenstergrößen.

Automatisierte Tests und erfolgreiche Builds ersetzen keine Ingame-Prüfung jeder Minecraft-Version. Für einen Release zusätzlich Installation, Pack-Auswahl, Vorschauen, Export/Aktivierung, einen Neustart und gegebenenfalls die Anzeige in Mod Menu prüfen. Kompatibilität mit weiteren Mods oder Grafik-Backends muss separat getestet werden.

Das Repository enthält keine Minecraft-Spielressourcen und keine fremden Ressourcenpakete. Lokale Instanzen, Testdaten, Caches und fertige JARs sind über die `.gitignore` ausgeschlossen.
