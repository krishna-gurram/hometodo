package dev.krishnagurram.hometodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StoreRulesTest {

    private val existing = listOf("Indian", "American", "Costco")

    @Test
    fun `next color skips colors already in use`() {
        val used = StoreRules.palette.take(3)
        assertEquals(StoreRules.palette[3], StoreRules.nextColor(used))
    }

    @Test
    fun `color matching ignores letter case`() {
        assertEquals(StoreRules.palette[1], StoreRules.nextColor(listOf(StoreRules.palette[0].lowercase())))
    }

    @Test
    fun `blank name is rejected`() {
        assertNotNull(StoreRules.nameError("   ", existing))
    }

    @Test
    fun `duplicate name is rejected regardless of case`() {
        assertNotNull(StoreRules.nameError("costco", existing))
    }

    @Test
    fun `new name is accepted`() {
        assertNull(StoreRules.nameError("Sam's Club", existing))
    }

    @Test
    fun `renaming a store to a different case of its own name is allowed`() {
        assertNull(StoreRules.nameError("COSTCO", existing, currentName = "Costco"))
    }
}
