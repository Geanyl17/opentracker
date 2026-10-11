package io.github.geanyl17.opentracker.ai

import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.SettingsStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
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

/** Applies the changes the model asks for in the "actions" of its answer. */
class AssistantTools(private val ledger: Ledger, private val settings: SettingsStore) {
    sealed interface Outcome {
        data class Applied(val action: AssistantAction) : Outcome
        data class Rejected(val reason: String) : Outcome
    }

    suspend fun apply(action: JsonObject, context: FinanceContext): Outcome = when (action.string("type")) {
        "add_transaction" -> add(action, context)
        "update_transaction" -> update(action, context)
        "delete_transaction" -> delete(action, context)
        "set_budget" -> setBudget(action, context)
        else -> Outcome.Rejected("unknown action ${action.string("type")}")
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

    private suspend fun add(args: JsonObject, context: FinanceContext): Outcome {
        val kind = args.kind() ?: Kind.EXPENSE
        val amount = args.amount() ?: return Outcome.Rejected("the amount must be a positive number")
        val category = context.find(args.string("category"), kind)
        val entry = Entry(
            kind = kind,
            amount = amount,
            categoryId = category?.id,
            note = args.string("note").orEmpty().trim(),
            date = args.date() ?: context.today,
        )
        return Outcome.Applied(AssistantAction.Added(entry.copy(id = ledger.add(entry)), category?.name))
    }

    private suspend fun update(args: JsonObject, context: FinanceContext): Outcome {
        val id = args.long("id") ?: return Outcome.Rejected("updating needs a transaction id")
        val before = ledger.entry(id) ?: return Outcome.Rejected("there's no transaction $id")
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
        return Outcome.Applied(AssistantAction.Updated(before, after, category?.name))
    }

    private suspend fun delete(args: JsonObject, context: FinanceContext): Outcome {
        val id = args.long("id") ?: return Outcome.Rejected("deleting needs a transaction id")
        val entry = ledger.entry(id) ?: return Outcome.Rejected("there's no transaction $id")
        ledger.delete(entry)
        return Outcome.Applied(AssistantAction.Deleted(entry, context.category(entry.categoryId)?.name))
    }

    private suspend fun setBudget(args: JsonObject, context: FinanceContext): Outcome {
        val amount = args.amount(allowZero = true) ?: return Outcome.Rejected("the budget must be a number")
        val budget = amount.takeIf { it > 0 }
        val name = args.string("category")
        if (name.isNullOrBlank()) {
            val previous = settings.current().monthlyBudget
            settings.update { it.copy(monthlyBudget = budget) }
            return Outcome.Applied(AssistantAction.BudgetChanged(null, previous, budget))
        }
        val category = context.find(name, Kind.EXPENSE, strict = true)
            ?: return Outcome.Rejected("there's no expense category called $name")
        ledger.saveCategory(category.copy(budget = budget))
        return Outcome.Applied(AssistantAction.BudgetChanged(category, category.budget, budget))
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

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(trim()) }.getOrNull()

    companion object {
        /** The shape of every answer. It stays the same between requests, so the relay can start the CLI ahead of time. */
        val schema: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                property("reply", "string", "What to tell the user. Plain text, read aloud.")
                putJsonObject("actions") {
                    put("type", "array")
                    put("description", "Changes for the app to make, in order. Empty when nothing should change.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("type") {
                                put("type", "string")
                                putJsonArray("enum") {
                                    listOf("add_transaction", "update_transaction", "delete_transaction", "set_budget")
                                        .forEach { add(JsonPrimitive(it)) }
                                }
                            }
                            property("id", "integer", "The transaction id, for update_transaction and delete_transaction")
                            putJsonObject("kind") {
                                put("type", "string")
                                putJsonArray("enum") { listOf("expense", "income").forEach { add(JsonPrimitive(it)) } }
                            }
                            property("amount", "number", "A positive amount. For set_budget, 0 removes the budget.")
                            property("category", "string", "An existing category name")
                            property("note", "string", "A short description of what it was")
                            property("date", "string", "YYYY-MM-DD. Leave out for today.")
                        }
                        putJsonArray("required") { add(JsonPrimitive("type")) }
                    }
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("reply"))
                add(JsonPrimitive("actions"))
            }
        }

        private fun JsonObjectBuilder.property(name: String, type: String, description: String) {
            putJsonObject(name) {
                put("type", type)
                put("description", description)
            }
        }
    }
}
