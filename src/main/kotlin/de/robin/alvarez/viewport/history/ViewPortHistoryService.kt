package de.robin.alvarez.viewport.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection
import de.robin.alvarez.viewport.HistoryEntry
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

@State(name = "ViewPortHistory", storages = [Storage("viewport-history.xml")])
class ViewPortHistoryService : SimplePersistentStateComponent<ViewPortHistoryService.HistoryState>(HistoryState()) {

    class HistoryState : BaseState() {
        @get:XCollection(style = XCollection.Style.v2)
        var entries by list<HistoryBean>()
    }

    class HistoryBean : BaseState() {
        var url by string("")
        var timestampIso by string("")
    }

    fun getEntries(): List<HistoryEntry> =
        state.entries.mapNotNull { bean ->
            val url = bean.url?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val ts = try {
                LocalDateTime.parse(bean.timestampIso ?: "")
            } catch (_: DateTimeParseException) {
                LocalDateTime.now()
            }
            HistoryEntry(url, ts, null)
        }

    fun addEntry(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val next = state.entries.toMutableList()
        next.add(
            HistoryBean().apply {
                this.url = trimmed
                this.timestampIso = LocalDateTime.now().toString()
            },
        )
        while (next.size > MAX_HISTORY_SIZE) {
            next.removeAt(0)
        }
        state.entries = next
    }

    fun clear() {
        state.entries = mutableListOf<HistoryBean>()
    }

    companion object {
        const val MAX_HISTORY_SIZE = 100

        fun getInstance(): ViewPortHistoryService =
            requireNotNull(ApplicationManager.getApplication().getService(ViewPortHistoryService::class.java)) {
                "ViewPortHistoryService is not registered"
            }
    }
}
