package com.cvke.fridge.lookup

import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The values of the add form from a looked-up product: the name, the category, the food of the table of foods, and
 * the quantity of the package. The foods here have the names, the categories, and the keywords of `spec/foods.json`.
 */
class ProductMappingTest {
    private fun food(id: String, name: String, subtitle: String?, category: Category, vararg keywords: String) =
        Food(id, name, subtitle, category, keywords.toList(), mapOf(StorageKind.FRIDGE to 7))

    private val foods = FoodTable(
        listOf(
            food("fk-1", "Butter", null, Category.DAIRY, "butter"),
            food("fk-3", "Cheese", "hard such as cheddar, swiss, block parmesan", Category.DAIRY, "cheese", "cheddar", "swiss", "parmesan"),
            food("fk-4", "Cheese", "parmesan; shredded or grated", Category.DAIRY, "cheese", "parmesan", "shredded", "grated"),
            food("fk-7", "Cheese", "soft such as brie, bel paese, goat", Category.DAIRY, "cheese", "brie", "bel paese", "goat"),
            food("fk-21", "Eggs", "in shell", Category.DAIRY, "eggs", "in shell", "egg", "shell"),
            food("fk-23", "Eggs", "hard boiled cooked", Category.DAIRY, "eggs", "egg", "hard boiled cooked", "boiled", "hard boiled"),
            food("fk-27", "Milk", "plain or flavored", Category.DAIRY, "milk", "plain", "flavored", "flavor"),
            food("fk-33", "Yogurt", null, Category.DAIRY, "yogurt"),
            food("fk-113", "Chicken", "whole", Category.MEAT, "chicken", "whole"),
            food("fk-118", "Chicken parts", "legs or thighs", Category.MEAT, "chicken parts", "legs", "thighs", "chicken", "leg", "thigh"),
            food("fk-216", "Chocolate", "unsweetened and semi-sweet, solid", Category.OTHER, "chocolate"),
            food("fk-383", "Milk", "canned evaporated or condensed", Category.OTHER, "milk", "canned"),
            food("fk-387", "Peanut butter", "commercial", Category.OTHER, "peanut butter", "commercial"),
        ),
    )

    private fun product(name: String, brands: String? = null, quantity: String? = null, categories: List<String> = emptyList()) =
        LookedUpProduct(name, brands, quantity, categories)

    @Test
    fun theNameHasTheBrandOnlyWhenTheNameDoesNotHaveIt() {
        assertEquals("Nutella", ProductNames.of(product("Nutella", "Ferrero, Nutella")))
        assertEquals("Coca-Cola", ProductNames.of(product("Coca-Cola", "COCA-COLA SERVICES SA/NV")))
        assertEquals("Fage Greek Yogurt", ProductNames.of(product("Greek Yogurt", "Fage")))
        assertEquals("Greek Yogurt", ProductNames.of(product("Greek Yogurt", " , ")))
    }

    @Test
    fun aMessyNameBecomesACleanName() {
        // Tabs, line breaks, and spaces become one space. HTML entities become their characters. Capital letters
        // only get a capital letter at the start of each word.
        assertEquals(
            "Bio&Gut Greek Style Yogurt",
            ProductNames.of(product("GREEK  STYLE\tYOGURT\n", " ,  BIO&amp;GUT  , Hofmolkerei")),
        )
        assertEquals("Ben \"n\" Jerry's Cookie Dough", ProductNames.of(product("Ben &quot;n&quot; Jerry's Cookie Dough")))
        // A name with small letters stays as it is. A name of three capital letters or fewer stays also: it can be an
        // abbreviation.
        assertEquals("UHT Milk", ProductNames.of(product("UHT Milk")))
        assertEquals("BBQ", ProductNames.of(product("BBQ")))
    }

    @Test
    fun aLongNameLosesTheBrandAndThenTheWordsAtTheEnd() {
        val name = "Organic wholegrain sourdough bread with sunflower seeds, pumpkin seeds and linseed"
        assertEquals(82, name.length)
        assertEquals(
            "Organic wholegrain sourdough bread with sunflower seeds, pumpkin seeds and",
            ProductNames.of(product(name, "Baker")),
        )
        val shorter = "Organic wholegrain sourdough bread with sunflower seeds and pumpkin seeds"
        assertEquals(shorter, ProductNames.of(product(shorter, "The Village Bakery of Somewhere")))
        assertEquals("a".repeat(80), ProductNames.of(product("a".repeat(95))))
    }

    @Test
    fun theCategoryComesFromWholeTagsInAFixedOrder() {
        assertEquals(Category.CONDIMENT, ProductCategories.of(listOf("en:breakfasts", "en:spreads", "en:sweet-spreads")))
        assertEquals(Category.BEVERAGE, ProductCategories.of(listOf("en:beverages", "en:sodas", "en:colas")))
        // A drinking yoghurt is a dairy, not a beverage.
        assertEquals(Category.DAIRY, ProductCategories.of(listOf("en:dairies", "en:yogurts", "en:beverages")))
        assertEquals(Category.MEAT, ProductCategories.of(listOf("en:fishes", "en:fatty-fishes")))
        assertEquals(Category.BAKERY, ProductCategories.of(listOf("EN:Breads")))
        assertEquals(Category.PRODUCE, ProductCategories.of(listOf("en:plant-based-foods", "en:fruits", "en:apples")))
        // A part of a tag does not match: a meat analogue is not meat.
        assertNull(ProductCategories.of(listOf("en:meat-analogues", "en:meat-alternatives", "en:plant-based-foods")))
        assertNull(ProductCategories.of(listOf("fr:pates-a-tartiner")))
        assertNull(ProductCategories.of(emptyList()))
    }

    @Test
    fun aFoodMatchesWhenEachWordOfItsNameIsInTheProductName() {
        assertEquals("fk-33", FoodMatch.find("Greek Style Yogurt", Category.DAIRY, foods)?.id)
        assertEquals("fk-1", FoodMatch.find("Salted Butter", Category.DAIRY, foods)?.id)
        // The food with more words wins.
        assertEquals("fk-387", FoodMatch.find("Smooth Peanut Butter", null, foods)?.id)
        // A word with "s" at the end is the same word.
        assertEquals("fk-21", FoodMatch.find("Free range egg", Category.DAIRY, foods)?.id)
        // A part of a word does not match.
        assertNull(FoodMatch.find("Buttermilk pancakes", null, foods))
    }

    @Test
    fun theCategoryOfTheTagsLimitsTheFoods() {
        // The milk of the dairy category, not the canned milk.
        assertEquals("fk-27", FoodMatch.find("Whole Milk", Category.DAIRY, foods)?.id)
        // Peanut butter is "other" in the table, so a spread does not get it.
        assertNull(FoodMatch.find("Smooth Peanut Butter", Category.CONDIMENT, foods))
    }

    @Test
    fun twoDifferentFoodsInTheNameGiveNoFood() {
        assertNull(FoodMatch.find("Milk Chocolate", null, foods))
        assertNull(FoodMatch.find("Butter Chicken", null, foods))
        assertNull(FoodMatch.find("Cocoa", null, foods))
        assertNull(FoodMatch.find("", null, foods))
    }

    @Test
    fun theKeywordsChooseBetweenFoodsWithTheSameName() {
        assertEquals("fk-4", FoodMatch.find("Grated Parmesan Cheese", Category.DAIRY, foods)?.id)
        assertEquals("fk-7", FoodMatch.find("French Brie Cheese", Category.DAIRY, foods)?.id)
        // No keyword decides: the first food of the name in the table.
        assertEquals("fk-3", FoodMatch.find("Cheese", Category.DAIRY, foods)?.id)
        assertEquals("fk-21", FoodMatch.find("Large Eggs", Category.DAIRY, foods)?.id)
    }

    @Test
    fun theQuantityOfAPackageIsReadOnlyWhenItIsReliable() {
        assertEquals(400_000L to MeasureUnit.G, PackQuantity.parse("400 g e"))
        assertEquals(400_000L to MeasureUnit.G, PackQuantity.parse("400 g ℮"))
        assertEquals(500_000L to MeasureUnit.G, PackQuantity.parse("500g"))
        assertEquals(250_000L to MeasureUnit.G, PackQuantity.parse("250 gr."))
        assertEquals(1_500L to MeasureUnit.L, PackQuantity.parse("1,5 L"))
        assertEquals(1_000L to MeasureUnit.KG, PackQuantity.parse(" 1 kg "))
        assertEquals(330_000L to MeasureUnit.ML, PackQuantity.parse("330 ml"))
        assertEquals(700_000L to MeasureUnit.ML, PackQuantity.parse("70 cl"))
        assertEquals(200_000L to MeasureUnit.ML, PackQuantity.parse("2 dl"))
        assertNull(PackQuantity.parse("6 x 150 g"))
        assertNull(PackQuantity.parse("144 fl oz"))
        assertNull(PackQuantity.parse("1.000 g"))
        assertNull(PackQuantity.parse("0 g"))
        assertNull(PackQuantity.parse("100000 g"))
        assertNull(PackQuantity.parse("12 eggs"))
        assertNull(PackQuantity.parse(""))
    }

    @Test
    fun aFoundProductGivesTheValuesOfTheForm() {
        assertEquals(
            Scanned("3017620422003", ScanSource.FOUND, "Nutella", "condiment", null, 400_000, "g"),
            ScannedFoods.found("3017620422003", product("Nutella", "Ferrero, Nutella", "400 g e", listOf("en:spreads")), foods),
        )
        val messy = product("GREEK  STYLE\tYOGURT\n", " ,  BIO&amp;GUT  , Hofmolkerei", "6 x 150 g", listOf("en:dairies", "en:yogurts", "en:beverages"))
        assertEquals(
            Scanned("4006381333931", ScanSource.FOUND, "Bio&Gut Greek Style Yogurt", "dairy", "fk-33", null, null),
            ScannedFoods.found("4006381333931", messy, foods),
        )
        // With no tags, the food gives the category.
        assertEquals("dairy", ScannedFoods.found("1", product("Whole milk"), foods).category)
        assertNull(ScannedFoods.found("1", product("Mystery snack"), foods).category)
    }

    @Test
    fun aBarcodeOfAProductInTheInventoryFindsTheProduct() {
        val upcA = Product("p-1", "Cola", Category.BEVERAGE, null, barcode = "036000291452", foodId = null)
        val ean = Product("p-2", "Water", Category.BEVERAGE, null, barcode = "5449000000996", foodId = null)
        val none = Product("p-3", "Rice", Category.OTHER, null, barcode = null, foodId = null)
        val products = listOf(none, upcA, ean)
        assertEquals(upcA, ScannedFoods.productWithBarcode(products, "0036000291452"))
        assertEquals(ean, ScannedFoods.productWithBarcode(products, "5449000000996"))
        assertNull(ScannedFoods.productWithBarcode(products, "3017620422003"))
    }
}
