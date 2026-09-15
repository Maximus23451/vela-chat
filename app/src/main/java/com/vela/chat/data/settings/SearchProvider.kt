package com.vela.chat.data.settings

/** Supported web-search backends. */
enum class SearchProvider(val label: String, val keyHint: String = "") {
    SEARXNG("SearXNG (self-hosted)"),
    BRAVE("Brave Search API", "api.search.brave.com subscription token"),
    TAVILY("Tavily", "tavily.com API key (tvly-…)"),
    SERPAPI("SerpAPI (Google)", "serpapi.com API key"),
    GOOGLE_PSE("Google Programmable Search", "Google API key"),
    DUCKDUCKGO("DuckDuckGo (free, limited)");

    val needsApiKey: Boolean get() = this == BRAVE || this == TAVILY || this == SERPAPI || this == GOOGLE_PSE
    val needsUrl: Boolean get() = this == SEARXNG
    val needsCx: Boolean get() = this == GOOGLE_PSE

    /** Stable id used to store this provider's API key in the secure store. */
    val secureKeyId: String get() = "search_${name.lowercase()}"
}

/** Everything [com.vela.chat.data.remote.WebSearchClient] needs for one query. */
data class SearchConfig(
    val provider: SearchProvider,
    val searxngUrl: String = "",
    val apiKey: String? = null,
    val googleCx: String = "",
    val maxResults: Int = 5,
)
