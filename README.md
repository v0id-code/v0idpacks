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

## Installation

Benötigt werden **Fabric Loader ab 0.20.1** und die passende **Fabric API**. Die Build-Matrix umfasst Minecraft **1.21.4–1.21.11** und **26.1–26.3**; alle genauen Versionen stehen in [versions.json](versions.json). Für den Spielclient ist Java **21+** bei 1.21.x bzw. **25+** bei 26.x erforderlich.

1. Die passende Mod-JAR [selbst bauen](#selbst-bauen) oder aus einem erfolgreichen [GitHub-Actions-Lauf](https://github.com/v0id-code/v0idpacks/actions/workflows/build.yml) herunterladen und entpacken.
2. `v0idpacks-<Minecraft-Version>-<Mod-Version>.jar` in den `mods`-Ordner legen und ältere v0idpacks-JARs ersetzen.
3. Minecraft starten und im Menü **v0idpacks** wählen oder in einer Welt **V** drücken.

## Bedienung

Quell-Packs in `resourcepacks` ablegen, im Editor eine Textur auswählen und **In mein Pack übernehmen** anklicken. **Speichern & aktivieren** exportiert und aktiviert deine Zusammenstellung; **Exportieren** erstellt nur das ZIP.

Original-Packs bleiben unverändert. Favoriten und Projekt liegen unter `config/v0idpacks`, Exporte unter `resourcepacks`. Exportiert werden Texturen und Animationsmetadaten; Custom-Modelle, Shader und OptiFine-CIT/CTM werden nicht mit übernommen.

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

Fehler melden: [GitHub Issues](https://github.com/v0id-code/v0idpacks/issues). Eine Projektlizenz ist derzeit nicht hinterlegt.
