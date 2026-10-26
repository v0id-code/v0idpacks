<p align="center">
  <img src="assets/branding/v1/v0idpacks-logo.png" alt="v0idpacks Logo" width="112">
</p>

# v0idpacks

**Deine Texturen. Dein Pack.** Eine clientseitige Fabric-Mod, mit der du Texturen aus installierten Minecraft-Ressourcenpaketen zu einem eigenen Pack kombinierst. Lokal, direkt im Spiel und mit deutscher Oberfläche.

## Funktionen

- ZIP- und Ordner-Packs durchsuchen, Texturen suchen und nach Kategorien filtern.
- Varianten vergleichen, Favoriten speichern und Texturen verschiedener Packs mischen.
- Interaktive 3D- und 2D-Vorschau mit Drehen und Zoomen.
- Zusammenstellungen automatisch speichern, als ZIP exportieren und direkt aktivieren.
- Mehrere eigene Packs anlegen, wechseln und später weiterbearbeiten, jeweils mit eigenem Namen und PNG-Logo.

## Installation

Benötigt werden **Fabric Loader ab 0.20.1** und die passende **Fabric API**. Die Build-Matrix umfasst Minecraft **1.21.4–1.21.11** und **26.1–26.3**; alle genauen Versionen stehen in [versions.json](versions.json). Für den Spielclient ist Java **21+** bei 1.21.x bzw. **25+** bei 26.x erforderlich.

1. Die passende Mod-JAR auf [v0idpacks.de](https://v0idpacks.de/#download), aus dem [aktuellen GitHub-Release](https://github.com/v0id-code/v0idpacks/releases/latest) herunterladen oder [selbst bauen](#selbst-bauen).
2. `v0idpacks-<Minecraft-Version>-<Mod-Version>.jar` in den `mods`-Ordner legen und ältere v0idpacks-JARs ersetzen.
3. Minecraft starten und im Menü **v0idpacks** wählen oder in einer Welt **V** drücken.

## Bedienung

Quell-Packs in `resourcepacks` ablegen und **resourcepacks reloaden** wählen. Die Tabs **Bibliothek**, **Favoriten** und **Mein Pack** stehen oben; das Raster nutzt die ganze Breite. Ein Klick auf eine Textur öffnet die große Vorschau mit Drehen/Zoomen, Darstellungsmodi und **In mein Pack übernehmen**. **Zurück**, Escape oder ein Klick auf den abgedunkelten Hintergrund kehrt zur bisherigen Rasterseite zurück; Suche und Filter bleiben erhalten. **Speichern & aktivieren** exportiert und aktiviert deine Zusammenstellung; **Exportieren** erstellt nur das ZIP.

Die untere Pack-Leiste bündelt Name, Texturanzahl und Export. Über **Anpassen** öffnet sich der Dialog für **Name im Spiel** und Logo. **PNG-Logo wählen…** öffnet die Dateiauswahl für ein quadratisches PNG; die Vorschau erscheint direkt daneben. Das Logo wird lokal kopiert und kann wieder entfernt werden. **Fertig**, Enter im Namensfeld oder Escape speichert den Namen und schließt den Dialog; ein leerer Name bleibt zur Korrektur geöffnet. Beim Export wird der aktuelle Name ebenfalls gespeichert. Mit installierter Mod zeigt Minecraft den Namen ohne technischen Dateizusatz in der Ressourcenpaket-Liste an. Das ZIP enthält das Logo als `pack.png` und den Namen in der Beschreibung; sein Dateiname enthält zusätzlich eine eindeutige Kennung für sichere Aktualisierungen.

Über **Meine Packs** → **Neues Pack** eine weitere Zusammenstellung anlegen. In derselben Übersicht ein vorhandenes Pack zum Weiterbearbeiten auswählen. Jedes Projekt speichert seinen Namen, sein Logo und seine Texturauswahl unabhängig; Favoriten stehen in allen Projekten zur Verfügung. Beim Wechsel wird auch der eingegebene Name gespeichert. Nach einem Neustart öffnet sich das zuletzt bearbeitete Projekt. Vorhandene Ein-Pack-Projekte werden automatisch übernommen. Das Bearbeiten allein aktiviert kein Pack; **Speichern & aktivieren** ersetzt nur frühere Exporte desselben Projekts und lässt andere aktive Packs bestehen.

Original-Packs bleiben unverändert. Favoriten und Projekte liegen unter `config/v0idpacks`, Exporte unter `resourcepacks`. Exportiert werden Texturen und Animationsmetadaten; Custom-Modelle, Shader und OptiFine-CIT/CTM werden nicht mit übernommen.

Beim End Crystal werden Inventar-Icon und Entity-Textur gemeinsam aus demselben Quell-Pack übernommen, ersetzt und entfernt. Das Quell-Pack muss beide Dateien enthalten; fehlt eine davon oder ist sie beschädigt, bleibt die bisherige Auswahl erhalten. Bei bereits übernommenen Crystals nutzt die Vorschau die gespeicherten Projekttexturen, die auch exportiert werden. Auto zeigt den platzierten Crystal, 2D das Inventar-Icon. Bei älteren unvollständigen oder gemischten Crystal-Auswahlen den gewünschten Crystal erneut übernehmen; bis dahin wird der Export mit einem Hinweis abgewiesen.

## Selbst bauen

Voraussetzung: **JDK 25 oder neuer**. Der Gradle Wrapper liegt bei.

**Windows / PowerShell:**

```powershell
.\gradlew.bat release '-PmcVersion=1.21.4'
```

**Linux / macOS:**

```sh
bash gradlew release -PmcVersion=1.21.4
```

Die JAR liegt anschließend unter `artifacts/<Mod-Version>/`. Der Build führt auch die Core-Tests aus. Weitere Prüfungen: [Verifikation](docs/VERIFICATION.md).

## Automatische Website-Downloads

Änderungen stehen im [Changelog](CHANGELOG.md). Der Abschnitt zur aktuellen Mod-Version wird auch in die GitHub-Release-Notes übernommen.

Ein Push auf `main` baut und testet alle Minecraft-Versionen aus `versions.json`. Erst wenn die gesamte Matrix erfolgreich ist, veröffentlicht der Workflow `.github/workflows/build.yml` die Produktions-JARs und SHA-256-Prüfsummen als GitHub-Release. Pull Requests und andere Branches veröffentlichen nichts. Über **Run workflow** lässt sich derselbe Ablauf für `main` auch manuell starten.

Die Mod-Version stammt aus `mod_version` in `gradle.properties`; diese für eine neue Produktversion anheben. Jeder veröffentlichte Build bekommt zusätzlich eine eigene Buildnummer, auch wenn die Mod-Version gleich bleibt. Bereits veröffentlichte JARs werden nicht überschrieben. Während des Uploads bleibt der Release ein Entwurf. Ein älterer Workflow-Lauf darf einen inzwischen neueren Stand auf `main` nicht als aktuell veröffentlichen.

Die Website liest den aktuellen vollständigen Release automatisch aus der öffentlichen GitHub-API und bietet die JARs direkt zum Download an. Mod-Version, Buildnummer, Dateigröße und das tatsächliche Uploaddatum der ausgewählten JAR aktualisieren sich gemeinsam. Nach der einmaligen Bereitstellung des Website-Codes ist für Mod-Updates kein Website-Deployment nötig. Der Workflow verwendet das kurzlebige `GITHUB_TOKEN` mit `contents: write` nur im Veröffentlichungsjob; zusätzliche Zugangsdaten oder ein Token für das Website-Repository werden nicht benötigt.

Veröffentlichungslogik lokal prüfen: `python -m unittest discover -s scripts/tests -p test_publish_release.py`. Diese Tests simulieren GitHub-Aufrufe und veröffentlichen keine echten Dateien.

Fehler melden: [GitHub Issues](https://github.com/v0id-code/v0idpacks/issues). Das Projekt steht unter der [MIT-Lizenz](LICENSE).
