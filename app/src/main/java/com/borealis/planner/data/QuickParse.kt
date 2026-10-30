package com.borealis.planner.data

/**
 * Reads plain-language hints out of a task title as you type, e.g.
 * "Gym tomorrow 6pm !!" → title "Gym", +1 day, 18:00, medium priority.
 */
object QuickParse {

    data class Result(
        val title: String,
        val dayOffset: Int? = null,
        val time: Int? = null,
        val priority: Priority? = null,
        /** A "#tag" naming a category, without the '#'. */
        val tag: String? = null,
    ) {
        val found: Boolean get() = dayOffset != null || time != null || priority != null || tag != null
    }

    // Note: Android's regex engine (ICU) reads "[:" as the start of a POSIX class,
    // so character sets here never begin with a colon.
    private val dayWords = Regex("""(?i)(?<![\p{L}\d])(today|tonight|tomorrow|tmrw|tmr|tmw)(?![\p{L}\d])""")
    private val time12 = Regex("""(?i)(?<![\p{L}\d\:])(?:at\s+)?(1[0-2]|0?[1-9])(?:[.\:]([0-5]\d))?\s*(am|pm|a\.m\.|p\.m\.)(?![\p{L}\d])""")
    private val time24 = Regex("""(?i)(?<![\p{L}\d\:])(?:at\s+)?([01]?\d|2[0-3])[.\:]([0-5]\d)(?![\p{L}\d\:])""")
    private val timeWords = Regex("""(?i)(?<![\p{L}\d])(?:at\s+)?(noon|midday|midnight)(?![\p{L}\d])""")
    private val atHour = Regex("""(?i)(?<![\p{L}\d])at\s+(1[0-2]|[1-9])(?![\p{L}\d\:.])""")
    private val hashtag = Regex("""(?<=^|\s)#([\p{L}\d][\p{L}\d_\-]*)""")
    private val bangs = Regex("""(?<=^|\s)(!{1,3})(?=\s|$)""")

    fun parse(input: String): Result {
        var text = input
        var dayOffset: Int? = null
        var time: Int? = null
        var priority: Priority? = null
        var tag: String? = null

        hashtag.find(text)?.let { m ->
            tag = m.groupValues[1]
            text = text.removeRange(m.range)
        }

        dayWords.find(text)?.let { m ->
            dayOffset = when (m.groupValues[1].lowercase()) {
                "today", "tonight" -> 0
                else -> 1
            }
            text = text.removeRange(m.range)
        }

        val m12 = time12.find(text)
        if (m12 != null) {
            var h = m12.groupValues[1].toInt() % 12
            val min = m12.groupValues[2].ifEmpty { "0" }.toInt()
            if (m12.groupValues[3].lowercase().startsWith("p")) h += 12
            time = h * 60 + min
            text = text.removeRange(m12.range)
        } else {
            val m24 = time24.find(text)
            if (m24 != null) {
                time = m24.groupValues[1].toInt() * 60 + m24.groupValues[2].toInt()
                text = text.removeRange(m24.range)
            } else {
                val mw = timeWords.find(text)
                if (mw != null) {
                    time = if (mw.groupValues[1].lowercase() == "midnight") 0 else 12 * 60
                    text = text.removeRange(mw.range)
                } else {
                    atHour.find(text)?.let { m ->
                        val h = m.groupValues[1].toInt()
                        // "at 7" most likely means 7 PM; "at 9" most likely 9 AM.
                        val hour24 = if (h in 1..7) h + 12 else h
                        time = hour24 * 60
                        text = text.removeRange(m.range)
                    }
                }
            }
        }

        bangs.find(text)?.let { m ->
            priority = when (m.groupValues[1].length) {
                3 -> Priority.HIGH
                2 -> Priority.MEDIUM
                else -> Priority.LOW
            }
            text = text.removeRange(m.range)
        }

        val clean = text.replace(Regex("""\s{2,}"""), " ").trim().trimEnd(',', '-', '·').trim()
        return Result(clean, dayOffset, time, priority, tag)
    }
}
