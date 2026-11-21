package app.cartogliss.geo

/**
 * Parsing of typed coordinates in DD, DDM or DMS, with N/S/E/W letters (before or after) or a sign.
 * Examples: `45.1234`, `-6.5`, `N 45°07.408'`, `45°7'24.4"N`, `W6 30`, `45 7 24.4 N`.
 */
object CoordInput {
    /** Degrees, or null when [text] isn't a valid latitude ([isLat]) or longitude. */
    fun parse(text: String, isLat: Boolean): Double? {
        var t = text.trim().uppercase()
            .replace('º', '°').replace('′', '\'').replace('″', '"').replace("''", "\"")
            .replace(',', '.')
        if (t.isEmpty()) return null
        var negative = false
        val hemis = if (isLat) "NS" else "EW"
        fun takeHemisphere(c: Char): Boolean {
            if (c !in "NSEW") return false
            if (c !in hemis) throw IllegalArgumentException()
            negative = c == 'S' || c == 'W'
            return true
        }
        try {
            if (takeHemisphere(t.first())) t = t.drop(1).trim()
            if (t.isNotEmpty() && takeHemisphere(t.last())) t = t.dropLast(1).trim()
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (t.startsWith("-")) {
            if (negative) return null
            negative = true
            t = t.drop(1)
        } else if (t.startsWith("+")) {
            t = t.drop(1)
        }
        if (t.any { it !in "0123456789. °'\"" }) return null
        val parts = t.split(Regex("[°'\"\\s]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = parts.map { it.toDoubleOrNull() ?: return null }
        // Only the last component may have decimals; minutes and seconds must be < 60.
        if (parts.dropLast(1).any { '.' in it }) return null
        if (nums.drop(1).any { it >= 60.0 }) return null
        val value = nums[0] + nums.getOrElse(1) { 0.0 } / 60.0 + nums.getOrElse(2) { 0.0 } / 3600.0
        if (value > (if (isLat) 90.0 else 180.0)) return null
        return if (negative) -value else value
    }

    /**
     * Space bar behaviour: when the user types a space right after a digit, replace it with the next
     * separator: ° then ' then ". Returns the new text and cursor.
     */
    fun applySpace(old: String, new: String, cursor: Int): Pair<String, Int> {
        if (new.length != old.length + 1 || cursor <= 0 || cursor > new.length) return new to cursor
        if (new[cursor - 1] != ' ') return new to cursor
        if (new.removeRange(cursor - 1, cursor) != old) return new to cursor
        val before = new.getOrNull(cursor - 2) ?: return new to cursor
        if (!before.isDigit()) return new to cursor
        val sep = when {
            '°' !in old -> '°'
            '\'' !in old -> '\''
            '"' !in old -> '"'
            else -> return new to cursor
        }
        return (new.substring(0, cursor - 1) + sep + new.substring(cursor)) to cursor
    }
}
