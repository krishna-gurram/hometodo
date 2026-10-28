package dev.krishnagurram.hometodo.domain

/** Rules for creating and renaming stores. Pure Kotlin, tested in StoreRulesTest. */
object StoreRules {

    /** Store colors, chosen to stay readable as small dots on a white card. */
    val palette = listOf(
        "#B45309", // amber
        "#1D4ED8", // blue
        "#475569", // slate
        "#7C3AED", // violet
        "#0F766E", // teal
        "#BE185D", // pink
        "#4D7C0F", // olive
        "#B91C1C", // red
    )

    /** First palette color no store uses yet. With 9+ stores, colors start repeating. */
    fun nextColor(usedColors: Collection<String>): String =
        palette.firstOrNull { color -> usedColors.none { it.equals(color, ignoreCase = true) } }
            ?: palette[usedColors.size % palette.size]

    /**
     * Returns an error message to show under the name field, or null if the name is fine.
     * `currentName` is the store being renamed (null when adding), so renaming
     * "Costco" to "costco" isn't reported as a duplicate of itself.
     */
    fun nameError(name: String, existingNames: List<String>, currentName: String? = null): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Enter a store name"
        val clash = existingNames.any {
            it.equals(trimmed, ignoreCase = true) && !it.equals(currentName, ignoreCase = true)
        }
        return if (clash) "You already have a store called $trimmed" else null
    }
}
