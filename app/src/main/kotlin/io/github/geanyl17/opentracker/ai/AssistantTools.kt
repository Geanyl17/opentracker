package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.SettingsStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.time.LocalDate

/** Something the assistant changed, kept so the chat can offer to undo it. */
sealed interface AssistantAction {
    data class Added(val entry: Entry, val category: String?) : AssistantAction
    data class Updated(val before: Entry, val after: Entry, val category: String?) : AssistantAction
    data class Deleted(val entry: Entry, val category: String?) : AssistantAction
    data class BudgetChanged(val category: Category?, val previous: Long?, val budget: Long?) : AssistantAction
}

class AssistantTools(private val ledger: Ledger, private val settings: SettingsStore) {
    class Outcome(val result: JsonObject, val action: AssistantAction? = null)

    fun declarations(context: FinanceContext): JsonArray {
        val names = context.categories.map { it.name }.distinct()
        val amount = "Positive amount in ${context.currency}, for example 250.5"
        return buildJsonArray {
            add(
                function("add_transaction", "Records money the user spent or received.", required = listOf("kind", "amount", "category")) {
                    enum("kind", "expense for money spent, income for money received", listOf("expense", "income"))
                    number("amount", amount)
                    if (names.isEmpty()) string("category", "Category name") else enum("category", "Category name", names)
                    string("note", "Short description of what it was, for example \"Lunch with Ana\"")
                    string("date", "YYYY-MM-DD. Leave out for today.")
                },
            )
            add(
                function("update_transaction", "Changes a recorded transaction. Only pass the fields that change.", required = listOf("id")) {
                    integer("id", "The transaction id")
                    enum("kind", "expense or income", listOf("expense", "income"))
                    number("amount", amount)
                    string("category", "Category name")
                    string("note", "Short description")
                    string("date", "YYYY-MM-DD")
                },
            )
            add(
                function("delete_transaction", "Deletes a recorded transaction.", required = listOf("id")) {
                    integer("id", "The transaction id")
                },
            )
            add(
                function(
                    "set_budget",
                    "Sets the monthly budget for an expense category, or the overall monthly budget when no category is given. An amount of 0 removes the budget.",
                    required = listOf("amount"),
                ) {
                    number("amount", "Monthly budget in ${context.currency}")
                    string("category", "Expense category name. Leave out for the overall budget.")
                },
            )
        }
    }

    suspend fun run(call: FunctionCall, context: FinanceContext): Outcome {
        val args = call.arguments
        return when (call.name) {
            "add_transaction" -> {
                val kind = args.kind() ?: Kind.EXPENSE
                val amount = args.amount() ?: return failure("amount must be a positive number")
                val category = context.find(args.string("category"), kind)
                val entry = Entry(
                    kind = kind,
                    amount = amount,
                    categoryId = category?.id,
                    note = args.string("note").orEmpty().trim(),
                    date = args.date() ?: context.today,
                )
                val id = ledger.add(entry)
                Outcome(success { put("id", id) }, AssistantAction.Added(entry.copy(id = id), category?.name))
            }
            "update_transaction" -> {
                val id = args.long("id") ?: return failure("id is required")
                val before = ledger.entry(id) ?: return failure("there is no transaction with id $id")
                val kind = args.kind() ?: before.kind
                val category = if (args.containsKey("category")) context.find(args.string("category"), kind) else context.category(before.categoryId)
                val after = before.copy(
                    kind = kind,
                    amount = args.amount() ?: before.amount,
                    categoryId = category?.id,
                    note = args.string("note")?.trim() ?: before.note,
                    date = args.date() ?: before.date,
                )
                ledger.update(after)
                Outcome(success(), AssistantAction.Updated(before, after, category?.name))
            }
            "delete_transaction" -> {
                val id = args.long("id") ?: return failure("id is required")
                val entry = ledger.entry(id) ?: return failure("there is no transaction with id $id")
                ledger.delete(entry)
                Outcome(success(), AssistantAction.Deleted(entry, context.category(entry.categoryId)?.name))
            }
            "set_budget" -> {
                val budget = args.amount(allowZero = true) ?: return failure("amount must be a number, 0 to remove")
                val name = args.string("category")
                if (name.isNullOrBlank()) {
                    val previous = settings.current().monthlyBudget
                    settings.update { it.copy(monthlyBudget = budget.takeIf { b -> b > 0 }) }
                    Outcome(success(), AssistantAction.BudgetChanged(null, previous, budget.takeIf { it > 0 }))
                } else {
                    val category = context.find(name, Kind.EXPENSE, strict = true)
                        ?: return failure("there is no expense category named $name")
                    ledger.saveCategory(category.copy(budget = budget.takeIf { it > 0 }))
                    Outcome(success(), AssistantAction.BudgetChanged(category, category.budget, budget.takeIf { it > 0 }))
                }
            }
            else -> failure("unknown function ${call.name}")
        }
    }

    suspend fun undo(action: AssistantAction) {
        when (action) {
            is AssistantAction.Added -> ledger.delete(action.entry)
            is AssistantAction.Updated -> ledger.update(action.before)
            is AssistantAction.Deleted -> ledger.restore(action.entry)
            is AssistantAction.BudgetChanged ->
                if (action.category == null) {
                    settings.update { it.copy(monthlyBudget = action.previous) }
                } else {
                    ledger.saveCategory(action.category.copy(budget = action.previous))
                }
        }
    }

    private fun FinanceContext.find(name: String?, kind: Kind, strict: Boolean = false): Category? {
        if (name.isNullOrBlank()) return null
        val matches = categories.filter { it.name.equals(name.trim(), ignoreCase = true) }
        return matches.firstOrNull { it.kind == kind } ?: if (strict) null else matches.firstOrNull()
    }

    private fun JsonObject.kind(): Kind? = when (string("kind")?.lowercase()) {
        "expense" -> Kind.EXPENSE
        "income" -> Kind.INCOME
        else -> null
    }

    private fun JsonObject.amount(allowZero: Boolean = false): Long? {
        val value = (this["amount"] as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull() ?: return null
        val cents = Amounts.fromDecimal(value.abs()) ?: return null
        return cents.takeIf { it > 0 || (allowZero && it == 0L) }
    }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull()?.toLong()

    private fun JsonObject.date(): LocalDate? = string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun success(extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("ok", true)
        extra()
    }

    private fun failure(message: String) = Outcome(
        buildJsonObject {
            put("ok", false)
            put("error", message)
        },
    )

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(trim()) }.getOrNull()
}

private class ParametersBuilder {
    val properties = mutableMapOf<String, JsonObject>()

    fun string(name: String, description: String) = prop(name, "string", description)

    fun number(name: String, description: String) = prop(name, "number", description)

    fun integer(name: String, description: String) = prop(name, "integer", description)

    fun enum(name: String, description: String, values: List<String>) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("description", description)
            putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun prop(name: String, type: String, description: String) {
        properties[name] = buildJsonObject {
            put("type", type)
            put("description", description)
        }
    }
}

private fun function(
    name: String,
    description: String,
    required: List<String>,
    parameters: ParametersBuilder.() -> Unit,
): JsonObject {
    val params = ParametersBuilder().apply(parameters)
    return buildJsonObject {
        put("type", "function")
        put("name", name)
        put("description", description)
        putJsonObject("parameters") {
            put("type", "object")
            put("properties", JsonObject(params.properties))
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }
    }
}
