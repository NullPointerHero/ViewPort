# ViewPort Plugin

Ein IntelliJ IDEA Plugin, das einen eingebetteten Web-Browser bereitstellt.

## Features

- **Tool Window**: Ein Tool Window auf der rechten Seite (wie der DB Browser)
- **URL-Eingabe**: Textfeld oben für die URL-Eingabe
- **Standard-URL**: Google.com wird standardmäßig geladen
- **Kontext-Menü**: Rechtsklick-Menü zum Öffnen des Browsers
- **Toolbar-Button**: Button in der Toolbar zum Öffnen des Browsers
- **Vollständiger Browser**: JCEF (Chromium) für moderne Webseiten mit CSS und JavaScript

## Installation

1. Bauen Sie das Plugin mit Gradle:
   ```bash
   ./gradlew buildPlugin
   ```

2. Installieren Sie das Plugin in IntelliJ IDEA:
   - Gehen Sie zu `File` → `Settings` → `Plugins`
   - Klicken Sie auf das Zahnrad-Symbol → `Install Plugin from Disk`
   - Wählen Sie die generierte `.zip` Datei aus dem `build/distributions` Ordner

## Verwendung

### Tool Window öffnen
- Klicken Sie auf das ViewPort-Icon in der rechten Toolbar
- Oder gehen Sie zu `View` → `Tool Windows` → `ViewPort Browser`

### Über Kontext-Menü
- Rechtsklick im Projekt-Explorer
- Wählen Sie "ViewPort Browser öffnen"

### Über Toolbar
- Klicken Sie auf den ViewPort-Button in der Toolbar

### URL eingeben
- Geben Sie eine URL in das Textfeld oben ein
- Drücken Sie Enter oder klicken Sie auf "Go"

### Navigation
- Verwenden Sie die Standard-Browser-Navigation (Links klicken, etc.)
- Der Browser unterstützt alle modernen Web-Features

## Technische Details

- **Java Version**: 21
- **Kotlin Version**: 2.1.0
- **IntelliJ Platform**: 2025.1
- **Browser Engine**: JCEF (Chromium Embedded Framework)

## Entwicklung

### Projektstruktur
```
src/main/kotlin/de/robin/alvarez/viewport/
├── ViewPortBrowser.kt          # Haupt-Browser-Komponente mit JCEF
├── ViewPortToolWindowFactory.kt # Tool Window Factory
└── actions/
    ├── OpenBrowserAction.kt    # Kontext-Menü Action
    └── ViewPortToolbarAction.kt # Toolbar Action
```

### Build
```bash
./gradlew buildPlugin
```

### Run
```bash
./gradlew runIde
```

## Hinweise

- Das Plugin verwendet JCEF für vollständige Browser-Funktionalität
- Moderne Webseiten mit CSS und JavaScript werden korrekt angezeigt
- Alle Standard-Browser-Features sind verfügbar
- Perfekt für Dokumentation, Stack Overflow, GitHub und andere Webseiten

## Lizenz

Dieses Projekt ist unter der MIT-Lizenz lizenziert.
