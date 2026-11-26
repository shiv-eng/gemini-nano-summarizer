package com.nanodigest.core.text

data class ChatMessage(val sender: String, val text: String)

data class ChatParseResult(
    val messages: List<ChatMessage>,
    /** Messages kept after applying the "last N" window. */
    val kept: List<ChatMessage>,
    val participants: List<String>,
    val mediaCount: Int,
    val droppedSystemLines: Int,
    val linksReplaced: Int,
) {
    /** One `Name: message` per line — the format Gemini Nano's CONVERSATION mode reads best. */
    val formatted: String get() = kept.joinToString("\n") { "${it.sender}: ${it.text}" }
}

/**
 * Turns messy chat exports (WhatsApp Android/iOS, Telegram desktop copies, or plain
 * `Name: message` text) into clean `Name: message` lines:
 *
 * - drops timestamps, system notices ("Messages are end-to-end encrypted") and deleted messages
 * - replaces media attachments with short placeholders like `[shared a photo]`
 * - joins multi-line messages back together
 * - optionally replaces URLs with `[link]` (they cost many tokens and carry little meaning)
 * - optionally keeps only the last N messages (the "what did I miss?" window)
 */
object ChatFormatter {

    // [12/31/23, 9:41:05 PM] Alice: hi      (iOS)
    // 12/31/23, 9:41 PM - Alice: hi          (Android)
    // 31.12.2023, 21:41 - Alice: hi
    private val WHATSAPP = Regex(
        """^\[?(\d{1,4}[./-]\d{1,2}[./-]\d{1,4}),?\s+(\d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s?[APap]\.?\s?[Mm]\.?)?)\]?\s*(?:[-–—]\s*)?(.*)$""",
    )

    // [09:41] Alice: hi   or   09:41 Alice: hi
    private val TIME_PREFIX = Regex("""^\[?(\d{1,2}:\d{2}(?::\d{2})?(?:\s?[APap][Mm])?)\]?\s*[-–]?\s*(.+)$""")

    // Telegram desktop copy:  "Alice, [31.12.23 21:41]" followed by message lines
    private val TELEGRAM_HEADER = Regex("""^(.{1,40}?),\s*\[[\d./:,\s APMapm]+]$""")

    // Plain "Name: message"
    private val NAME_LINE = Regex("""^([\p{L}\p{N}@+][\p{L}\p{N} ._'()@+-]{0,39}?):\s+(.+)$""")

    private val URL = Regex("""https?://\S+|www\.\S+""")

    private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩﻿]")

    private val MEDIA_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)^<?\s*media omitted\s*>?$""") to "[shared media]",
        Regex("""(?i)^(image|photo) omitted$""") to "[shared a photo]",
        Regex("""(?i)^video omitted$""") to "[shared a video]",
        Regex("""(?i)^(audio|voice message) omitted$""") to "[sent a voice note]",
        Regex("""(?i)^sticker omitted$""") to "[sent a sticker]",
        Regex("""(?i)^gif omitted$""") to "[sent a GIF]",
        Regex("""(?i)^document omitted$""") to "[shared a document]",
        Regex("""(?i)^contact card omitted$""") to "[shared a contact]",
        Regex("""(?i)^<attached:.*>$""") to "[shared a file]",
        Regex("""(?i)^.*\(file attached\)$""") to "[shared a file]",
        Regex("""(?i)^location: https?://\S+$""") to "[shared a location]",
    )

    private val DELETED = Regex("""(?i)^(this message was deleted|you deleted this message|message deleted)\.?$""")

    private val SYSTEM_HINTS = listOf(
        "end-to-end encrypted", "created group", "added you", " added ", " left", " joined using",
        "changed the subject", "changed this group", "changed the group", "security code changed",
        "pinned a message", "removed ", "disappearing messages", "you're now an admin", "missed voice call",
        "missed video call",
    )

    fun looksLikeChat(text: String): Boolean {
        val lines = text.lineSequence().map { clean(it) }.filter { it.isNotBlank() }.take(60).toList()
        if (lines.size < 3) return false
        val chatty = lines.count {
            WHATSAPP.matches(it) || TELEGRAM_HEADER.matches(it) || NAME_LINE.matches(it) ||
                TIME_PREFIX.find(it)?.groupValues?.get(2)?.let { rest -> NAME_LINE.matches(rest) } == true
        }
        return chatty * 2 >= lines.size
    }

    fun parse(text: String, lastN: Int = 0, replaceLinks: Boolean = true): ChatParseResult {
        val messages = mutableListOf<MutableMsg>()
        var media = 0
        var system = 0
        var links = 0
        var pendingTelegramSender: String? = null

        fun addMessage(sender: String, body: String) {
            val processed = processBody(body.trim(), replaceLinks)
            when (processed) {
                is Body.Dropped -> Unit
                is Body.Media -> {
                    media++
                    messages += MutableMsg(sender.trim(), StringBuilder(processed.placeholder))
                }
                is Body.Text -> {
                    links += processed.linksReplaced
                    messages += MutableMsg(sender.trim(), StringBuilder(processed.text))
                }
            }
        }

        for (rawLine in text.lineSequence()) {
            val line = clean(rawLine)
            if (line.isBlank()) continue

            val wa = WHATSAPP.matchEntire(line)
            if (wa != null) {
                pendingTelegramSender = null
                val rest = wa.groupValues[3]
                val nameMatch = splitSender(rest)
                if (nameMatch == null) {
                    system++ // timestamped line without "Name:" is a system notice
                } else {
                    addMessage(nameMatch.first, nameMatch.second)
                }
                continue
            }

            val tg = TELEGRAM_HEADER.matchEntire(line)
            if (tg != null) {
                pendingTelegramSender = tg.groupValues[1]
                continue
            }
            if (pendingTelegramSender != null) {
                addMessage(pendingTelegramSender, line)
                pendingTelegramSender = null
                continue
            }

            val timeRest = TIME_PREFIX.matchEntire(line)?.groupValues?.get(2)
            val candidate = if (timeRest != null && NAME_LINE.matches(timeRest)) timeRest else line
            val named = NAME_LINE.matchEntire(candidate)
            if (named != null && !looksLikeSentenceColon(named.groupValues[1])) {
                addMessage(named.groupValues[1], named.groupValues[2])
                continue
            }

            if (isSystem(line) && messages.isEmpty()) {
                system++
                continue
            }

            // Continuation of a multi-line message.
            val last = messages.lastOrNull()
            if (last != null) {
                val processed = processBody(line, replaceLinks)
                if (processed is Body.Text) {
                    links += processed.linksReplaced
                    last.text.append(' ').append(processed.text)
                }
            } else {
                messages += MutableMsg("Unknown", StringBuilder(line))
            }
        }

        val all = messages.map { ChatMessage(it.sender, it.text.toString().trim()) }.filter { it.text.isNotEmpty() }
        val kept = if (lastN > 0 && all.size > lastN) all.takeLast(lastN) else all
        return ChatParseResult(
            messages = all,
            kept = kept,
            participants = kept.map { it.sender }.distinct(),
            mediaCount = media,
            droppedSystemLines = system,
            linksReplaced = links,
        )
    }

    private class MutableMsg(val sender: String, val text: StringBuilder)

    private sealed interface Body {
        data object Dropped : Body
        data class Media(val placeholder: String) : Body
        data class Text(val text: String, val linksReplaced: Int) : Body
    }

    private fun processBody(body: String, replaceLinks: Boolean): Body {
        if (body.isEmpty() || body.equals("null", ignoreCase = true)) return Body.Dropped
        if (DELETED.matches(body)) return Body.Dropped
        MEDIA_PATTERNS.firstOrNull { it.first.matches(body) }?.let { return Body.Media(it.second) }
        if (!replaceLinks) return Body.Text(body, 0)
        var count = 0
        val replaced = URL.replace(body) { count++; "[link]" }
        return Body.Text(replaced, count)
    }

    private fun splitSender(rest: String): Pair<String, String>? {
        val idx = rest.indexOf(": ")
        if (idx <= 0 || idx > 40) {
            // Some exports put the media placeholder right after "Name:" with no space.
            if (rest.endsWith(":") && rest.length <= 41) return rest.dropLast(1) to ""
            return null
        }
        return rest.substring(0, idx) to rest.substring(idx + 2)
    }

    private fun isSystem(line: String): Boolean {
        val lower = line.lowercase()
        return SYSTEM_HINTS.any { lower.contains(it) }
    }

    /** Avoids treating "Note: remember to..." style prose as a speaker label when it reads like a sentence. */
    private fun looksLikeSentenceColon(label: String): Boolean = label.split(' ').size > 4

    private fun clean(line: String): String =
        line.replace(INVISIBLE, "").replace(' ', ' ').replace(' ', ' ').trimEnd()
}
