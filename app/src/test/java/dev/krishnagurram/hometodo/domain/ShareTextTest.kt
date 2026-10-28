package dev.krishnagurram.hometodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTextTest {

    @Test
    fun `splits lines and commas, strips bullets`() {
        val parsed = ShareParser.parse("- dhania\n• methi, onions\n1. paneer")
        assertEquals(listOf("dhania", "methi", "onions", "paneer"), parsed.map { it.name })
    }

    @Test
    fun `reads leading and trailing quantities`() {
        val parsed = ShareParser.parse("2 curd\nmilk x2\n20 lb rice\neggs - 3")
        assertEquals(
            listOf(
                ParsedLine("curd", "2"),
                ParsedLine("milk", "2"),
                ParsedLine("rice", "20 lb"),
                ParsedLine("eggs", "3"),
            ),
            parsed,
        )
    }

    @Test
    fun `a number that is part of a word is not a quantity`() {
        assertEquals(ParsedLine("gobi", "2"), ShareParser.parse("2 gobi").single())
    }

    @Test
    fun `strips the WhatsApp copy prefix and drops duplicates`() {
        val parsed = ShareParser.parse("[10/27, 7:40 PM] Wife: Onions\nonions")
        assertEquals(listOf("Onions"), parsed.map { it.name })
    }

    private val catalog = listOf(
        CatalogEntry("1", "Coriander", "dhania,cilantro"),
        CatalogEntry("2", "Onion", ""),
        CatalogEntry("3", "Tomato", ""),
    )

    @Test
    fun `matches by alias, ignoring case`() {
        assertEquals("1", ItemMatcher.match("Dhania", catalog)?.id)
    }

    @Test
    fun `matches simple plurals`() {
        assertEquals("2", ItemMatcher.match("onions", catalog)?.id)
        assertEquals("3", ItemMatcher.match("tomatoes", catalog)?.id)
    }

    @Test
    fun `unknown item has no match`() {
        assertNull(ItemMatcher.match("ice cream", catalog))
    }

    @Test
    fun `formats a list for sharing`() {
        val text = ShareFormatter.format("Indian", listOf("Rice" to "20 lb", "Coriander" to null))
        assertEquals("Indian (2)\n- Rice, 20 lb\n- Coriander", text)
    }
}
