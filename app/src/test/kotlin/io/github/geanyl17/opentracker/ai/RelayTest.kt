package io.github.geanyl17.opentracker.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayTest {
    @Test
    fun normalizesAddresses() {
        assertEquals("192.168.1.20:8765", RelayClient.normalizeAddress("192.168.1.20"))
        assertEquals("192.168.1.20:9000", RelayClient.normalizeAddress(" https://192.168.1.20:9000/ "))
        assertEquals("my-pc.tailnet.ts.net:8765", RelayClient.normalizeAddress("my-pc.tailnet.ts.net"))
        assertNull(RelayClient.normalizeAddress(""))
        assertNull(RelayClient.normalizeAddress("192.168.1.20:99999"))
        assertNull(RelayClient.normalizeAddress("192.168.1.20/path"))
        assertNull(RelayClient.normalizeAddress("my pc"))
    }

    @Test
    fun shortensFingerprintsLikeTheRelay() {
        assertEquals("98:12:AD:54:27:07:1E:54", RelayClient.shortFingerprint("9812AD5427071E5482FC974CAC06574A"))
    }

    @Test
    fun readsStructuredAnswers() {
        val structured = Json.parseToJsonElement(
            """{"reply": "Recorded ₱120 for coffee.", "actions": [{"type": "add_transaction", "amount": 120, "category": "Food"}]}""",
        ).jsonObject
        val answer = readAnswer(RelayResult(text = "ignored", structured = structured))
        assertEquals("Recorded ₱120 for coffee.", answer.reply)
        assertEquals(1, answer.actions.size)
        assertEquals(JsonPrimitive("add_transaction"), answer.actions[0]["type"])
    }

    @Test
    fun fallsBackToJsonInTheText() {
        val text = "```json\n{\"reply\": \"Done.\", \"actions\": []}\n```"
        assertEquals(Answer("Done.", emptyList()), readAnswer(RelayResult(text, structured = null)))
    }

    @Test
    fun treatsPlainTextAsTheReply() {
        assertEquals(Answer("You spent ₱4,200 on food.", emptyList()), readAnswer(RelayResult(" You spent ₱4,200 on food. ", null)))
    }

    @Test
    fun answerSchemaNeedsAReplyAndActions() {
        val required = AssistantTools.schema["required"].toString()
        assertEquals("""["reply","actions"]""", required)
    }
}
