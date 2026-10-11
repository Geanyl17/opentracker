package io.github.geanyl17.opentracker.ui.components

import io.github.geanyl17.opentracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryLooksTest {
    @Test
    fun matchesWholeWords() {
        assertEquals(R.drawable.ic_shopping_cart_fill, CategoryLooks.of("Groceries").icon)
        assertEquals(R.drawable.ic_directions_car_fill, CategoryLooks.of("Car & fuel").icon)
        assertNull(CategoryLooks.of("Credit card").icon)
    }

    @Test
    fun specificRulesComeFirst() {
        assertEquals(R.drawable.ic_savings_fill, CategoryLooks.of("Other income").icon)
        assertEquals(R.drawable.ic_more_horiz_fill, CategoryLooks.of("Other").icon)
        assertEquals(R.drawable.ic_local_cafe_fill, CategoryLooks.of("Coffee").icon)
    }

    @Test
    fun unknownNamesGetAStableColor() {
        assertEquals(CategoryLooks.of("Hobby box").color, CategoryLooks.of("hobby box").color)
        assertEquals(CategoryLooks.uncategorized, CategoryLooks.of(null))
    }
}
