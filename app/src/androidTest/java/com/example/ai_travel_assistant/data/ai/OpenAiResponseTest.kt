package com.example.ai_travel_assistant.data.ai

import com.example.ai_travel_assistant.domain.trip.GenerationException
import com.example.ai_travel_assistant.domain.trip.PlanFailure
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiResponseTest {
    @Test
    fun readsItineraryFromMessageAfterReasoningItem() {
        val response = JSONObject(
            """
            {"status":"completed","output":[
              {"type":"reasoning"},
              {"type":"message","content":[
                {"type":"output_text","text":"{\"days\":[{\"title\":\"Day 1\",\"stops\":[{\"title\":\"My Khe\"}]}]}"}
              ]}
            ]}
            """.trimIndent(),
        )

        val itinerary = readItinerary(readOpenAiText(response))
        assertEquals("My Khe", itinerary.days?.single()?.stops?.single()?.title)
    }

    @Test
    fun rejectsIncompleteOutputAndRefusals() {
        val incomplete = JSONObject(
            """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}""",
        )
        val refusal = JSONObject(
            """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal"}]}]}""",
        )
        assertEquals(PlanFailure.TokenLimit, assertThrows(GenerationException::class.java) {
            readOpenAiText(incomplete)
        }.failure)
        assertEquals(PlanFailure.Blocked, assertThrows(GenerationException::class.java) {
            readOpenAiText(refusal)
        }.failure)
    }
}
