package de.robin.alvarez.viewport.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ViewPortSettings", storages = [Storage("viewport-settings.xml")])
class ViewPortSettingsService : SimplePersistentStateComponent<ViewPortSettingsService.SettingsState>(SettingsState()) {

    class SettingsState : BaseState() {
        var searchEngineId by string(DefaultSearchEngine.GOOGLE.id)
        /** Persisted as "true" / "false" for reliable XML binding. */
        var showTabBar by string("true")
        var showBookmarksBar by string("true")
    }

    fun getSearchEngine(): DefaultSearchEngine = DefaultSearchEngine.fromId(state.searchEngineId)

    fun setSearchEngine(engine: DefaultSearchEngine) {
        state.searchEngineId = engine.id
    }

    fun isShowTabBar(): Boolean = state.showTabBar != "false"

    fun setShowTabBar(show: Boolean) {
        state.showTabBar = if (show) "true" else "false"
    }

    fun isShowBookmarksBar(): Boolean = state.showBookmarksBar != "false"

    fun setShowBookmarksBar(show: Boolean) {
        state.showBookmarksBar = if (show) "true" else "false"
    }

    fun homePageUrl(): String = getSearchEngine().homeUrl

    companion object {
        fun getInstance(): ViewPortSettingsService =
            requireNotNull(ApplicationManager.getApplication().getService(ViewPortSettingsService::class.java)) {
                "ViewPortSettingsService is not registered"
            }
    }
}
