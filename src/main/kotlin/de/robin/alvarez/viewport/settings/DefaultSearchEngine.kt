package de.robin.alvarez.viewport.settings

enum class DefaultSearchEngine(val id: String, val displayName: String, val homeUrl: String) {
    GOOGLE("google", "Google", "https://www.google.com"),
    BING("bing", "Bing", "https://www.bing.com"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com"),
    YAHOO("yahoo", "Yahoo", "https://www.yahoo.com"),
    ECOSIA("ecosia", "Ecosia", "https://www.ecosia.org"),
    BRAVE("brave", "Brave Search", "https://search.brave.com"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com"),
    ;

    companion object {
        fun fromId(id: String?): DefaultSearchEngine =
            entries.find { it.id == id } ?: GOOGLE
    }
}
