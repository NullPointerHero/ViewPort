package de.robin.alvarez.viewport

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*

class ViewPortBrowser(private val project: Project) : JPanel() {
    
    private val urlField = JBTextField()
    private val browser: JBCefBrowser = JBCefBrowser()
    private val backButton = JButton("←")
    
    // URL-Historie für Navigation (letzte 20 URLs)
    private val urlHistory = mutableListOf<String>()
    private val maxHistorySize = 20
    private var lastKnownUrl = ""
    private var urlCheckTimer: Timer? = null
    
    init {
        layout = BorderLayout()
        setupUI()
        setupBrowser()
        startUrlMonitoring()
    }
    
    private fun setupUI() {
        // URL-Eingabefeld oben
        val urlPanel = JPanel(BorderLayout())
        urlPanel.border = JBUI.Borders.empty(5)
        
        // Zurück-Button links
        val navPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        backButton.toolTipText = "Zurück"
        
        // Navigation implementieren
        backButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                goBack()
                // Fokus auf Browser setzen
                browser.component.requestFocusInWindow()
            }
        })
        
        navPanel.add(backButton)
        
        // URL-Feld
        urlField.text = "https://www.google.com"
        
        // Go-Button
        val goButton = JButton("Go")
        goButton.addActionListener { 
            navigateToUrl()
            // Fokus auf Browser setzen
            browser.component.requestFocusInWindow()
        }
        
        urlPanel.add(navPanel, BorderLayout.WEST)
        urlPanel.add(urlField, BorderLayout.CENTER)
        urlPanel.add(goButton, BorderLayout.EAST)
        
        add(urlPanel, BorderLayout.NORTH)
        
        // Browser-Panel
        val browserComponent = browser.component
        browserComponent.preferredSize = Dimension(800, 600)
        add(browserComponent, BorderLayout.CENTER)
        
        // Enter-Taste im URL-Feld
        urlField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToUrl()
                    // Fokus auf Browser setzen
                    browser.component.requestFocusInWindow()
                }
            }
        })
    }
    
    private fun setupBrowser() {
        // Standard-URL laden
        loadUrl("https://www.google.com")
    }
    
    private fun startUrlMonitoring() {
        // Timer, der alle 500ms die URL überprüft
        urlCheckTimer = Timer(500) {
            try {
                val currentUrl = browser.cefBrowser.url
                if (currentUrl != lastKnownUrl && currentUrl.isNotEmpty()) {
                    lastKnownUrl = currentUrl
                    urlField.text = currentUrl
                    addToHistory(currentUrl)
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
        urlCheckTimer?.start()
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
            
            // URL zur Historie hinzufügen
            addToHistory(urlString)
            
        } catch (e: Exception) {
            // Bei Fehlern zeigen wir eine einfache HTML-Seite
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
        // Füge neue URL am Ende hinzu
        urlHistory.add(url)
        
        // Begrenze die Historie auf maxHistorySize URLs
        if (urlHistory.size > maxHistorySize) {
            urlHistory.removeAt(0) // Entferne die älteste URL
        }
        
        updateBackButton()
    }
    
    private fun goBack() {
        if (urlHistory.size > 1) {
            // Entferne die aktuelle URL
            urlHistory.removeAt(urlHistory.size - 1)
            
            // Lade die vorherige URL
            val previousUrl = urlHistory.last()
            browser.loadURL(previousUrl)
            urlField.text = previousUrl
            lastKnownUrl = previousUrl
            
            updateBackButton()
        }
    }
    
    private fun updateBackButton() {
        // Button ist aktiviert, wenn es mehr als eine URL in der Historie gibt
        backButton.isEnabled = urlHistory.size > 1
    }
    
    fun getCurrentUrl(): String {
        return urlField.text
    }
    
    fun dispose() {
        urlCheckTimer?.stop()
    }
}
