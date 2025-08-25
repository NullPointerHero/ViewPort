# ViewPort Plugin

An IntelliJ IDEA plugin that provides an embedded web browser.

## Features

- **Tool Window**: A tool window on the right side (like the DB Browser)
- **URL Input**: Text field at the top for URL input
- **Default URL**: Google.com is loaded by default
- **Context Menu**: Right-click menu to open the browser
- **Toolbar Button**: Button in the toolbar to open the browser
- **Full Browser**: JCEF (Chromium) for modern websites with CSS and JavaScript

## Installation

1. Build the plugin with Gradle:
   ```bash
   ./gradlew buildPlugin
   ```

2. Install the plugin in IntelliJ IDEA:
   - Go to `File` → `Settings` → `Plugins`
   - Click on the gear icon → `Install Plugin from Disk`
   - Select the generated `.zip` file from the `build/distributions` folder

## Usage

### Open Tool Window
- Click on the ViewPort icon in the right toolbar
- Or go to `View` → `Tool Windows` → `ViewPort Browser`

### Via Context Menu
- Right-click in the project explorer
- Select "ViewPort Browser öffnen"

### Via Toolbar
- Click on the ViewPort button in the toolbar

### Enter URL
- Enter a URL in the text field at the top
- Press Enter or click "Go"

### Navigation
- Use standard browser navigation (clicking links, etc.)
- The browser supports all modern web features

## Technical Details

- **Java Version**: 21
- **Kotlin Version**: 2.1.0
- **IntelliJ Platform**: 2025.1
- **Browser Engine**: JCEF (Chromium Embedded Framework)

## Development

### Project Structure
```
src/main/kotlin/de/robin/alvarez/viewport/
├── ViewPortBrowser.kt          # Main browser component with JCEF
├── ViewPortToolWindowFactory.kt # Tool Window Factory
└── actions/
    ├── OpenBrowserAction.kt    # Context menu action
    └── ViewPortToolbarAction.kt # Toolbar action
```

### Build
```bash
./gradlew buildPlugin
```

### Run
```bash
./gradlew runIde
```

## Notes

- The plugin uses JCEF for full browser functionality
- Modern websites with CSS and JavaScript are displayed correctly
- All standard browser features are available
- Perfect for documentation, Stack Overflow, GitHub and other websites

## License

This project is licensed under the MIT License.
