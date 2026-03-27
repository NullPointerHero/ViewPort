package de.robin.alvarez.viewport

import com.intellij.ui.jcef.JBCefBrowser
import java.util.UUID

class ViewPortBrowserTab(
    val id: String,
    val jbBrowser: JBCefBrowser,
) {
    var displayTitle: String = "New tab"

    companion object {
        fun create(): ViewPortBrowserTab {
            val jb = JBCefBrowser()
            return ViewPortBrowserTab(UUID.randomUUID().toString(), jb)
        }
    }

    fun dispose() {
        jbBrowser.dispose()
    }
}
