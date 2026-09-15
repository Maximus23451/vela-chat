package com.vela.chat.data.tailscale

/**
 * Best-effort metadata extraction from raw model IDs advertised by
 * OpenAI-compatible servers, e.g.
 *
 * - "llama-3-8b-instruct.Q4_K_M" → family "llama", params "8B", quant "Q4_K_M"
 * - "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf" → family "llama", params "8B", quant "Q4_K_M"
 * - "qwen2.5-7b-instruct-q5_k_m" → family "qwen", params "7B", quant "Q5_K_M"
 * - "phi-3-mini-4k-instruct-q4f16_1" → family "phi", params null, quant "Q4F16_1"
 *
 * Pure string heuristics — no network, no server metadata endpoint, and nothing is
 * guaranteed: servers name models arbitrarily, so every field may be null and
 * callers must treat the result as advisory decoration (model dashboard cards,
 * tailnet AI-server picker, `model_cache` metadata columns).
 */
object AiServerHeuristics {

    /** Metadata recoverable from a model ID alone; every field may be null. */
    data class ModelHeuristics(
        /** Quantization tag normalised to uppercase ("Q4_K_M", "Q8_0", "F16", "FP16", ...). */
        val quantization: String?,
        /** Parameter count with an uppercase B suffix ("8B", "3.5B", "70B"). */
        val parameterCount: String?,
        /** Lowercased model family ("llama", "qwen", "mistral", ...). */
        val family: String?,
    )

    /** Parses [modelId]; a blank ID yields an all-null result. */
    fun parse(modelId: String): ModelHeuristics {
        val id = modelId.trim().lowercase()
        if (id.isEmpty()) return ModelHeuristics(quantization = null, parameterCount = null, family = null)
        return ModelHeuristics(
            quantization = quantizationOf(id),
            parameterCount = parameterCountOf(id),
            family = familyOf(id.split(TOKEN_SEPARATOR)),
        )
    }

    /**
     * GGUF-style quantization tags with boundaries so they do not match inside other
     * words: Q4_K_M, Q5_K_S, Q6_K, Q8_0, IQ4_XS, F16, ...
     */
    private val GGUF_QUANT = Regex("""(?<![a-z0-9])(i?[qf]\d+(?:_[a-z0-9]+)*)(?![a-z0-9])""")

    /** ONNX-style / freeform quant keywords the GGUF pattern cannot express. */
    private val QUANT_KEYWORD = Regex(
        """(?<![a-z0-9])(fp16|fp32|bf16|int4|int8|q4f16_1|q4f32_1|8bit|4bit)(?![a-z0-9])""",
    )

    /**
     * Parameter counts like 8b / 3.5b / 70b. The 'b' suffix only — 'k'/'m' suffixes
     * are context lengths or vocab sizes and are excluded on purpose.
     */
    private val PARAMETER_COUNT = Regex("""(?<![a-z0-9])(\d+(?:\.\d+)?)b(?![a-z0-9])""")

    private val TOKEN_SEPARATOR = Regex("""[^a-z0-9]+""")

    /** Well-known families, checked across every token before the first-word fallback. */
    private val KNOWN_FAMILIES = setOf(
        "llama", "codellama", "tinyllama", "mistral", "mixtral", "qwen", "phi", "gemma",
        "deepseek", "yi", "falcon", "mpt", "stablelm", "redpajama", "dolphin", "openhermes",
        "hermes", "zephyr", "vicuna", "wizardlm", "orca", "starcoder", "granite", "internlm",
        "solar", "minicpm", "smollm", "llava", "openchat", "nous",
    )

    /** Variant words that describe a flavour, not a family; never returned as the fallback family. */
    private val NON_FAMILY_TOKENS = setOf(
        "instruct", "chat", "it", "base", "latest", "gguf", "quantized", "merged",
        "finetune", "finetuned", "preview", "experimental", "assistant", "mini",
        "small", "medium", "large", "embed", "embedding", "vision", "audio",
    )

    /** A family fallback token must be at least this long and start with a letter. */
    private const val MIN_FAMILY_TOKEN_LENGTH = 2

    private fun quantizationOf(id: String): String? =
        GGUF_QUANT.find(id)?.groupValues?.get(1)?.uppercase()
            ?: QUANT_KEYWORD.find(id)?.groupValues?.get(1)?.uppercase()

    private fun parameterCountOf(id: String): String? =
        PARAMETER_COUNT.find(id)?.groupValues?.get(1)?.let { "${it}B" }

    private fun familyOf(tokens: List<String>): String? =
        tokens.firstOrNull { it in KNOWN_FAMILIES }
            ?: tokens.firstOrNull {
                it.length >= MIN_FAMILY_TOKEN_LENGTH && it[0].isLetter() && it !in NON_FAMILY_TOKENS
            }
}
