package dev.krishnagurram.hometodo.domain

// Everything about turning WhatsApp text into items, and items back into text.
// All pure functions, all tested in ShareTextTest.

/** One line of a shared message, e.g. "2 curd" -> name "curd", quantity "2". */
data class ParsedLine(val name: String, val quantity: String?)

object ShareParser {

    // "[10/27, 7:40 PM] Wife: " that WhatsApp adds when you copy several messages.
    private val chatPrefix = Regex("""^\[[^\]]*\]\s*[^:]{1,40}:\s*""")

    // "- ", "* ", "• ", "1. ", "2) " at the start of a line.
    private val bullet = Regex("""^\s*(?:[-*•·]+|\d+[.)])\s*""")

    // "2 curd", "20 lb rice", "2kg atta". The unit is optional.
    private val leadingQuantity = Regex(
        """^(\d+(?:\.\d+)?\s*(?:kg|g|lbs?|packs?|bunch(?:es)?|dozen|bottles?|boxes?)?)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    // "curd x2", "curd x 2", "milk - 2".
    private val trailingQuantity = Regex("""^(.+?)\s*(?:[x×]\s*(\d+)|-\s*(\d+))$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<ParsedLine> =
        text.lines()
            .map { it.replace(chatPrefix, "") }
            .flatMap { it.split(',') }
            .map { it.replace(bullet, "").trim() }
            .filter { it.isNotEmpty() }
            .map(::parseOne)
            .filter { it.name.isNotEmpty() }
            .distinctBy { it.name.lowercase() }

    private fun parseOne(raw: String): ParsedLine {
        trailingQuantity.matchEntire(raw)?.let { m ->
            val qty = m.groupValues[2].ifEmpty { m.groupValues[3] }
            return ParsedLine(m.groupValues[1].trim(), qty)
        }
        leadingQuantity.matchEntire(raw)?.let { m ->
            return ParsedLine(m.groupValues[2].trim(), m.groupValues[1].trim())
        }
        return ParsedLine(raw, null)
    }
}

/** The minimum the matcher needs to know about a catalog item. */
data class CatalogEntry(val id: String, val name: String, val aliases: String)

object ItemMatcher {

    fun normalize(s: String): String = s.trim().lowercase().replace(Regex("""\s+"""), " ")

    /** "onions" also tries "onion"; "tomatoes" also tries "tomato". Crude, but covers lists. */
    private fun variants(s: String): Set<String> {
        val n = normalize(s)
        return setOf(n, n.removeSuffix("es"), n.removeSuffix("s")).filter { it.isNotEmpty() }.toSet()
    }

    /** Finds the catalog item whose name or any alias matches. Null means "new item". */
    fun match(name: String, catalog: List<CatalogEntry>): CatalogEntry? {
        val wanted = variants(name)
        return catalog.firstOrNull { entry ->
            val names = listOf(entry.name) + entry.aliases.split(',')
            names.any { candidate -> candidate.isNotBlank() && variants(candidate).any { it in wanted } }
        }
    }
}

object ShareFormatter {

    /** "Indian (2)\n- Rice, 20 lb\n- Coriander" for pasting into WhatsApp. */
    fun format(storeName: String, items: List<Pair<String, String?>>): String = buildString {
        append("$storeName (${items.size})")
        items.forEach { (name, quantity) ->
            append("\n- ").append(name)
            if (!quantity.isNullOrBlank()) append(", ").append(quantity)
        }
    }
}
