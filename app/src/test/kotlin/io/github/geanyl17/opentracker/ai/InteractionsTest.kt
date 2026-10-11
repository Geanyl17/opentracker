package io.github.geanyl17.opentracker.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionsTest {
    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun readsFunctionCalls() {
        val response = parse(
            """
            {
              "id": "int_001",
              "status": "requires_action",
              "steps": [
                {"type": "thought", "summary": [{"type": "text", "text": "Log it"}], "signature": "abc123"},
                {"type": "function_call", "id": "fc_1", "name": "add_transaction", "arguments": {"kind": "expense", "amount": 250}}
              ]
            }
            """,
        )
        val calls = response.steps.functionCalls()
        assertEquals(1, calls.size)
        assertEquals("fc_1", calls[0].id)
        assertEquals("add_transaction", calls[0].name)
        assertEquals(JsonPrimitive(250), calls[0].arguments["amount"])
        assertEquals("", response.steps.outputText())
    }

    @Test
    fun readsModelOutput() {
        val response = parse(
            """
            {
              "status": "completed",
              "steps": [
                {"type": "model_output", "content": [{"type": "text", "text": "You've spent ₱4,200 on food."}]}
              ]
            }
            """,
        )
        assertEquals("You've spent ₱4,200 on food.", response.steps.outputText())
        assertEquals(emptyList<FunctionCall>(), response.steps.functionCalls())
    }

    @Test
    fun agentReportIsTheTextAfterTheLastToolStep() {
        val response = parse(
            """
            {
              "status": "completed",
              "steps": [
                {"type": "user_input", "content": [{"type": "text", "text": "Analyze"}]},
                {"type": "model_output", "content": [{"type": "text", "text": "Reading the CSV first."}]},
                {"type": "function_call", "id": "c1", "name": "run_code", "arguments": {}},
                {"type": "function_result", "call_id": "c1", "result": [{"type": "text", "text": "ok"}]},
                {"type": "thought", "signature": "x"},
                {"type": "model_output", "content": [{"type": "text", "text": "## Summary"}, {"type": "text", "text": "\nAll good."}]}
              ]
            }
            """,
        )
        assertEquals("## Summary\nAll good.", response.steps.finalOutputText())
    }

    @Test
    fun readsErrorMessages() {
        val wrapped = """[{"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT"}}]"""
        assertEquals("API key not valid. Please pass a valid API key.", errorMessage(wrapped))
        assertEquals("Quota exceeded", errorMessage("""{"error": {"code": 429, "message": "Quota exceeded"}}"""))
        assertEquals(null, errorMessage("<html>Bad gateway</html>"))
    }
}
