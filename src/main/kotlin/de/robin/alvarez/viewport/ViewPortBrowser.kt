package de.robin.alvarez.viewport

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import de.robin.alvarez.viewport.bookmarks.CreateBookmarkDialog
import de.robin.alvarez.viewport.bookmarks.ViewPortBookmarkService
import de.robin.alvarez.viewport.history.ViewPortHistoryService
import de.robin.alvarez.viewport.settings.DefaultSearchEngine
import de.robin.alvarez.viewport.settings.ViewPortSettingsService
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import com.intellij.icons.AllIcons
import java.awt.Font
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class HistoryEntry(
    val url: String,
    val timestamp: LocalDateTime,
    val favicon: String? = null
) {
    fun getDisplayText(): String {
        val time = timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))
        val faviconIcon = favicon ?: "🌐"
        return "$faviconIcon $time - $url"
    }
}

/** Horizontal bookmark row: viewport height follows strip only; no vertical scrollbar. */
private class BookmarkStripPanel : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        border = BorderFactory.createEmptyBorder(2, 0, 4, 0)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableTracksViewportWidth(): Boolean = false

    override fun getScrollableTracksViewportHeight(): Boolean = true

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.HORIZONTAL) visibleRect.width.coerceAtLeast(1)
        else visibleRect.height.coerceAtLeast(1)

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.HORIZONTAL) 48 else 16
}

/** Tab chip with rounded background and vertically centered label + close control. */
private class RoundedTabCell(
    private val fillColor: Color,
    private val cornerArc: Int,
) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(5, 8, 5, 6)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = fillColor
            val w = width
            val h = height
            if (w > 0 && h > 0) {
                g2.fillRoundRect(0, 0, w - 1, h - 1, cornerArc, cornerArc)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

private data class BookmarkListRow(val index: Int, val name: String, val url: String) {
    fun displayLine(): String =
        if (name.isNotEmpty()) "$name — $url" else url.ifEmpty { "(empty)" }
}

class ViewPortBrowser(private val project: Project) : JPanel(), Disposable {

    private val bookmarkService = ViewPortBookmarkService.getInstance()
    private val historyService = ViewPortHistoryService.getInstance()
    private val settingsService = ViewPortSettingsService.getInstance()
    private val tabs = mutableListOf<ViewPortBrowserTab>()
    private var selectedTabIndex = 0
    private lateinit var northStack: JPanel
    private lateinit var northColumn: JPanel
    private lateinit var bookmarkScrollPane: JScrollPane
    private lateinit var bookmarksFlowPanel: BookmarkStripPanel
    private lateinit var browserCardPanel: JPanel
    private lateinit var browserCardLayout: CardLayout
    private lateinit var tabStripPanel: BookmarkStripPanel
    private lateinit var tabScrollPane: JScrollPane
    /** Nur Tab-Zeile; Zusatz für horizontale Scrollbar kommt nur bei sichtbarer Scrollbar dazu. */
    private var tabStripRowHeightPx = 0
    private var tabScrollbarReservePx = 0
    private lateinit var newTabButton: JButton

    private val urlField = JBTextField()
    private val backButton = JButton(AllIcons.Actions.Back)
    private val forwardButton = JButton(AllIcons.Actions.Forward)
    private val reloadButton = JButton(AllIcons.Actions.Refresh)
    private val menuButton = JButton("⋮")
    private var urlFieldClickCount = 0
    private var lastKnownUrl = ""
    private lateinit var urlCheckTimer: Timer
    private var isHistoryMode = false
    
    private var recordHistory = true
    private var showForwardButton = true
    
    private val historyPanel = JPanel(BorderLayout())
    private val historyList = JList<HistoryEntry>()
    private val historyScrollPane = JScrollPane(historyList)
    private val backToBrowserButton = JButton("← Back to Browser")
    private val clearHistoryButton = JButton("Clear History")

    private val bookmarksManagerPanel = JPanel(BorderLayout())
    private val bookmarkManagerList = JList<BookmarkListRow>()
    private val bookmarkManagerScrollPane = JScrollPane(bookmarkManagerList)
    private val backToBrowserFromBookmarksButton = JButton("← Back to Browser")
    private val deleteBookmarkButton = JButton("Delete")
    private val clearAllBookmarksButton = JButton("Clear all")
    
    private val settingsPanel = JPanel(BorderLayout())
    private val backToBrowserFromSettingsButton = JButton("← Back to Browser")
    private lateinit var searchEngineCombo: JComboBox<DefaultSearchEngine>
    private lateinit var showTabBarToggle: JCheckBox
    private lateinit var showBookmarksBarToggle: JCheckBox
    private var urlFieldListenersAttached = false
    private var mainToolbarListenersAttached = false

    init {
        tabs.add(ViewPortBrowserTab.create())
        setupUI()
        loadUrl(settingsService.homePageUrl())
        setupHistoryUI()
        setupBookmarksManagerUI()
        setupSettingsUI()
        startUrlMonitoring()
    }

    private fun activeBrowser(): JBCefBrowser =
        tabs[selectedTabIndex.coerceIn(0, tabs.lastIndex)].jbBrowser
    
    private fun setupUI() {
        layout = BorderLayout()

        northStack = JPanel(BorderLayout())

        val showTabs = settingsService.isShowTabBar()
        val showBm = settingsService.isShowBookmarksBar()

        northColumn = JPanel(BorderLayout())

        val scrollbarThickness = UIManager.getInt("ScrollBar.width").takeIf { it > 0 } ?: JBUI.scale(14)

        tabStripPanel = BookmarkStripPanel()
        tabScrollPane = JScrollPane(
            tabStripPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
        )
        tabScrollPane.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
        tabStripRowHeightPx = JBUI.scale(34)
        tabScrollbarReservePx = scrollbarThickness + 4
        tabScrollPane.preferredSize = Dimension(0, tabStripRowHeightPx)
        tabScrollPane.minimumSize = Dimension(80, tabStripRowHeightPx)

        val tabHsbSync = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                SwingUtilities.invokeLater { syncTabStripScrollPaneHeight() }
            }

            override fun componentShown(e: ComponentEvent) {
                SwingUtilities.invokeLater { syncTabStripScrollPaneHeight() }
            }

            override fun componentHidden(e: ComponentEvent) {
                SwingUtilities.invokeLater { syncTabStripScrollPaneHeight() }
            }
        }
        tabScrollPane.horizontalScrollBar.addComponentListener(tabHsbSync)
        tabScrollPane.viewport.addComponentListener(tabHsbSync)

        newTabButton = JButton(AllIcons.General.Add)
        newTabButton.toolTipText = "New tab"
        newTabButton.addActionListener { addNewTab() }
        menuButton.toolTipText = "Options"

        if (showTabs) {
            val headerPanel = JPanel(BorderLayout())
            headerPanel.border = JBUI.Borders.empty(4, 8, 4, 8)
            headerPanel.add(tabScrollPane, BorderLayout.CENTER)
            val headerEast = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
            headerEast.add(newTabButton)
            headerEast.add(menuButton)
            headerPanel.add(headerEast, BorderLayout.EAST)
            northColumn.add(headerPanel, BorderLayout.NORTH)
        }

        val urlPanel = JPanel(BorderLayout())
        urlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        backButton.toolTipText = "Back"
        forwardButton.toolTipText = "Forward"
        reloadButton.toolTipText = "Reload"

        if (!mainToolbarListenersAttached) {
            mainToolbarListenersAttached = true
            backButton.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent) {
                    goBack()
                    activeBrowser().component.requestFocusInWindow()
                }
            })

            forwardButton.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent) {
                    goForward()
                    activeBrowser().component.requestFocusInWindow()
                }
            })

            reloadButton.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent) {
                    reload()
                    activeBrowser().component.requestFocusInWindow()
                }
            })

            menuButton.addActionListener { showOptionsMenu() }
        }

        buttonPanel.add(backButton)
        if (showForwardButton) {
            buttonPanel.add(forwardButton)
        }
        buttonPanel.add(reloadButton)

        syncUrlFieldFromActiveTab()

        attachUrlFieldListenersOnce()

        val goButton = JButton("Go")
        goButton.addActionListener {
            navigateToUrl()
            activeBrowser().component.requestFocusInWindow()
        }

        urlPanel.add(buttonPanel, BorderLayout.WEST)
        urlPanel.add(urlField, BorderLayout.CENTER)

        val rightPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        rightPanel.add(goButton)
        if (!showTabs) {
            rightPanel.add(newTabButton)
            rightPanel.add(menuButton)
        }
        urlPanel.add(rightPanel, BorderLayout.EAST)

        if (showTabs) {
            northColumn.add(urlPanel, BorderLayout.CENTER)
        } else {
            northColumn.add(urlPanel, BorderLayout.NORTH)
        }

        bookmarksFlowPanel = BookmarkStripPanel()
        bookmarkScrollPane = JScrollPane(
            bookmarksFlowPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
        )
        bookmarkScrollPane.border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
        bookmarkScrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        bookmarkScrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        val stripRowHeight = 34
        val stripTotalHeight = stripRowHeight + scrollbarThickness + 4
        bookmarkScrollPane.preferredSize = Dimension(0, stripTotalHeight)
        bookmarkScrollPane.minimumSize = Dimension(0, stripTotalHeight)

        if (showBm) {
            northColumn.add(bookmarkScrollPane, BorderLayout.SOUTH)
        }

        northStack.add(northColumn, BorderLayout.NORTH)

        browserCardLayout = CardLayout()
        browserCardPanel = JPanel(browserCardLayout)
        tabs.forEach { tab ->
            browserCardPanel.add(tab.jbBrowser.component, tab.id)
        }
        if (tabs.isNotEmpty()) {
            browserCardLayout.show(browserCardPanel, tabs[selectedTabIndex.coerceIn(0, tabs.lastIndex)].id)
        }

        add(northStack, BorderLayout.NORTH)
        add(browserCardPanel, BorderLayout.CENTER)

        refreshTabStrip()
        refreshBookmarksBar()

        updateButtonStates()
        SwingUtilities.invokeLater { syncTabStripScrollPaneHeight() }
    }

    /** Reserviert Zusatzhöhe für die horizontale Tab-Scrollbar nur, wenn sie sichtbar ist. */
    private fun syncTabStripScrollPaneHeight() {
        if (!::tabScrollPane.isInitialized || tabStripRowHeightPx <= 0) return
        if (!settingsService.isShowTabBar()) return
        val hsb = tabScrollPane.horizontalScrollBar
        val total = tabStripRowHeightPx + if (hsb.isVisible) tabScrollbarReservePx else 0
        val cur = tabScrollPane.preferredSize.height
        if (cur == total) return
        tabScrollPane.preferredSize = Dimension(0, total)
        tabScrollPane.minimumSize = Dimension(80, total)
        tabScrollPane.revalidate()
        tabScrollPane.parent?.revalidate()
    }

    private fun attachUrlFieldListenersOnce() {
        if (urlFieldListenersAttached) return
        urlFieldListenersAttached = true

        urlField.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                urlFieldClickCount++
                if (urlFieldClickCount == 1) {
                    urlField.selectAll()
                } else {
                    urlFieldClickCount = 0
                }
            }
        })

        urlField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                if (urlFieldClickCount == 0) {
                    urlField.selectAll()
                }
            }
        })

        urlField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToUrl()
                    activeBrowser().component.requestFocusInWindow()
                }
            }
        })
    }

    private fun syncUrlFieldFromActiveTab() {
        if (tabs.isEmpty()) return
        val url = try {
            activeBrowser().cefBrowser.url?.takeIf { it.isNotEmpty() } ?: ""
        } catch (_: Exception) {
            ""
        }
        lastKnownUrl = url
        urlField.text = url.ifEmpty { settingsService.homePageUrl() }
    }

    private fun refreshTabStrip() {
        if (!settingsService.isShowTabBar() || !::tabStripPanel.isInitialized) return
        tabStripPanel.removeAll()
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            val bg =
                if (selected) UIManager.getColor("TabbedPane.selected") ?: Color(57, 110, 175)
                else (UIManager.getColor("Panel.background") ?: Color.LIGHT_GRAY)
            val fg = foregroundForTabBackground(bg)

            val arc = JBUI.scale(10).coerceAtLeast(8)
            val cell = RoundedTabCell(bg, arc)

            val row = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
            row.isOpaque = false

            val titleLabel = JLabel(tab.displayTitle)
            titleLabel.foreground = fg
            titleLabel.isOpaque = false
            titleLabel.alignmentY = Component.CENTER_ALIGNMENT
            titleLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            titleLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    selectTab(index)
                }
            })
            row.add(titleLabel)

            // JLabel statt JButton: gleiche vertikale Metrik wie der Titel (× wirkt bei JButton oft zu tief).
            val closeLabel = JLabel("×")
            closeLabel.font = closeLabel.font.deriveFont(Font.BOLD, 12f)
            closeLabel.foreground = fg
            closeLabel.isOpaque = false
            closeLabel.verticalAlignment = SwingConstants.CENTER
            closeLabel.horizontalAlignment = SwingConstants.CENTER
            val rowH = titleLabel.preferredSize.height.coerceAtLeast(JBUI.scale(16))
            closeLabel.preferredSize = Dimension(JBUI.scale(18), rowH)
            closeLabel.minimumSize = closeLabel.preferredSize
            closeLabel.maximumSize = Dimension(JBUI.scale(22), rowH)
            closeLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            closeLabel.alignmentY = Component.CENTER_ALIGNMENT
            closeLabel.toolTipText = "Close tab"
            closeLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    closeTabAt(index)
                }
            })
            row.add(closeLabel)

            row.alignmentX = Component.LEFT_ALIGNMENT
            cell.add(Box.createVerticalGlue())
            cell.add(row)
            cell.add(Box.createVerticalGlue())

            tabStripPanel.add(cell)
            if (index < tabs.lastIndex) {
                tabStripPanel.add(Box.createHorizontalStrut(4))
            }
        }
        tabStripPanel.revalidate()
        tabStripPanel.repaint()
        SwingUtilities.invokeLater { syncTabStripScrollPaneHeight() }
    }

    private fun selectTab(index: Int) {
        if (index !in tabs.indices) return
        selectedTabIndex = index
        browserCardLayout.show(browserCardPanel, tabs[index].id)
        syncUrlFieldFromActiveTab()
        refreshTabStrip()
        updateButtonStates()
        activeBrowser().component.requestFocusInWindow()
    }

    private fun addNewTab() {
        val tab = ViewPortBrowserTab.create()
        tabs.add(tab)
        browserCardPanel.add(tab.jbBrowser.component, tab.id)
        selectedTabIndex = tabs.lastIndex
        browserCardLayout.show(browserCardPanel, tab.id)
        refreshTabStrip()
        loadUrl(settingsService.homePageUrl())
        updateButtonStates()
        activeBrowser().component.requestFocusInWindow()
    }

    private fun closeTabAt(index: Int) {
        if (index !in tabs.indices) return
        if (tabs.size == 1) {
            loadUrl(settingsService.homePageUrl())
            return
        }
        val removed = tabs.removeAt(index)
        browserCardPanel.remove(removed.jbBrowser.component)
        removed.dispose()
        if (selectedTabIndex >= tabs.size) {
            selectedTabIndex = tabs.lastIndex
        } else if (index < selectedTabIndex) {
            selectedTabIndex--
        }
        browserCardLayout.show(browserCardPanel, tabs[selectedTabIndex].id)
        browserCardPanel.revalidate()
        syncUrlFieldFromActiveTab()
        refreshTabStrip()
        updateButtonStates()
        activeBrowser().component.requestFocusInWindow()
    }

    private fun updateActiveTabTitleFromUrl(url: String) {
        if (tabs.isEmpty() || !::tabStripPanel.isInitialized) return
        val tab = tabs[selectedTabIndex.coerceIn(0, tabs.lastIndex)]
        tab.displayTitle = titleFromUrl(url)
        refreshTabStrip()
    }

    private fun titleFromUrl(url: String): String {
        if (url.isEmpty()) return "New tab"
        return try {
            val host = URI(url).host
            if (host.isNullOrBlank()) url.take(24) else host.take(28)
        } catch (_: Exception) {
            url.take(24)
        }
    }

    /** Readable label/close color on top of the given tab background (light and dark themes). */
    private fun foregroundForTabBackground(bg: Color): Color {
        val r = bg.red / 255.0
        val g = bg.green / 255.0
        val b = bg.blue / 255.0
        val lum = 0.299 * r + 0.587 * g + 0.114 * b
        return if (lum > 0.55) Color(35, 35, 35) else Color.WHITE
    }

    private fun refreshBookmarksBar() {
        if (!::bookmarksFlowPanel.isInitialized || !::northStack.isInitialized || !::bookmarkScrollPane.isInitialized) return
        if (!settingsService.isShowBookmarksBar()) {
            bookmarkScrollPane.parent?.remove(bookmarkScrollPane)
            if (::northColumn.isInitialized) {
                northColumn.revalidate()
                northColumn.repaint()
            }
            return
        }

        bookmarksFlowPanel.removeAll()

        val engine = settingsService.getSearchEngine()
        val homeButton = JButton(AllIcons.Nodes.HomeFolder)
        homeButton.toolTipText = "Home — ${engine.displayName}"
        homeButton.isFocusable = false
        homeButton.alignmentY = 0.5f
        homeButton.addActionListener {
            loadUrl(settingsService.homePageUrl())
            activeBrowser().component.requestFocusInWindow()
        }
        bookmarksFlowPanel.add(homeButton)

        val entries = bookmarkService.getBookmarks()
        if (entries.isNotEmpty()) {
            bookmarksFlowPanel.add(Box.createHorizontalStrut(6))
        }
        for ((index, entry) in entries.withIndex()) {
            val label = entry.name?.takeIf { it.isNotEmpty() } ?: (entry.url ?: "")
            val chip = JButton(if (label.isNotEmpty()) label else "Bookmark")
            chip.toolTipText = entry.url.orEmpty()
            chip.isFocusable = false
            chip.alignmentY = 0.5f
            chip.addActionListener {
                val url = entry.url?.takeIf { it.isNotEmpty() } ?: return@addActionListener
                loadUrl(url)
                activeBrowser().component.requestFocusInWindow()
            }
            bookmarksFlowPanel.add(chip)
            if (index < entries.lastIndex) {
                bookmarksFlowPanel.add(Box.createHorizontalStrut(6))
            }
        }
        bookmarksFlowPanel.revalidate()
        bookmarksFlowPanel.repaint()

        if (::northColumn.isInitialized && bookmarkScrollPane.parent != northColumn) {
            northColumn.add(bookmarkScrollPane, BorderLayout.SOUTH)
        }
        if (::northColumn.isInitialized) {
            northColumn.revalidate()
            northColumn.repaint()
        }
    }
    
    private fun startUrlMonitoring() {
        urlCheckTimer = Timer(500) {
            try {
                if (tabs.isNotEmpty()) {
                    val currentUrl = activeBrowser().cefBrowser.url
                    if (currentUrl != lastKnownUrl && currentUrl.isNotEmpty()) {
                        lastKnownUrl = currentUrl
                        urlField.text = currentUrl
                        addToHistory(currentUrl)
                        updateActiveTabTitleFromUrl(currentUrl)
                    }
                }

                updateButtonStates()
            } catch (e: Exception) {
            }
        }
        urlCheckTimer.start()
    }
    
    private fun navigateToUrl() {
        val url = urlField.text.trim()
        if (url.isNotEmpty()) {
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            
            loadUrl(fullUrl)
        }
    }
    
    private fun loadUrl(urlString: String) {
        try {
            activeBrowser().loadURL(urlString)
            urlField.text = urlString
            lastKnownUrl = urlString

            addToHistory(urlString)
            updateActiveTabTitleFromUrl(urlString)
        } catch (e: Exception) {
            val errorHtml = """
                <html>
                <head><title>ViewPort Browser - Fehler</title></head>
                <body style="font-family: Arial, sans-serif; padding: 20px;">
                    <h2>ViewPort Browser</h2>
                    <p>Fehler beim Laden der URL: $urlString</p>
                    <p>Fehler: ${e.message}</p>
                    <p>Bitte überprüfen Sie die URL und versuchen Sie es erneut.</p>
                </body>
                </html>
            """.trimIndent()
            activeBrowser().loadHTML(errorHtml)
        }
    }
    
    private fun addToHistory(url: String) {
        if (!recordHistory) return
        historyService.addEntry(url)
    }
    
    private fun goBack() {
        if (activeBrowser().cefBrowser.canGoBack()) {
            activeBrowser().cefBrowser.goBack()
        }
    }

    private fun goForward() {
        if (activeBrowser().cefBrowser.canGoForward()) {
            activeBrowser().cefBrowser.goForward()
        }
    }

    private fun reload() {
        activeBrowser().cefBrowser.reload()
    }

    private fun updateButtonStates() {
        if (tabs.isEmpty()) return
        backButton.isEnabled = activeBrowser().cefBrowser.canGoBack()
        forwardButton.isEnabled = activeBrowser().cefBrowser.canGoForward()
    }
    
    private fun setupHistoryUI() {
        historyPanel.layout = BorderLayout()
        historyPanel.border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
        
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 0, 15, 0)
        
        val titleLabel = JLabel("History")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        headerPanel.add(titleLabel, BorderLayout.CENTER)
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        
        clearHistoryButton.addActionListener {
            clearHistory()
        }
        
        backToBrowserButton.addActionListener {
            showBrowser()
        }
        
        buttonPanel.add(clearHistoryButton)
        buttonPanel.add(backToBrowserButton)
        headerPanel.add(buttonPanel, BorderLayout.EAST)
        
        historyPanel.add(headerPanel, BorderLayout.NORTH)
        
        historyList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        historyList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                
                if (component is JLabel && value is HistoryEntry) {
                    component.text = value.getDisplayText()
                    component.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
                    
                    if (isSelected) {
                        component.foreground = Color.WHITE
                        component.background = Color.BLUE
                    } else {
                        component.foreground = Color.WHITE
                        component.background = Color(0, 0, 0, 0)
                        component.isOpaque = false
                    }
                }
                
                return component
            }
        }
        
        historyList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && historyList.selectedValue != null) {
                val selectedUrl = historyList.selectedValue
                loadUrl(selectedUrl.url)
                showBrowser()
            }
        }
        
        historyList.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                historyList.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
            
            override fun mouseExited(e: MouseEvent) {
                historyList.cursor = Cursor.getDefaultCursor()
            }
        })
        
        historyScrollPane.preferredSize = Dimension(600, 400)
        historyScrollPane.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        historyPanel.add(historyScrollPane, BorderLayout.CENTER)
    }

    private fun setupBookmarksManagerUI() {
        bookmarksManagerPanel.layout = BorderLayout()
        bookmarksManagerPanel.border = BorderFactory.createEmptyBorder(10, 15, 10, 15)

        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 0, 15, 0)

        val titleLabel = JLabel("Bookmarks")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))

        deleteBookmarkButton.toolTipText = "Remove selected bookmark"
        deleteBookmarkButton.addActionListener { deleteSelectedBookmark() }

        clearAllBookmarksButton.addActionListener { clearAllBookmarksConfirmed() }

        backToBrowserFromBookmarksButton.addActionListener { showBrowser() }

        buttonPanel.add(deleteBookmarkButton)
        buttonPanel.add(clearAllBookmarksButton)
        buttonPanel.add(backToBrowserFromBookmarksButton)
        headerPanel.add(buttonPanel, BorderLayout.EAST)

        bookmarksManagerPanel.add(headerPanel, BorderLayout.NORTH)

        bookmarkManagerList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        bookmarkManagerList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (c is JLabel && value is BookmarkListRow) {
                    c.text = value.displayLine()
                    c.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
                    if (isSelected) {
                        c.foreground = Color.WHITE
                        c.background = Color.BLUE
                        c.isOpaque = true
                    } else {
                        c.foreground = Color.WHITE
                        c.background = Color(0, 0, 0, 0)
                        c.isOpaque = false
                    }
                }
                return c
            }
        }

        deleteBookmarkButton.isEnabled = false
        bookmarkManagerList.addListSelectionListener {
            deleteBookmarkButton.isEnabled = bookmarkManagerList.selectedValue != null
        }

        bookmarkManagerList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val idx = bookmarkManagerList.locationToIndex(e.point)
                if (idx < 0) return
                bookmarkManagerList.selectedIndex = idx
                val row = bookmarkManagerList.selectedValue ?: return
                val url = row.url.takeIf { it.isNotEmpty() } ?: return
                loadUrl(url)
                showBrowser()
            }

            override fun mouseEntered(e: MouseEvent) {
                bookmarkManagerList.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }

            override fun mouseExited(e: MouseEvent) {
                bookmarkManagerList.cursor = Cursor.getDefaultCursor()
            }
        })

        val deleteKey = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
        bookmarkManagerList.getInputMap(JComponent.WHEN_FOCUSED).put(deleteKey, "deleteBookmark")
        bookmarkManagerList.actionMap.put("deleteBookmark", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                deleteSelectedBookmark()
            }
        })

        bookmarkManagerScrollPane.preferredSize = Dimension(600, 400)
        bookmarkManagerScrollPane.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        bookmarksManagerPanel.add(bookmarkManagerScrollPane, BorderLayout.CENTER)
    }

    private fun refreshBookmarkManagerList() {
        val model = DefaultListModel<BookmarkListRow>()
        bookmarkService.getBookmarks().forEachIndexed { index, bean ->
            model.addElement(
                BookmarkListRow(
                    index,
                    bean.name.orEmpty(),
                    bean.url.orEmpty(),
                ),
            )
        }
        bookmarkManagerList.model = model
        deleteBookmarkButton.isEnabled = bookmarkManagerList.selectedValue != null
    }

    private fun deleteSelectedBookmark() {
        val row = bookmarkManagerList.selectedValue ?: return
        bookmarkService.removeBookmarkAt(row.index)
        refreshBookmarkManagerList()
        refreshBookmarksBar()
    }

    private fun clearAllBookmarksConfirmed() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "Remove all bookmarks?",
            "Clear bookmarks",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
        )
        if (result == JOptionPane.YES_OPTION) {
            bookmarkService.clearAllBookmarks()
            refreshBookmarkManagerList()
            refreshBookmarksBar()
        }
    }

    private fun showBookmarksManager() {
        isHistoryMode = false
        refreshBookmarkManagerList()
        removeAll()
        add(bookmarksManagerPanel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    private fun setupSettingsUI() {
        settingsPanel.layout = BorderLayout()
        settingsPanel.border = BorderFactory.createEmptyBorder(5, 15, 5, 15)
        
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        
        val titleLabel = JLabel("Settings")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        headerPanel.add(titleLabel, BorderLayout.CENTER)
        
        backToBrowserFromSettingsButton.addActionListener {
            showBrowser()
        }
        headerPanel.add(backToBrowserFromSettingsButton, BorderLayout.EAST)
        
        settingsPanel.add(headerPanel, BorderLayout.NORTH)
        
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        val searchEnginePanel = JPanel(BorderLayout())
        searchEnginePanel.maximumSize = Dimension(Int.MAX_VALUE, 34)
        searchEnginePanel.preferredSize = Dimension(Int.MAX_VALUE, 34)

        val searchEngineLabel = JLabel("Default search engine (home page)")
        searchEngineLabel.font = searchEngineLabel.font.deriveFont(Font.PLAIN, 14f)
        searchEnginePanel.add(searchEngineLabel, BorderLayout.CENTER)

        val searchEngineComboWrapper = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        searchEngineCombo = JComboBox(DefaultSearchEngine.entries.toTypedArray())
        searchEngineCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (c is JLabel && value is DefaultSearchEngine) {
                    c.text = value.displayName
                }
                return c
            }
        }
        searchEngineCombo.selectedItem = settingsService.getSearchEngine()
        searchEngineCombo.addActionListener {
            val sel = searchEngineCombo.selectedItem as? DefaultSearchEngine ?: return@addActionListener
            settingsService.setSearchEngine(sel)
        }
        searchEngineComboWrapper.add(searchEngineCombo)
        searchEnginePanel.add(searchEngineComboWrapper, BorderLayout.EAST)

        contentPanel.add(searchEnginePanel)
        contentPanel.add(Box.createVerticalStrut(8))
        
        val historyPanel = JPanel(BorderLayout())
        historyPanel.maximumSize = Dimension(Int.MAX_VALUE, 30)
        historyPanel.preferredSize = Dimension(Int.MAX_VALUE, 30)
        
        val historyLabel = JLabel("Record History")
        historyLabel.font = historyLabel.font.deriveFont(Font.PLAIN, 14f)
        historyPanel.add(historyLabel, BorderLayout.CENTER)
        
        val historyToggle = JCheckBox()
        historyToggle.isSelected = recordHistory
        historyToggle.addActionListener {
            recordHistory = historyToggle.isSelected
        }
        historyPanel.add(historyToggle, BorderLayout.EAST)
        
        contentPanel.add(historyPanel)
        contentPanel.add(Box.createVerticalStrut(8))
        
        val forwardPanel = JPanel(BorderLayout())
        forwardPanel.maximumSize = Dimension(Int.MAX_VALUE, 30)
        forwardPanel.preferredSize = Dimension(Int.MAX_VALUE, 30)
        
        val forwardLabel = JLabel("Show Forward Button")
        forwardLabel.font = forwardLabel.font.deriveFont(Font.PLAIN, 14f)
        forwardPanel.add(forwardLabel, BorderLayout.CENTER)
        
        val forwardToggle = JCheckBox()
        forwardToggle.isSelected = showForwardButton
        forwardToggle.addActionListener {
            showForwardButton = forwardToggle.isSelected
            if (!isHistoryMode) {
                showBrowser()
            }
        }
        forwardPanel.add(forwardToggle, BorderLayout.EAST)
        
        contentPanel.add(forwardPanel)
        contentPanel.add(Box.createVerticalStrut(8))

        val tabBarVisibilityPanel = JPanel(BorderLayout())
        tabBarVisibilityPanel.maximumSize = Dimension(Int.MAX_VALUE, 30)
        tabBarVisibilityPanel.preferredSize = Dimension(Int.MAX_VALUE, 30)
        val tabBarLabel = JLabel("Show tab bar")
        tabBarLabel.font = tabBarLabel.font.deriveFont(Font.PLAIN, 14f)
        tabBarVisibilityPanel.add(tabBarLabel, BorderLayout.CENTER)
        showTabBarToggle = JCheckBox()
        showTabBarToggle.isSelected = settingsService.isShowTabBar()
        showTabBarToggle.addActionListener {
            settingsService.setShowTabBar(showTabBarToggle.isSelected)
            if (!isHistoryMode) {
                showBrowser()
            }
        }
        tabBarVisibilityPanel.add(showTabBarToggle, BorderLayout.EAST)
        contentPanel.add(tabBarVisibilityPanel)
        contentPanel.add(Box.createVerticalStrut(8))

        val bookmarksVisibilityPanel = JPanel(BorderLayout())
        bookmarksVisibilityPanel.maximumSize = Dimension(Int.MAX_VALUE, 30)
        bookmarksVisibilityPanel.preferredSize = Dimension(Int.MAX_VALUE, 30)
        val bookmarksBarLabel = JLabel("Show bookmarks bar")
        bookmarksBarLabel.font = bookmarksBarLabel.font.deriveFont(Font.PLAIN, 14f)
        bookmarksVisibilityPanel.add(bookmarksBarLabel, BorderLayout.CENTER)
        showBookmarksBarToggle = JCheckBox()
        showBookmarksBarToggle.isSelected = settingsService.isShowBookmarksBar()
        showBookmarksBarToggle.addActionListener {
            settingsService.setShowBookmarksBar(showBookmarksBarToggle.isSelected)
            if (!isHistoryMode) {
                showBrowser()
            }
        }
        bookmarksVisibilityPanel.add(showBookmarksBarToggle, BorderLayout.EAST)
        contentPanel.add(bookmarksVisibilityPanel)

        contentPanel.add(Box.createVerticalGlue())

        settingsPanel.add(contentPanel, BorderLayout.CENTER)
    }
    
    private fun showHistory() {
        isHistoryMode = true

        val reversedHistory = historyService.getEntries().asReversed()
        val listModel = DefaultListModel<HistoryEntry>()
        reversedHistory.forEach { entry ->
            listModel.addElement(entry)
        }
        historyList.model = listModel

        removeAll()
        add(historyPanel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    private fun clearHistory() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to clear all history?",
            "Clear History",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        
        if (result == JOptionPane.YES_OPTION) {
            historyService.clear()
            val listModel = DefaultListModel<HistoryEntry>()
            historyList.model = listModel
        }
    }
    
    private fun openDevTools() {
        val jb = activeBrowser()
        try {
            jb.openDevtools()
        } catch (e: Exception) {
            try {
                val devTools = jb.cefBrowser.devTools
                val devToolsBrowser = JBCefBrowser.createBuilder()
                    .setCefBrowser(devTools)
                    .setClient(jb.jbCefClient)
                    .build()
                
                val frame = JFrame("ViewPort DevTools")
                frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                frame.size = Dimension(1200, 800)
                frame.setLocationRelativeTo(null)
                frame.add(devToolsBrowser.component)
                frame.isVisible = true
            } catch (ex: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Could not open DevTools: ${ex.message}",
                    "DevTools Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
    
    private fun showBrowser() {
        isHistoryMode = false
        
        removeAll()
        setupUI()
        revalidate()
        repaint()
        
        activeBrowser().component.requestFocusInWindow()
    }

    private fun showOptionsMenu() {
        val menu = JPopupMenu()
        
        val devToolsItem = JMenuItem("DevTools")
        devToolsItem.addActionListener { openDevTools() }
        menu.add(devToolsItem)

        val createBookmarkItem = JMenuItem("Create bookmark")
        createBookmarkItem.addActionListener { openCreateBookmarkFlow() }
        menu.add(createBookmarkItem)

        val bookmarksItem = JMenuItem("Bookmarks")
        bookmarksItem.addActionListener { showBookmarksManager() }
        menu.add(bookmarksItem)

        val historyItem = JMenuItem("History")
        historyItem.addActionListener { showHistory() }
        menu.add(historyItem)
        
        val settingsItem = JMenuItem("Settings")
        settingsItem.addActionListener { showSettings() }
        menu.add(settingsItem)

        menu.show(menuButton, 0, menuButton.height)
    }

    private fun openCreateBookmarkFlow() {
        val url = try {
            activeBrowser().cefBrowser.url?.takeIf { it.isNotEmpty() } ?: urlField.text.trim()
        } catch (_: Exception) {
            urlField.text.trim()
        }.trim()
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No URL to bookmark.",
                "Create bookmark",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        val dialog = CreateBookmarkDialog(project, url)
        if (!dialog.showAndGet()) return
        val name = dialog.bookmarkName()
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a bookmark name.",
                "Create bookmark",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        bookmarkService.addBookmark(name, url)
        refreshBookmarksBar()
    }

    private fun showSettings() {
        isHistoryMode = false

        if (::searchEngineCombo.isInitialized) {
            searchEngineCombo.selectedItem = settingsService.getSearchEngine()
        }
        if (::showTabBarToggle.isInitialized) {
            showTabBarToggle.isSelected = settingsService.isShowTabBar()
            showBookmarksBarToggle.isSelected = settingsService.isShowBookmarksBar()
        }

        removeAll()
        add(settingsPanel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    fun getCurrentUrl(): String {
        return urlField.text
    }
    
    fun getUrlHistory(): List<HistoryEntry> {
        return historyService.getEntries()
    }
    
    override fun dispose() {
        if (::urlCheckTimer.isInitialized) {
            urlCheckTimer.stop()
        }
        tabs.toList().forEach { tab ->
            try {
                tab.dispose()
            } catch (_: Exception) {
            }
        }
        tabs.clear()
    }
}


