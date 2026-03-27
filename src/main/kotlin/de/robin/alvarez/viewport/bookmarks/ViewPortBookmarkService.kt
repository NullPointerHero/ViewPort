package de.robin.alvarez.viewport.bookmarks

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection

@State(name = "ViewPortBookmarks", storages = [Storage("viewport-bookmarks.xml")])
class ViewPortBookmarkService : SimplePersistentStateComponent<ViewPortBookmarkService.BookmarkState>(BookmarkState()) {

    class BookmarkState : BaseState() {
        @get:XCollection(style = XCollection.Style.v2)
        var entries by list<BookmarkBean>()
    }

    class BookmarkBean : BaseState() {
        var name by string("")
        var url by string("")
    }

    fun getBookmarks(): List<BookmarkBean> = state.entries.toList()

    fun addBookmark(name: String, url: String) {
        val bean = BookmarkBean().apply {
            this.name = name
            this.url = url
        }
        val next = state.entries.toMutableList()
        next.add(bean)
        state.entries = next
    }

    fun removeBookmarkAt(index: Int) {
        val next = state.entries.toMutableList()
        if (index in next.indices) {
            next.removeAt(index)
            state.entries = next
        }
    }

    fun clearAllBookmarks() {
        state.entries = mutableListOf<BookmarkBean>()
    }

    companion object {
        fun getInstance(): ViewPortBookmarkService =
            requireNotNull(ApplicationManager.getApplication().getService(ViewPortBookmarkService::class.java)) {
                "ViewPortBookmarkService is not registered"
            }
    }
}
