package com.nanodigest.core.engine

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A fully offline, model-free summarizer that runs on every Android phone.
 *
 * It ranks sentences (or chat messages) with TextRank over TF-IDF vectors, nudges the
 * ranking towards sentences that carry decisions, deadlines and problems, and returns the
 * top N verbatim. It can't paraphrase like Gemini Nano, but it is instant, private, works in
 * any language that separates words with spaces, and handles unlimited input length.
 */
class ExtractiveEngine : SummaryEngine {

    override val id = EngineId.EXTRACTIVE
    override val maxInputChars = Int.MAX_VALUE

    override suspend fun availability(config: SummaryConfig): Availability = Availability.Ready

    override suspend fun summarize(text: String, config: SummaryConfig, onText: (String) -> Unit): String {
        val picks = keyPoints(text, config.bullets, config.inputKind == InputKind.CONVERSATION)
        val summary = picks.joinToString("\n") { "• $it" }
        picks.forEachIndexed { i, line -> onText((if (i == 0) "" else "\n") + "• " + line) }
        return summary
    }

    /**
     * Chats get a structured, reworded digest (what happened / decision / next steps);
     * everything else gets the most central sentences.
     */
    fun keyPoints(text: String, count: Int, conversation: Boolean): List<String> {
        if (conversation) {
            val digest = ChatDigest.digest(text, count) { units -> centrality(units) }
            if (digest.isNotEmpty()) return digest.map { "${it.label}: ${it.text}" }
            return rank(text, count, conversation = true).map { line ->
                val idx = line.indexOf(": ")
                if (idx in 1..40) ChatDigest.rewrite(ChatDigest.Message(0, line.substring(0, idx), line.substring(idx + 2))) else line
            }
        }
        return rank(text, count, conversation = false)
    }

    /** TextRank score of each unit, scaled to 0..1. */
    fun centrality(units: List<String>): List<Double> {
        if (units.size < 2) return units.map { 1.0 }
        val (_, score) = graph(units, units.map { tokenize(it) })
        val max = score.max().takeIf { it > 0 } ?: 1.0
        return score.map { it / max }
    }

    private fun graph(units: List<String>, tokens: List<List<String>>): Pair<Array<DoubleArray>, DoubleArray> {
        val df = HashMap<String, Int>()
        tokens.forEach { set -> set.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = units.size.toDouble()
        val vectors = tokens.map { words ->
            val tf = HashMap<String, Double>()
            words.forEach { tf[it] = (tf[it] ?: 0.0) + 1.0 }
            tf.mapValues { (w, f) -> f * (ln(n / (df[w] ?: 1)) + 1.0) }
        }
        val norms = vectors.map { v -> sqrt(v.values.sumOf { it * it }) }
        val sim = Array(units.size) { DoubleArray(units.size) }
        for (i in units.indices) for (j in i + 1 until units.size) {
            if (norms[i] == 0.0 || norms[j] == 0.0) continue
            val (small, large) = if (vectors[i].size < vectors[j].size) vectors[i] to vectors[j] else vectors[j] to vectors[i]
            var dot = 0.0
            for ((w, x) in small) large[w]?.let { dot += x * it }
            val s = dot / (norms[i] * norms[j])
            sim[i][j] = s
            sim[j][i] = s
        }
        val damping = 0.85
        var score = DoubleArray(units.size) { 1.0 }
        val outSum = DoubleArray(units.size) { i -> sim[i].sum() }
        repeat(30) {
            val next = DoubleArray(units.size) { 1 - damping }
            for (i in units.indices) for (j in units.indices) {
                if (i != j && outSum[j] > 0) next[i] += damping * sim[j][i] / outSum[j] * score[j]
            }
            score = next
        }
        return sim to score
    }

    /** Returns the [count] most central units of [text], in their original order. */
    fun rank(text: String, count: Int, conversation: Boolean): List<String> {
        val units = segment(text, conversation)
        if (units.isEmpty()) return emptyList()
        if (units.size <= count) return units.map { shorten(it) }

        val tokens = units.map { tokenize(it) }
        val (sim, score) = graph(units, tokens)
        // Heuristic boosts: decisions, deadlines, problems, asks; slight preference for
        // recent messages in chats (what's latest usually matters most) and early sentences
        // in articles (lede).
        val boosted = units.indices.map { i ->
            val lower = units[i].lowercase()
            var b = 1.0
            if (CUES.any { lower.contains(it) }) b += 0.35
            if (units[i].any { it.isDigit() }) b += 0.1
            if (tokens[i].size < 3) b -= 0.5
            val position = i.toDouble() / (units.size - 1).coerceAtLeast(1)
            b += if (conversation) 0.25 * position else 0.25 * (1 - position)
            i to score[i] * b
        }

        // Pick top units while avoiding near-duplicates.
        val chosen = mutableListOf<Int>()
        for ((i, _) in boosted.sortedByDescending { it.second }) {
            if (chosen.size == count) break
            if (chosen.any { sim[it][i] > 0.6 }) continue
            chosen += i
        }
        return chosen.sorted().map { shorten(units[it]) }
    }

    private fun segment(text: String, conversation: Boolean): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        val raw = if (conversation) {
            cleaned.lines()
        } else {
            cleaned.split(Regex("""(?<=[.!?。！？])\s+|\n+"""))
        }
        return raw.map { it.trim() }.filter { it.length >= 3 && it.any(Char::isLetter) }
    }

    private fun tokenize(text: String): List<String> {
        // In chats ignore the "Name:" prefix so speakers don't dominate similarity.
        val body = text.substringAfter(": ", text)
        return WORD.findAll(body.lowercase()).map { it.value }.filter { it.length > 2 && it !in STOPWORDS }.toList()
    }

    private fun shorten(s: String, max: Int = 240): String =
        if (s.length <= max) s else s.take(max).substringBeforeLast(' ') + "…"

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}']+""")

        val CUES = listOf(
            "decid", "agree", "deadline", "ship", "release", "launch", "crash", "bug", "fix", "block",
            "urgent", "asap", "must", "need to", "will ", "plan", "todo", "action", "tomorrow", "today",
            "monday", "tuesday", "wednesday", "thursday", "friday", "eod", "because", "result", "conclu",
            "important", "problem", "issue", "risk", "approved", "final", "?",
        )

        val STOPWORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one",
            "our", "out", "has", "have", "him", "his", "how", "its", "may", "new", "now", "see", "two", "who",
            "did", "get", "got", "let", "say", "she", "too", "use", "that", "with", "this", "from", "they",
            "will", "would", "there", "their", "what", "about", "which", "when", "make", "like", "time", "just",
            "know", "take", "into", "your", "some", "could", "them", "than", "then", "look", "only", "come",
            "over", "think", "also", "back", "after", "work", "first", "well", "even", "want", "because",
            "these", "give", "most", "been", "were", "said", "each", "does", "very", "much", "should", "yeah",
            "okay", "lol", "yes", "haha", "thanks", "thank", "sure", "gonna", "really", "here", "where",
        )
    }
}
