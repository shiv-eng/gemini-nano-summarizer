package com.nanodigest.core.text

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** A date (and maybe a time) mentioned in a summary, ready to become a calendar event. */
data class FoundEvent(
    val title: String,
    val description: String,
    val start: LocalDateTime,
    val allDay: Boolean,
    val phrase: String,
)

/**
 * Finds dates like "Thursday 10 AM", "tomorrow at 5pm", "March 14" or "Sat 8 PM" in short
 * summary sentences, resolved relative to [reference] (when the chat was summarized).
 * Fully offline; meant for "Add to calendar" suggestions, so it prefers precision over recall.
 */
object DateFinder {

    private val WEEKDAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val MONTHS = Month.entries.associateBy { it.name.take(3).lowercase() }

    private const val WD = """(mon|tues?|wed(?:nes)?|thu(?:rs?)?|fri|sat(?:ur)?|sun)(?:day)?"""
    private const val MON = """(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?"""

    private val DATE = Regex(
        """(?i)\b(?:(today|tonight|tomorrow)|(?:next\s+)?$WD|$MON\s+(\d{1,2})(?:st|nd|rd|th)?|(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?$MON)\b""",
    )
    private val TIME_AMPM = Regex("""(?i)^[\s,]*(?:at\s+|by\s+|@\s*)?(\d{1,2})(?:[:.](\d{2}))?\s*([ap])\.?m\.?\b""")
    private val TIME_24 = Regex("""^[\s,]*(?:at\s+|by\s+|@\s*)?([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val TIME_WORD = Regex("""(?i)^[\s,]*(?:at\s+)?(noon|midday|midnight)\b""")

    fun find(text: String, reference: LocalDateTime, max: Int = 3): List<FoundEvent> {
        val out = mutableListOf<FoundEvent>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim().removePrefix("•").trim()
            if (line.isEmpty()) continue
            val sentence = line.substringAfter(": ").takeIf { line.indexOf(": ") in 1..20 } ?: line
            for (m in DATE.findAll(sentence)) {
                val date = resolveDate(m, reference.toLocalDate()) ?: continue
                val after = sentence.substring(m.range.last + 1)
                val time = parseTime(after)
                val start = if (time != null) LocalDateTime.of(date, time.first) else date.atStartOfDay()
                if (out.any { it.start == start }) continue
                out += FoundEvent(
                    title = titleFrom(sentence),
                    description = "$sentence\n\nAdded from Nano Digest",
                    start = start,
                    allDay = time == null,
                    phrase = (m.value + (time?.second ?: "")).trim(),
                )
            }
        }
        // Timed events are the useful ones; keep them first and cap the list.
        return out.sortedWith(compareBy<FoundEvent> { it.allDay }.thenBy { it.start }).take(max)
    }

    private fun resolveDate(m: MatchResult, today: LocalDate): LocalDate? {
        val g = m.groupValues
        return when {
            g[1].isNotEmpty() -> when (g[1].lowercase()) {
                "tomorrow" -> today.plusDays(1)
                else -> today
            }
            g[2].isNotEmpty() -> {
                val day = WEEKDAYS[g[2].lowercase().take(3)] ?: return null
                val next = m.value.lowercase().startsWith("next")
                val d = today.with(TemporalAdjusters.nextOrSame(day))
                if (next && d == today) d.plusWeeks(1) else d
            }
            g[3].isNotEmpty() -> monthDay(g[3], g[4], today)
            g[5].isNotEmpty() -> monthDay(g[6], g[5], today)
            else -> null
        }
    }

    private fun monthDay(monthText: String, dayText: String, today: LocalDate): LocalDate? {
        val month = MONTHS[monthText.lowercase().take(3)] ?: return null
        val day = dayText.toIntOrNull() ?: return null
        if (day !in 1..month.maxLength()) return null
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (thisYear.isBefore(today.minusDays(1))) thisYear.plusYears(1) else thisYear
    }

    /** Time right after the date phrase, plus the text it matched. */
    private fun parseTime(after: String): Pair<LocalTime, String>? {
        TIME_AMPM.find(after)?.let { t ->
            val h = t.groupValues[1].toInt()
            val min = t.groupValues[2].toIntOrNull() ?: 0
            if (h !in 1..12 || min !in 0..59) return null
            val pm = t.groupValues[3].equals("p", ignoreCase = true)
            val hour = (h % 12) + if (pm) 12 else 0
            return LocalTime.of(hour, min) to t.value
        }
        TIME_24.find(after)?.let { t -> return LocalTime.of(t.groupValues[1].toInt(), t.groupValues[2].toInt()) to t.value }
        TIME_WORD.find(after)?.let { t ->
            val time = if (t.groupValues[1].equals("midnight", true)) LocalTime.MIDNIGHT else LocalTime.NOON
            return time to t.value
        }
        return null
    }

    private val FILLER = Regex("""(?i)^(ok(ay)?|yes|yeah|so|well|sure|hey)[,.!]*\s+""")

    private fun titleFrom(sentence: String): String {
        val clean = sentence.replace(FILLER, "").replaceFirstChar { it.uppercase() }.replace(Regex("""\s*\([^)]*\)\s*\.?$"""), "").trimEnd('.', ' ')
        val firstClause = clean.split(Regex("""[,;]""")).first().trim()
        val base = if (firstClause.length in 8..60) firstClause else clean
        return if (base.length <= 60) base else base.take(60).substringBeforeLast(' ') + "…"
    }
}
