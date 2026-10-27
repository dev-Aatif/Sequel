package dev.sequel.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedPillRatingTest {

    @Test
    fun clampAndSnapRating_clampsBelowMinimumToHalf() {
        assertEquals(0.5f, RedPillRatingUtil.clampAndSnapRating(0.0f), 0.001f)
        assertEquals(0.5f, RedPillRatingUtil.clampAndSnapRating(-3.5f), 0.001f)
        assertEquals(0.5f, RedPillRatingUtil.clampAndSnapRating(0.2f), 0.001f)
    }

    @Test
    fun clampAndSnapRating_clampsAboveMaximumToTen() {
        assertEquals(10.0f, RedPillRatingUtil.clampAndSnapRating(10.0f), 0.001f)
        assertEquals(10.0f, RedPillRatingUtil.clampAndSnapRating(10.2f), 0.001f)
        assertEquals(10.0f, RedPillRatingUtil.clampAndSnapRating(15.0f), 0.001f)
    }

    @Test
    fun clampAndSnapRating_snapsToNearestHalfPoint() {
        assertEquals(7.0f, RedPillRatingUtil.clampAndSnapRating(7.1f), 0.001f)
        assertEquals(7.0f, RedPillRatingUtil.clampAndSnapRating(7.2f), 0.001f)
        assertEquals(7.5f, RedPillRatingUtil.clampAndSnapRating(7.3f), 0.001f)
        assertEquals(7.5f, RedPillRatingUtil.clampAndSnapRating(7.5f), 0.001f)
        assertEquals(7.5f, RedPillRatingUtil.clampAndSnapRating(7.7f), 0.001f)
        assertEquals(8.0f, RedPillRatingUtil.clampAndSnapRating(7.8f), 0.001f)
        assertEquals(8.0f, RedPillRatingUtil.clampAndSnapRating(8.1f), 0.001f)
        assertEquals(8.5f, RedPillRatingUtil.clampAndSnapRating(8.4f), 0.001f)
        assertEquals(9.0f, RedPillRatingUtil.clampAndSnapRating(8.9f), 0.001f)
    }

    @Test
    fun clampAndSnapRating_allValuesAreDiscreteMultiplesOfHalf() {
        var current = 0.5f
        while (current <= 10.0f) {
            val snapped = RedPillRatingUtil.clampAndSnapRating(current)
            assertEquals(current, snapped, 0.001f)
            val doubled = (snapped * 2).toInt()
            assertTrue("Expected snapped value $snapped to be multiple of 0.5", doubled % 1 == 0)
            current += 0.5f
        }
    }

    @Test
    fun getRatingTier_resolvesAllEightTiersAccurately() {
        // 10.0: Generational Peak
        val peak = RedPillRatingUtil.getRatingTier(10.0f)
        assertEquals("👑", peak.icon)
        assertEquals("Generational Peak", peak.statusPhrase)
        assertEquals("Absolute Cinema", peak.verdictLabel)

        // 9.0 – 9.9: Certified Masterclass
        val masterclass = RedPillRatingUtil.getRatingTier(9.5f)
        assertEquals("🔥", masterclass.icon)
        assertEquals("Certified Masterclass", masterclass.statusPhrase)
        assertEquals("Absolute Cinema", masterclass.verdictLabel)

        // 8.0 – 8.9: Valid Banger
        val banger = RedPillRatingUtil.getRatingTier(8.5f)
        assertEquals("⚡", banger.icon)
        assertEquals("Valid Banger", banger.statusPhrase)
        assertEquals("Certified Banger", banger.verdictLabel)

        // 7.0 – 7.9: Respectably Solid
        val solidHigh = RedPillRatingUtil.getRatingTier(7.5f)
        assertEquals("🥪", solidHigh.icon)
        assertEquals("Respectably Solid", solidHigh.statusPhrase)
        assertEquals("Certified Banger", solidHigh.verdictLabel)

        val solidLow = RedPillRatingUtil.getRatingTier(7.0f)
        assertEquals("🥪", solidLow.icon)
        assertEquals("Respectably Solid", solidLow.statusPhrase)
        assertEquals("Decent / Mid", solidLow.verdictLabel)

        // 6.0 – 6.9: Second-Screen Core
        val secondScreen = RedPillRatingUtil.getRatingTier(6.5f)
        assertEquals("📺", secondScreen.icon)
        assertEquals("Second-Screen Core", secondScreen.statusPhrase)
        assertEquals("Decent / Mid", secondScreen.verdictLabel)

        // 5.0 – 5.9: Aggressively Mid
        val mid = RedPillRatingUtil.getRatingTier(5.5f)
        assertEquals("📉", mid.icon)
        assertEquals("Aggressively Mid", mid.statusPhrase)
        assertEquals("Decent / Mid", mid.verdictLabel)

        // 3.5 – 4.9: Dopamine Sinkhole
        val sinkhole = RedPillRatingUtil.getRatingTier(4.0f)
        assertEquals("💀", sinkhole.icon)
        assertEquals("Dopamine Sinkhole", sinkhole.statusPhrase)
        assertEquals("Blue Pill", sinkhole.verdictLabel)

        // 0.0 – 3.4: Terminal Biohazard
        val biohazard = RedPillRatingUtil.getRatingTier(2.0f)
        assertEquals("☣️", biohazard.icon)
        assertEquals("Terminal Biohazard", biohazard.statusPhrase)
        assertEquals("Blue Pill", biohazard.verdictLabel)
    }

    @Test
    fun formatScore_formatsWithOneDecimalPlace() {
        assertEquals("7.5", RedPillRatingUtil.formatScore(7.5f))
        assertEquals("10.0", RedPillRatingUtil.formatScore(10.0f))
        assertEquals("0.5", RedPillRatingUtil.formatScore(0.5f))
    }
}
