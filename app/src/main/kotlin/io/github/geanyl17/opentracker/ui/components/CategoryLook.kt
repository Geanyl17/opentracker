package io.github.geanyl17.opentracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.github.geanyl17.opentracker.R
import kotlin.math.absoluteValue

/** The icon and color a category is drawn with, picked from the words in its name. */
data class CategoryLook(@param:DrawableRes val icon: Int?, val color: Color)

object CategoryLooks {
    private val orange = Color(0xFFF97316)
    private val green = Color(0xFF22C55E)
    private val blue = Color(0xFF3B82F6)
    private val sky = Color(0xFF0EA5E9)
    private val violet = Color(0xFF8B5CF6)
    private val indigo = Color(0xFF6366F1)
    private val pink = Color(0xFFEC4899)
    private val rose = Color(0xFFF43F5E)
    private val red = Color(0xFFEF4444)
    private val amber = Color(0xFFF59E0B)
    private val lime = Color(0xFF84CC16)
    private val cyan = Color(0xFF06B6D4)
    private val teal = Color(0xFF14B8A6)
    private val emerald = Color(0xFF10B981)
    private val brown = Color(0xFFB45309)
    private val slate = Color(0xFF64748B)

    private val palette = listOf(orange, blue, violet, pink, teal, amber, indigo, rose, cyan, lime, sky, emerald)

    private class Rule(val words: List<String>, @param:DrawableRes val icon: Int, val color: Color)

    private val rules = listOf(
        Rule(listOf("income", "earning", "earnings", "bonus", "allowance"), R.drawable.ic_savings_fill, teal),
        Rule(listOf("grocery", "groceries", "grocer", "supermarket", "market"), R.drawable.ic_shopping_cart_fill, green),
        Rule(listOf("coffee", "cafe", "tea"), R.drawable.ic_local_cafe_fill, brown),
        Rule(listOf("food", "eat", "eating", "restaurant", "dining", "lunch", "dinner", "breakfast", "meal", "snack"), R.drawable.ic_restaurant_fill, orange),
        Rule(listOf("transport", "transportation", "bus", "train", "commute", "taxi", "grab", "uber", "jeep", "fare"), R.drawable.ic_directions_bus_fill, blue),
        Rule(listOf("car", "gas", "fuel", "parking", "toll"), R.drawable.ic_directions_car_fill, sky),
        Rule(listOf("rent", "house", "housing", "home", "mortgage"), R.drawable.ic_house_fill, violet),
        Rule(listOf("bill", "bills", "utility", "utilities", "electric", "electricity", "water", "power"), R.drawable.ic_bolt_fill, violet),
        Rule(listOf("phone", "internet", "mobile", "load", "data", "wifi"), R.drawable.ic_phone_iphone_fill, indigo),
        Rule(listOf("subscription", "subscriptions", "streaming", "netflix", "spotify"), R.drawable.ic_subscriptions_fill, rose),
        Rule(listOf("shopping", "shop", "clothes", "clothing", "fashion"), R.drawable.ic_shopping_bag_fill, pink),
        Rule(listOf("health", "medical", "medicine", "pharmacy", "doctor", "hospital", "dental"), R.drawable.ic_medical_services_fill, red),
        Rule(listOf("gym", "fitness", "sport", "sports"), R.drawable.ic_fitness_center_fill, lime),
        Rule(listOf("game", "games", "gaming"), R.drawable.ic_sports_esports_fill, indigo),
        Rule(listOf("fun", "entertainment", "movie", "movies", "party", "hobby", "hobbies", "leisure"), R.drawable.ic_celebration_fill, amber),
        Rule(listOf("school", "education", "tuition", "book", "books", "course", "courses"), R.drawable.ic_school_fill, cyan),
        Rule(listOf("travel", "trip", "trips", "flight", "flights", "vacation", "hotel"), R.drawable.ic_flight_fill, sky),
        Rule(listOf("pet", "pets", "dog", "cat"), R.drawable.ic_pets_fill, brown),
        Rule(listOf("gift", "gifts", "donation", "donations", "charity"), R.drawable.ic_redeem_fill, rose),
        Rule(listOf("salary", "wage", "wages", "pay", "paycheck", "payroll"), R.drawable.ic_payments_fill, emerald),
        Rule(listOf("freelance", "work", "side", "business", "client", "clients", "commission"), R.drawable.ic_work_fill, cyan),
        Rule(listOf("savings", "saving", "invest", "investment", "investments", "interest", "dividend", "dividends"), R.drawable.ic_savings_fill, teal),
        Rule(listOf("other", "misc", "miscellaneous", "others"), R.drawable.ic_more_horiz_fill, slate),
    )

    val uncategorized = CategoryLook(R.drawable.ic_more_horiz_fill, slate)

    fun of(name: String?): CategoryLook {
        if (name.isNullOrBlank()) return uncategorized
        val words = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val rule = rules.firstOrNull { rule -> words.any { it in rule.words } }
        return if (rule != null) CategoryLook(rule.icon, rule.color) else CategoryLook(null, palette[name.lowercase().hashCode().absoluteValue % palette.size])
    }
}
