package de.robin.alvarez.viewport

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import de.robin.alvarez.viewport.bookmarks.CreateBookmarkDialog
import de.robin.alvarez.viewport.bookmarks.ViewPortBookmarkService
import de.robin.alvarez.viewport.history.ViewPortHistoryService
import de.robin.alvarez.viewport.settings.DefaultSearchEngine
import de.robin.alvarez.viewport.settings.ViewPortSettingsService
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
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

private data class BookmarkListRow(val index: Int, val name: String, val url: String) {
    fun displayLine(): String =
        if (name.isNotEmpty()) "$name — $url" else url.ifEmpty { "(empty)" }
}

class ViewPortBrowser(private val project: Project) : JPanel() {

    private val bookmarkService = ViewPortBookmarkService.getInstance()
    private val historyService = ViewPortHistoryService.getInstance()
    private val settingsService = ViewPortSettingsService.getInstance()
    private lateinit var northStack: JPanel
    private lateinit var bookmarkScrollPane: JScrollPane
    private lateinit var bookmarksFlowPanel: BookmarkStripPanel

    private val urlField = JBTextField()
    private val browser: JBCefBrowser = JBCefBrowser()
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
    
    init {
        setupUI()
        setupBrowser()
        setupHistoryUI()
        setupBookmarksManagerUI()
        setupSettingsUI()
        startUrlMonitoring()
    }
    
    private fun setupUI() {
        layout = BorderLayout()

        northStack = JPanel(BorderLayout())

        val urlPanel = JPanel(BorderLayout())
        urlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        backButton.toolTipText = "Back"
        forwardButton.toolTipText = "Forward"
        reloadButton.toolTipText = "Reload"
        menuButton.toolTipText = "Options"
        
        backButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                goBack()
                browser.component.requestFocusInWindow()
            }
        })
        
        forwardButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                goForward()
                browser.component.requestFocusInWindow()
            }
        })
        
        reloadButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                reload()
                browser.component.requestFocusInWindow()
            }
        })
        
        menuButton.addActionListener {
            showOptionsMenu()
        }
        
        buttonPanel.add(backButton)
        if (showForwardButton) {
            buttonPanel.add(forwardButton)
        }
        buttonPanel.add(reloadButton)
        buttonPanel.add(menuButton)
        
        urlField.text = settingsService.homePageUrl()
        
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
        
        val goButton = JButton("Go")
        goButton.addActionListener { 
            navigateToUrl()
            browser.component.requestFocusInWindow()
        }
        
        urlPanel.add(buttonPanel, BorderLayout.WEST)
        urlPanel.add(urlField, BorderLayout.CENTER)

        val rightPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        rightPanel.add(goButton)
        rightPanel.add(menuButton)
        urlPanel.add(rightPanel, BorderLayout.EAST)

        northStack.add(urlPanel, BorderLayout.NORTH)

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
        val scrollbarThickness = UIManager.getInt("ScrollBar.width").takeIf { it > 0 } ?: 14
        val stripTotalHeight = stripRowHeight + scrollbarThickness + 4
        bookmarkScrollPane.preferredSize = Dimension(0, stripTotalHeight)
        bookmarkScrollPane.minimumSize = Dimension(0, stripTotalHeight)

        add(northStack, BorderLayout.NORTH)
        add(browser.component, BorderLayout.CENTER)

        refreshBookmarksBar()
        
        urlField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToUrl()
                    browser.component.requestFocusInWindow()
                }
            }
        })

        updateButtonStates()
    }

    private fun refreshBookmarksBar() {
        if (!::bookmarksFlowPanel.isInitialized || !::northStack.isInitialized || !::bookmarkScrollPane.isInitialized) return
        bookmarksFlowPanel.removeAll()

        val engine = settingsService.getSearchEngine()
        val homeButton = JButton(AllIcons.Nodes.HomeFolder)
        homeButton.toolTipText = "Home — ${engine.displayName}"
        homeButton.isFocusable = false
        homeButton.alignmentY = 0.5f
        homeButton.addActionListener {
            loadUrl(settingsService.homePageUrl())
            browser.component.requestFocusInWindow()
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
                browser.component.requestFocusInWindow()
            }
            bookmarksFlowPanel.add(chip)
            if (index < entries.lastIndex) {
                bookmarksFlowPanel.add(Box.createHorizontalStrut(6))
            }
        }
        bookmarksFlowPanel.revalidate()
        bookmarksFlowPanel.repaint()

        if (bookmarkScrollPane.parent != northStack) {
            northStack.add(bookmarkScrollPane, BorderLayout.SOUTH)
        }
        northStack.revalidate()
        northStack.repaint()
    }
    
    private fun setupBrowser() {
        loadUrl(settingsService.homePageUrl())
    }
    
    private fun startUrlMonitoring() {
        urlCheckTimer = Timer(500) {
            try {
                val currentUrl = browser.cefBrowser.url
                if (currentUrl != lastKnownUrl && currentUrl.isNotEmpty()) {
                    lastKnownUrl = currentUrl
                    urlField.text = currentUrl
                    addToHistory(currentUrl)
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
            browser.loadURL(urlString)
            urlField.text = urlString
            lastKnownUrl = urlString
            
            addToHistory(urlString)
            
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
            browser.loadHTML(errorHtml)
        }
    }
    
    private fun addToHistory(url: String) {
        if (!recordHistory) return
        historyService.addEntry(url)
    }
    
    private fun goBack() {
        if (browser.cefBrowser.canGoBack()) {
            browser.cefBrowser.goBack()
        }
    }
    
    private fun goForward() {
        if (browser.cefBrowser.canGoForward()) {
            browser.cefBrowser.goForward()
        }
    }
    
    private fun reload() {
        browser.cefBrowser.reload()
    }
    
    private fun updateButtonStates() {
        backButton.isEnabled = browser.cefBrowser.canGoBack()
        forwardButton.isEnabled = browser.cefBrowser.canGoForward()
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
        try {
            browser.openDevtools()
        } catch (e: Exception) {
            try {
                val devTools = browser.cefBrowser.devTools
                val devToolsBrowser = JBCefBrowser.createBuilder()
                    .setCefBrowser(devTools)
                    .setClient(browser.jbCefClient)
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
        
        browser.component.requestFocusInWindow()
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
            browser.cefBrowser.url?.takeIf { it.isNotEmpty() } ?: urlField.text.trim()
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
    
    fun dispose() {
        if (::urlCheckTimer.isInitialized) {
            urlCheckTimer.stop()
        }
    }
}


