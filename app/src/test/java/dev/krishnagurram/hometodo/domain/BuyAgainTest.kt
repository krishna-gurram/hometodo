package dev.krishnagurram.hometodo.domain

import dev.krishnagurram.hometodo.data.PurchaseTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyAgainTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun bought(id: String, vararg daysAgo: Int) =
        daysAgo.map { PurchaseTime(itemId = id, name = id, boughtAt = now - it * day) }

    @Test
    fun `item bought every 20 days, last 19 days ago, is due`() {
        val result = BuyAgain.probablyDue(bought("ghee", 59, 39, 19), now)
        assertEquals(listOf("ghee"), result.map { it.name })
        assertEquals(20, result.first().everyDays)
    }

    @Test
    fun `item bought recently is not due yet`() {
        assertTrue(BuyAgain.probablyDue(bought("ghee", 45, 25, 5), now).isEmpty())
    }

    @Test
    fun `two purchases are not enough to guess a rhythm`() {
        assertTrue(BuyAgain.probablyDue(bought("ghee", 60, 30), now).isEmpty())
    }

    @Test
    fun `item not bought for over six months is dormant`() {
        assertTrue(BuyAgain.probablyDue(bought("ghee", 260, 230, 200), now).isEmpty())
    }

    @Test
    fun `most overdue item comes first`() {
        val purchases = bought("rice", 63, 42, 21) + bought("ghee", 90, 60, 40)
        assertEquals(listOf("ghee", "rice"), BuyAgain.probablyDue(purchases, now).map { it.name })
    }
}
