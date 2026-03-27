package de.robin.alvarez.viewport.bookmarks

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.net.URI
import javax.swing.JComponent
import javax.swing.JPanel

class CreateBookmarkDialog(
    project: Project,
    private val url: String,
) : DialogWrapper(project) {

    private val urlField = JBTextField(url)
    private val nameField = JBTextField(suggestedName(url))

    init {
        title = "Create bookmark"
        init()
        urlField.isEditable = false
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = JBUI.insets(2, 0, 4, 0)
        }
        c.gridx = 0
        c.gridy = 0
        panel.add(JBLabel("URL:"), c)
        c.gridy = 1
        panel.add(urlField, c)
        c.gridy = 2
        panel.add(JBLabel("Name:"), c)
        c.gridy = 3
        panel.add(nameField, c)
        return panel
    }

    fun bookmarkName(): String = nameField.text.trim()

    companion object {
        private fun suggestedName(url: String): String {
            return try {
                val host = URI(url).host
                if (!host.isNullOrBlank()) host else url.take(80)
            } catch (_: Exception) {
                url.take(80)
            }
        }
    }
}
