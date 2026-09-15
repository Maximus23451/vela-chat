package com.vela.chat.ui.components.markdown

/**
 * Content-addressed LRU memoization for parsed Markdown blocks.
 *
 * Re-parsing a long finalized message on every recomposition (window scroll,
 * flag toggle, selection change) is wasteful: the parse result depends only on
 * the content string. [getOrParse] returns cached blocks for content seen
 * recently (max [MAX_ENTRIES] entries, LRU eviction), so re-renders skip the
 * parse entirely while streaming recompositions only ever parse the tail once.
 *
 * A JVM [LinkedHashMap] with access order is used instead of `android.util.LruCache`
 * so the cache is unit-testable and dependency-free.
 */
object MarkdownCache {

    /** Upper bound on cached documents — long chats stream through, old entries evict. */
    private const val MAX_ENTRIES = 64

    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f

    private val cache = object : LinkedHashMap<String, List<MdBlock>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MdBlock>>): Boolean =
            size > MAX_ENTRIES
    }

    /** Return cached blocks for [markdown], parsing (and caching) on first sight. */
    fun getOrParse(markdown: String): List<MdBlock> = synchronized(cache) {
        cache.getOrPut(markdown) { parseBlocks(markdown) }
    }

    /** Drop all cached parses (used by tests and theme memory pressure). */
    fun clear() = synchronized(cache) { cache.clear() }
}
