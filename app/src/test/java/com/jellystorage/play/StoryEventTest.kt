package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryEventTest {
    private val eventIds = listOf("merchant", "stele", "bard", "archive", "inkwell", "spirit_forge")

    @Test
    fun `every map event has three usable choices`() {
        eventIds.forEach { id ->
            val (title, body, choices) = StoryBook.eventScript(id)
            assertTrue(title.isNotBlank(), "$id needs a title")
            assertTrue(body.isNotBlank(), "$id needs story text")
            assertEquals(3, choices.size, "$id should offer three choices")
            choices.forEach { (label, result, effect) ->
                assertTrue(label.isNotBlank(), "$id has a blank choice label")
                assertTrue(result.isNotBlank(), "$id has a blank result")
                assertTrue(effect.isNotBlank(), "$id has a blank effect key")
            }
        }
    }

    @Test
    fun `new events expose their intended distinct effects`() {
        val inkwellEffects = StoryBook.eventScript("inkwell").third.map { it.third }.toSet()
        val forgeEffects = StoryBook.eventScript("spirit_forge").third.map { it.third }.toSet()

        assertEquals(setOf("skill_amp", "mana_cdr", "fruit_gift"), inkwellEffects)
        assertEquals(setOf("forge_weapon", "mystery_weapon", "gold25"), forgeEffects)
    }
}
