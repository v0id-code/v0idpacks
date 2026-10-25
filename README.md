<p align="center">
  <img src="assets/branding/v1/v0idpacks-logo.png" alt="v0idpacks Logo" width="128">
</p>

# v0idpacks

**Deine Texturen. Dein Pack.**

v0idpacks ist eine clientseitige Fabric-Mod für Minecraft Java Edition. Durchsuche deine installierten Ressourcenpakete, vergleiche einzelne Texturen und stelle daraus direkt im Spiel dein eigenes Pack zusammen – mit Favoriten, interaktiver Vorschau und ZIP-Export.

Die Mod arbeitet mit lokalen Dateien. Deine ursprünglichen Ressourcenpakete bleiben unverändert. Die Oberfläche ist derzeit auf Deutsch.

## Funktionen

- **Pack-Bibliothek:** ZIP-Dateien und entpackte Packs mit Icon, Name und Beschreibung durchsuchen. Minecraft-Farbcodes und Textformatierungen werden angezeigt.
- **Suche und Filter:** Nach Textur, Ressourcenpfad und Pack suchen; Kategorien wie Blöcke, Schwerter, Rüstung und Werkzeuge über alle Packs hinweg vergleichen. Zusätzliche Filter für Rüstungsteile und Werkzeugtypen.
- **Favoriten:** Texturen einschließlich Animationsmetadaten lokal sichern und später wiederverwenden – auch wenn das ursprüngliche Pack entfernt wurde.
- **Eigene Zusammenstellung:** Pro Ressourcenpfad eine Variante auswählen und Texturen aus mehreren Packs kombinieren. Das Projekt wird automatisch gespeichert.
- **Interaktive Vorschau:** Drehen und zoomen, zwischen Modell-, Item-, Würfel- und 2D-Ansicht wechseln. Unterstützt gewöhnliche JSON-Quader-Modelle und eine eigene End-Crystal-Vorschau.
- **Export und Aktivierung:** Die Auswahl als Ressourcenpaket exportieren oder direkt speichern und aktivieren. Die Aktivierung bleibt nach einem Neustart erhalten.
- **Kompakte Oberfläche:** Bedienung mit Maus und Tastatur; kurze Animationen lassen sich dauerhaft abschalten. Mod Menu ist optional und zeigt das Mod-Icon an.

## Voraussetzungen und Versionen

| Minecraft | Java für den Spielclient | Mod-Loader |
| --- | --- | --- |
| 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 | 21 oder neuer | Fabric Loader ab 0.20.1 |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | 25 oder neuer | Fabric Loader ab 0.20.1 |

Zusätzlich wird die zur jeweiligen Minecraft-Version passende **Fabric API** benötigt. v0idpacks läuft ausschließlich auf dem Client; auf einem Server muss die Mod nicht installiert werden.

Für jede aufgeführte Minecraft-Version wird eine **eigene JAR** gebaut. Die genaue Build-Matrix einschließlich Fabric-API-Versionen steht in [versions.json](versions.json), die Prüfverfahren in [docs/VERIFICATION.md](docs/VERIFICATION.md). Erfolgreiche Builds sind keine Garantie für die Kompatibilität mit jeder Mod-Kombination. Snapshots und nicht aufgeführte Versionen sind nicht freigegeben.

## Installation

1. Fabric Loader und die passende Fabric API für deine Minecraft-Version installieren.
2. Die Mod [aus dem Quellcode bauen](#selbst-bauen) oder nach einem erfolgreichen [GitHub-Actions-Lauf](https://github.com/v0id-code/v0idpacks/actions/workflows/build.yml) das Artefakt deiner Minecraft-Version herunterladen und entpacken.
3. Die passende Datei `v0idpacks-<Minecraft-Version>-<Mod-Version>.jar` in den `mods`-Ordner deiner Minecraft-Instanz legen. Beispiel für Mod-Version 0.6.1: `v0idpacks-1.21.4-0.6.1.jar`.
4. Eine ältere v0idpacks-JAR ersetzen, sodass nur eine Version installiert ist. Minecraft anschließend neu starten.

Die fertigen Mod-JARs werden nicht im Git-Repository gespeichert. Ein selbst gebautes Paket liegt unter `artifacts/<Mod-Version>/`.

## Erste Schritte

1. Deine Quell-Packs im Ordner `resourcepacks` der verwendeten Minecraft-Instanz ablegen. ZIP-Packs müssen `pack.mcmeta` direkt im ZIP-Hauptverzeichnis enthalten.
2. Im Hauptmenü, Pausenmenü oder Ressourcenpaket-Menü auf **v0idpacks** klicken. In einer Welt öffnet standardmäßig **V** den Editor; die Taste lässt sich unter Steuerung ändern.
3. In der **Bibliothek** ein Pack öffnen oder eine **Kategorie** wählen. Kategorien zeigen passende Texturen aus allen installierten Packs. Mit der Suche lässt sich die Auswahl weiter eingrenzen.
4. Eine Textur auswählen, in der Vorschau prüfen und **In mein Pack übernehmen** anklicken. Eine andere Variante desselben Ressourcenpfads ersetzt die bisherige Auswahl. Favoriten werden unabhängig davon gespeichert.
5. Einen Pack-Namen vergeben und **Speichern & aktivieren** wählen. **Exportieren** erstellt nur das ZIP, ohne die aktiven Ressourcenpakete zu ändern.

Nach Änderungen an den Quell-Packs aktualisiert **Neu einlesen** die Bibliothek. Änderungen am Projekt erscheinen erst nach einem erneuten Export im ausgegebenen Pack.

| Bedienung | Aktion |
| --- | --- |
| Maus in der Vorschau ziehen / Mausrad | Drehen / Zoomen |
| Tab und Enter | Bedienelemente auswählen und auslösen |
| F / Leertaste, außerhalb der Texteingabe | Favorit / Pack-Auswahl umschalten |
| Pfeiltasten und Plus/Minus, bei fokussierter Vorschau | Drehen und zoomen |
| Strg+A / Einfügen | Text in Such- und Namensfeldern bearbeiten |

Über **Animationen: An/Aus** lässt sich die Darstellung anpassen. Diese Einstellung bleibt gespeichert.

## Lokale Daten und Sicherung

Alle Daten liegen in der **jeweiligen Minecraft-Instanz**, auch bei alternativen Launchern:

| Pfad | Inhalt |
| --- | --- |
| `config/v0idpacks/library.json` | Favoriten, Pack-Name und aktuelle Auswahl |
| `config/v0idpacks/library.backup.json` | Vorheriger Projektstand |
| `config/v0idpacks/ui.json` | Animationseinstellung |
| `config/v0idpacks/textures/<sha256>/` | Kopierte Texturen und Metadaten |
| `resourcepacks/v0idpacks-custom-<inhalts-id>.zip` | Exportierte Ressourcenpakete |

Für eine Sicherung des bearbeitbaren Projekts den gesamten Ordner `config/v0idpacks` kopieren. Minecraft speichert die aktive Pack-Auswahl in `options.txt`.

Veränderte Zusammenstellungen bekommen beim Export einen neuen Dateinamen, damit ein bereits geöffnetes ZIP unter Windows nicht überschrieben werden muss. Frühere Exporte bleiben als Dateien erhalten; unveränderte Exporte verwenden dieselbe Datei. Beim Aktivieren bleiben andere aktive Packs ausgewählt.

## Grenzen

- Exportiert werden die ausgewählten **PNGs und `.png.mcmeta`-Dateien**. Animationen bleiben im Export erhalten; die Vorschau zeigt das erste konfigurierte Animationsbild.
- **Custom-Modelle und zusätzliche Abhängigkeiten** werden nicht in den Export übernommen. Ein dafür benötigtes Quell-Pack muss weiterhin aktiv sein. OptiFine-CIT/CTM, Shader, Pack-Overlays und Bedrock-Packs werden nicht zusammengeführt.
- Vorschauen bilden nicht alle Spiel-Renderer nach: dynamische Item-Zustände, spezielle Entities außer dem End Crystal, Biome-Tints und Shader werden nicht vollständig dargestellt. Bei fehlendem Modell gibt es eine Sprite-, Würfel- oder 2D-Ansicht.
- Texturpfade werden nicht zwischen Minecraft-Versionen konvertiert. Ein Quell-Pack muss zu den verwendeten Modellen und Ressourcenpfaden passen.
- Es wird ein aktuelles Projekt verwaltet. Online-Downloads, Sharing und ein Pixel-Editor sind nicht enthalten.

## Selbst bauen

Benötigt wird ein **JDK ab Version 25** für Gradle und Fabric Loom. Java 21 genügt zum Spielen der 1.21.x-Versionen, aber nicht für diesen Build. Der Gradle Wrapper liegt bei; eine globale Gradle-Installation ist nicht nötig. Der erste Build lädt Gradle und die benötigten Abhängigkeiten herunter.

```sh
git clone https://github.com/v0id-code/v0idpacks.git
cd v0idpacks
```

**Windows / PowerShell:**

```powershell
.\gradlew.bat release '-PmcVersion=1.21.4'
```

**Linux / macOS:**

```sh
bash gradlew release -PmcVersion=1.21.4
```

`release` baut die Mod, führt die Core-Tests aus und kopiert die fertige JAR nach `artifacts/<Mod-Version>/`. `mcVersion` kann durch einen Schlüssel aus [versions.json](versions.json) ersetzt werden. Die Mod-Version steht in [gradle.properties](gradle.properties).

Weitere Befehle unter PowerShell:

```powershell
# Tests ausführen
.\gradlew.bat :core:test

# Entwicklungsclient starten
.\gradlew.bat :fabric:runClient '-PmcVersion=1.21.4'

# Alle konfigurierten Minecraft-Versionen bauen
.\scripts\build-all.ps1

# Nach dem vollständigen Build die erzeugten JARs prüfen (Python 3)
python scripts/verify-artifacts.py
```

Unter Linux/macOS kann für Tests und Entwicklungsclient entsprechend `bash gradlew` verwendet werden. Details zu Testberichten, Artefaktprüfung und optionalem Smoke-Test stehen in [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Projektstruktur

```text
core/                  Bibliothek, Persistenz, Export, UI und Tests ohne Minecraft-Abhängigkeit
fabric/                Fabric-Anbindung und Adaptervorlagen für die einzelnen Spielversionen
assets/branding/v1/    Logo und Schriftzug, die beim Build eingebettet werden
gradle/wrapper/        Gradle Wrapper für reproduzierbare Build-Aufrufe
scripts/               Versions-Builds und Artefaktprüfung
docs/                  Hinweise zur Verifikation
.github/workflows/     GitHub Actions für die Versionsmatrix
versions.json          Minecraft-, Fabric-API- und Java-Zuordnung
```

Builds, Caches, lokale Spielinstanzen, Ressourcenpakete, Zugangsdaten und interne Designentwürfe sind über [.gitignore](.gitignore) ausgeschlossen. Quellcode, Tests, Build-Skripte und die benötigten Branding-Grafiken gehören zum Repository.

## Fehler melden

Fehler und Verbesserungsvorschläge können über [GitHub Issues](https://github.com/v0id-code/v0idpacks/issues) gemeldet werden. Hilfreich sind Minecraft-, v0idpacks-, Fabric-Loader- und Fabric-API-Version, weitere beteiligte Mods sowie kurze Schritte zum Nachstellen. Vor dem Teilen von Logs persönliche Daten entfernen.

## Lizenz

Für dieses Projekt ist derzeit keine Lizenzdatei hinterlegt. Ressourcenpakete und Texturen anderer Ersteller sind nicht Bestandteil dieses Repositorys; deren jeweilige Nutzungsbedingungen gelten weiterhin.
