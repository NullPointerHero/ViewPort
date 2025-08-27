package de.robin.alvarez.viewport

import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
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
import java.awt.Point

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

class ViewPortBrowser() : JPanel() {
    
    private val urlField = JBTextField()
    private val browser: JBCefBrowser = JBCefBrowser()
    private val backButton = JButton(AllIcons.Actions.Back)
    private val forwardButton = JButton(AllIcons.Actions.Forward)
    private val reloadButton = JButton(AllIcons.Actions.Refresh)
    private val menuButton = JButton("⋮")
    private var urlFieldClickCount = 0
    private val urlHistory: MutableList<HistoryEntry> = mutableListOf()
    private val maxHistorySize = 100
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
    
    private val settingsPanel = JPanel(BorderLayout())
    private val backToBrowserFromSettingsButton = JButton("← Back to Browser")
    
    init {
        setupUI()
        setupBrowser()
        setupHistoryUI()
        setupSettingsUI()
        startUrlMonitoring()
    }
    
    private fun setupUI() {
        layout = BorderLayout()
        
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
        
        urlField.text = "https://www.google.com"
        
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
        
        add(urlPanel, BorderLayout.NORTH)
        add(browser.component, BorderLayout.CENTER)
        
        urlField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToUrl()
                    browser.component.requestFocusInWindow()
                }
            }
        })
        
        startUrlMonitoring()
        
        updateButtonStates()
    }
    
    private fun setupBrowser() {
        loadUrl("https://www.google.com")
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
        
        urlHistory.add(HistoryEntry(url, LocalDateTime.now()))
        
        if (urlHistory.size > maxHistorySize) {
            urlHistory.removeAt(0)
        }
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
        
        val reversedHistory = urlHistory.reversed()
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
            urlHistory.clear()
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
        
        val historyItem = JMenuItem("History")
        historyItem.addActionListener { showHistory() }
        menu.add(historyItem)
        
        val settingsItem = JMenuItem("Settings")
        settingsItem.addActionListener { showSettings() }
        menu.add(settingsItem)
        
        val rightPanel = menuButton.parent
        val urlPanel = rightPanel?.parent
        val buttonLocation = menuButton.location
        val rightPanelLocation = rightPanel?.location ?: Point(0, 0)
        val urlPanelLocation = urlPanel?.location ?: Point(0, 0)
        
        val totalX = urlPanelLocation.x + rightPanelLocation.x + buttonLocation.x
        val totalY = urlPanelLocation.y + rightPanelLocation.y + buttonLocation.y
        
        menu.show(this, totalX, totalY + menuButton.height)
    }

    private fun showSettings() {
        isHistoryMode = false
        
        removeAll()
        add(settingsPanel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    fun getCurrentUrl(): String {
        return urlField.text
    }
    
    fun getUrlHistory(): List<HistoryEntry> {
        return urlHistory.toList()
    }
    
    fun dispose() {
        urlCheckTimer.stop()
    }
}


