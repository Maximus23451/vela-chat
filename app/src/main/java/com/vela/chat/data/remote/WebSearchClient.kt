package com.vela.chat.data.remote

import com.vela.chat.data.AppJson
import com.vela.chat.data.settings.SearchConfig
import com.vela.chat.data.settings.SearchProvider
import com.vela.chat.di.NoLogOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** A single web result handed back to the model. */
data class SearchResult(val title: String, val url: String, val snippet: String)

/**
 * Multi-provider web search. Each provider has its own endpoint and response
 * shape; all are normalised to [SearchResult]. The provider and its credentials
 * come from [SearchConfig].
 *
 * Uses [NoLogOkHttpClient] rather than the app's main client: SerpAPI and Google PSE
 * only accept their API key as a URL query parameter, and the main client's request
 * logging (debug builds) would otherwise put that key in Logcat.
 */
@Singleton
class WebSearchClient @Inject constructor(
    @NoLogOkHttpClient private val client: OkHttpClient,
) {
    suspend fun search(config: SearchConfig, query: String): Result<List<SearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val n = config.maxResults.coerceIn(1, 10)
                when (config.provider) {
                    SearchProvider.SEARXNG -> searxng(config.searxngUrl, query, n)
                    SearchProvider.BRAVE -> brave(config.requireKey(), query, n)
                    SearchProvider.TAVILY -> tavily(config.requireKey(), query, n)
                    SearchProvider.SERPAPI -> serpapi(config.requireKey(), query, n)
                    SearchProvider.GOOGLE_PSE -> googlePse(config.requireKey(), config.googleCx, query, n)
                    SearchProvider.DUCKDUCKGO -> duckduckgo(query, n)
                }
            }
        }

    private fun SearchConfig.requireKey(): String =
        apiKey?.takeIf { it.isNotBlank() } ?: error("${provider.label} needs an API key — set it in Settings → Web search.")

    // ---- SearXNG ----
    private fun searxng(baseUrl: String, query: String, n: Int): List<SearchResult> {
        require(baseUrl.isNotBlank()) { "SearXNG URL is not set." }
        val url = "${baseUrl.trim().trimEnd('/')}/search?q=${enc(query)}&format=json&safesearch=0"
        val body = getJson(Request.Builder().url(url).header("Accept", "application/json")) {
            "HTTP $it from SearXNG — enable the JSON API (search.formats: [html, json])."
        }
        return AppJson.decodeFromString<SearxngResponse>(body).results.take(n)
            .map { SearchResult(it.title.orEmpty(), it.url.orEmpty(), it.content.orEmpty()) }
    }

    // ---- Brave ----
    private fun brave(key: String, query: String, n: Int): List<SearchResult> {
        val url = "https://api.search.brave.com/res/v1/web/search?q=${enc(query)}&count=$n"
        val body = getJson(
            Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", key),
        ) { "HTTP $it from Brave — check your subscription token." }
        return AppJson.decodeFromString<BraveResponse>(body).web?.results.orEmpty().take(n)
            .map { SearchResult(it.title.orEmpty(), it.url.orEmpty(), it.description.orEmpty()) }
    }

    // ---- Tavily (POST) ----
    private fun tavily(key: String, query: String, n: Int): List<SearchResult> {
        val payload = AppJson.encodeToString(TavilyRequest(api_key = key, query = query, max_results = n))
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toRequestBody(JSON))
            .header("Accept", "application/json")
        val body = getJson(request) { "HTTP $it from Tavily — check your API key." }
        return AppJson.decodeFromString<TavilyResponse>(body).results.take(n)
            .map { SearchResult(it.title.orEmpty(), it.url.orEmpty(), it.content.orEmpty()) }
    }

    // ---- SerpAPI ----
    private fun serpapi(key: String, query: String, n: Int): List<SearchResult> {
        val url = "https://serpapi.com/search.json?engine=google&q=${enc(query)}&num=$n&api_key=${enc(key)}"
        val body = getJson(Request.Builder().url(url)) { "HTTP $it from SerpAPI — check your API key." }
        return AppJson.decodeFromString<SerpApiResponse>(body).organic_results.take(n)
            .map { SearchResult(it.title.orEmpty(), it.link.orEmpty(), it.snippet.orEmpty()) }
    }

    // ---- Google Programmable Search ----
    private fun googlePse(key: String, cx: String, query: String, n: Int): List<SearchResult> {
        require(cx.isNotBlank()) { "Google PSE needs a Search Engine ID (cx)." }
        val url = "https://www.googleapis.com/customsearch/v1?key=${enc(key)}&cx=${enc(cx)}&q=${enc(query)}&num=$n"
        val body = getJson(Request.Builder().url(url)) { "HTTP $it from Google PSE — check the key and cx." }
        return AppJson.decodeFromString<GoogleResponse>(body).items.orEmpty().take(n)
            .map { SearchResult(it.title.orEmpty(), it.link.orEmpty(), it.snippet.orEmpty()) }
    }

    // ---- DuckDuckGo (free instant-answer API; limited but no key) ----
    private fun duckduckgo(query: String, n: Int): List<SearchResult> {
        val url = "https://api.duckduckgo.com/?q=${enc(query)}&format=json&no_html=1&skip_disambig=1"
        val body = getJson(Request.Builder().url(url)) { "HTTP $it from DuckDuckGo." }
        val parsed = AppJson.decodeFromString<DdgResponse>(body)
        val results = buildList {
            if (!parsed.AbstractText.isNullOrBlank()) {
                add(SearchResult(parsed.Heading.orEmpty(), parsed.AbstractURL.orEmpty(), parsed.AbstractText))
            }
            parsed.RelatedTopics.orEmpty().forEach { t ->
                if (!t.Text.isNullOrBlank() && !t.FirstURL.isNullOrBlank()) {
                    add(SearchResult(t.Text.take(60), t.FirstURL, t.Text))
                }
            }
        }
        return results.take(n)
    }

    // No explicit .get() needed — OkHttp defaults to GET; Tavily sets .post() itself.
    private fun getJson(builder: Request.Builder, errorMessage: (Int) -> String): String {
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) error(errorMessage(response.code))
            return response.body?.string().orEmpty()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    // ---- Provider response DTOs ----
    @Serializable private data class SearxngResponse(val results: List<SearxngResult> = emptyList())
    @Serializable private data class SearxngResult(val title: String? = null, val url: String? = null, val content: String? = null)

    @Serializable private data class BraveResponse(val web: BraveWeb? = null)
    @Serializable private data class BraveWeb(val results: List<BraveResult> = emptyList())
    @Serializable private data class BraveResult(val title: String? = null, val url: String? = null, val description: String? = null)

    @Serializable private data class TavilyRequest(val api_key: String, val query: String, val max_results: Int, val search_depth: String = "basic")
    @Serializable private data class TavilyResponse(val results: List<TavilyResult> = emptyList())
    @Serializable private data class TavilyResult(val title: String? = null, val url: String? = null, val content: String? = null)

    @Serializable private data class SerpApiResponse(val organic_results: List<SerpResult> = emptyList())
    @Serializable private data class SerpResult(val title: String? = null, val link: String? = null, val snippet: String? = null)

    @Serializable private data class GoogleResponse(val items: List<GoogleItem>? = null)
    @Serializable private data class GoogleItem(val title: String? = null, val link: String? = null, val snippet: String? = null)

    @Serializable private data class DdgResponse(
        val Heading: String? = null,
        val AbstractText: String? = null,
        val AbstractURL: String? = null,
        val RelatedTopics: List<DdgTopic>? = null,
    )
    @Serializable private data class DdgTopic(val Text: String? = null, val FirstURL: String? = null)
}
