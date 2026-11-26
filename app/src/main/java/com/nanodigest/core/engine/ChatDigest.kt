package com.nanodigest.core.engine

/**
 * Offline "what did I miss?" for group chats.
 *
 * Instead of quoting random messages, it looks for the three things people actually want
 * after being away — what happened (a problem or main topic), what was decided, and who is
 * doing what next — and rewrites them as plain third-person sentences:
 *
 *   "Dev: I'll have a PR up tomorrow"  →  "Dev will have a PR up tomorrow"
 */
object ChatDigest {

    data class Message(val index: Int, val sender: String, val text: String)

    data class Point(val label: String, val text: String)

    fun parse(chat: String): List<Message> = chat.lines().mapNotNull { line ->
        val idx = line.indexOf(": ")
        if (idx <= 0 || idx > 40) null else Message(0, line.substring(0, idx).trim(), line.substring(idx + 2).trim())
    }.filter { it.text.isNotBlank() && !it.text.startsWith("[") }
        .mapIndexed { i, m -> m.copy(index = i) }

    /**
     * Returns up to [count] key points, or an empty list if the text doesn't look like a chat
     * with enough substance (callers then fall back to generic ranking).
     */
    fun digest(chat: String, count: Int, centrality: (List<String>) -> List<Double>): List<Point> {
        val messages = parse(chat)
        if (messages.size < 3) return emptyList()
        val central = centrality(messages.map { it.text })
        val n = messages.size

        fun score(m: Message, cues: List<Pair<Regex, Double>>, recency: Double): Double {
            val lower = m.text.lowercase()
            val cueScore = cues.sumOf { (re, w) -> if (re.containsMatchIn(lower)) w else 0.0 }
            if (cueScore == 0.0) return 0.0
            val questionPenalty = if (m.text.trimEnd().endsWith("?")) 0.4 else 1.0
            val lengthBonus = (wordCount(m.text) / 12.0).coerceAtMost(1.0)
            val position = m.index.toDouble() / (n - 1).coerceAtLeast(1)
            return (cueScore + central[m.index] * 2 + lengthBonus + recency * position) * questionPenalty
        }

        val used = mutableSetOf<Int>()
        fun best(cues: List<Pair<Regex, Double>>, recency: Double): Message? =
            messages.filter { it.index !in used }
                .map { it to score(it, cues, recency) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
                ?.also { used += it.index }

        val decision = best(DECISION, recency = 1.5)
        val problem = best(PROBLEM, recency = -0.5)
        val actions = messages
            .filter { it.index !in used }
            .map { it to score(it, ACTION, recency = 0.5) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .distinctBy { it.first.sender }
            .take(2)
            .map { it.first }
            .sortedBy { it.index }

        val points = mutableListOf<Point>()
        problem?.let { points += Point("What happened", rewrite(it)) }
        decision?.let { points += Point("Decision", rewrite(it)) }
        if (actions.isNotEmpty()) points += Point("Next steps", actions.joinToString(" ") { rewrite(it) })

        if (points.isEmpty()) return emptyList()
        // Most important first when the user asked for fewer points.
        val priority = listOf("Decision", "What happened", "Next steps")
        val chosen = points.sortedBy { priority.indexOf(it.label) }.take(count).toSet()
        return points.filter { it in chosen }
    }

    /** Turns a chat message into a standalone third-person sentence. */
    fun rewrite(m: Message): String {
        var t = m.text
        // Drop sentences that are only links / attachments.
        val sentences = t.split(Regex("""(?<=[.!?])\s+""")).filter { s ->
            val stripped = s.replace(Regex("""\[link]|\[shared[^\]]*]"""), "").replace(Regex("""(?i)\b(here|stack trace|see)\b"""), "")
            stripped.count { it.isLetter() } > 3
        }
        if (sentences.isNotEmpty()) t = sentences.joinToString(" ")
        t = t.replace(Regex("""\s*\[link]"""), "")
        // Remove chat filler at the start.
        repeat(2) { t = t.replace(FILLER, "") }
        // Labels that just repeat the bullet heading.
        t = t.replace(Regex("""(?i)^(decision|update|fyi|reminder|compromise)\s*:\s*"""), "")
        val name = m.sender
        t = t
            .replace(Regex("""(?i)\bI'll\b|\bI will\b"""), "$name will")
            .replace(Regex("""(?i)\bI'm\b|\bI am\b"""), "$name is")
            .replace(Regex("""(?i)\bI've\b|\bI have\b"""), "$name has")
            .replace(Regex("""(?i)\bI think\s+"""), "")
            .replace(Regex("""\bI can\b"""), "$name can")
            .replace(Regex("""\bI\b"""), name)
            .replace(Regex("""(?i)\bmy\b"""), "their")
        // Repeated name in one sentence reads oddly: keep the first, use "they" afterwards.
        val first = t.indexOf(name)
        if (first >= 0) {
            val head = t.substring(0, first + name.length)
            val tail = t.substring(first + name.length).replace(Regex("""\b${Regex.escape(name)} (will|is|has|can)\b""")) { "they ${it.groupValues[1]}" }
            t = head + tail
        }
        t = t.trim().trimEnd(',', ';', '.', ' ')
        if (!t.contains(name)) t = "$t ($name)"
        return t.replaceFirstChar { it.uppercase() } + "."
    }

    private fun wordCount(s: String) = s.split(Regex("""\s+""")).count { it.any(Char::isLetter) }

    private val FILLER = Regex("""(?i)^(ok(ay)?|yes|yeah|yep|so|well|nice|great|cool|sure|hmm+|right|hey|hi|morning|works for me)[,.!]*\s+""")

    private fun cues(vararg pairs: Pair<String, Double>) = pairs.map { Regex(it.first) to it.second }

    private val DECISION = cues(
        """\bdecision\b""" to 3.0, """\bdecided\b""" to 3.0, """\bagreed?\b""" to 2.0, """\bfinal\b""" to 1.5,
        """\blet'?s (go|do|ship|use)\b""" to 1.5, """\bwe('ll| will) (ship|release|go|launch)\b""" to 1.5,
        """\bplan is\b""" to 1.5, """\bapproved\b""" to 2.0, """\bgoing with\b""" to 1.5,
        """\b(release|ship|launch|deadline|rollout)\b""" to 0.5,
    )

    private val PROBLEM = cues(
        """\b(crash|crashes|crashing|bug|issue|error|outage|broken|fails?|failing|down|blocked|regression)\b""" to 1.5,
        // Someone reporting something new outranks people debating it afterwards.
        """\b(new|found|saw|seeing|noticed|reported|spotted|discovered)\b""" to 2.0,
        """\b(users?|customers?|sessions?|affected)\b""" to 0.5,
        """\b(problem|risk|risky|delay|late)\b""" to 0.5,
        """\b(should|disagree|think we)\b""" to -1.0,
    )

    private val ACTION = cues(
        """\bi'll\b|\bi will\b""" to 2.5, """\bi('m| am) going to\b""" to 2.0, """\bi can\b""" to 1.0,
        """\bcan you\b|\bplease\b""" to 1.0, """\breminder\b""" to 1.5, """\b(todo|to-do|need to|will update|let me)\b""" to 1.0,
        """\b(tomorrow|today|monday|tuesday|wednesday|thursday|friday|eod|\d{1,2} ?(am|pm))\b""" to 0.5,
    )
}
