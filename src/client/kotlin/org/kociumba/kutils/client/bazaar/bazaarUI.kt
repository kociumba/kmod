package org.kociumba.kutils.client.bazaar

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTableFlags
import imgui.type.ImDouble
import imgui.type.ImInt
import imgui.type.ImString
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.text.Text
import org.kociumba.kutils.client.imgui.ImGuiKutilsTheme
import org.kociumba.kutils.client.imgui.coloredText
import org.kociumba.kutils.log
import xyz.breadloaf.imguimc.Imguimc
import xyz.breadloaf.imguimc.screen.ImGuiScreen
import xyz.breadloaf.imguimc.screen.ImGuiWindow
import java.awt.Color
import java.lang.reflect.Field
import kotlin.Double

/**
 * Types of smoothing functions that can be applied to the data
 */
enum class SmoothingTypes(val displayName: String) {
    SIGMOID("Sigmoid"),
    TANH("Tanh"),
    SATURATING("Saturating"),
    PIECEWISE("Piecewise"),
    NONE("None"),
}

var products: Map<String, Product> = emptyMap()

/**
 * Main bazaar related features ui
 *
 * The predictions are a little buggy so far but everything works fine
 * search is fucked tho for some reason
 *
 * Spent almost a whole day comparing this 1 to 1 with the go version
 * everything is essentially the same, but the predictions are still fucked.
 * At this point honestly think it's some edge cases with the kotlinx serialization 🤷
 */
@Environment(EnvType.CLIENT)
object bazaarUI: ImGuiScreen(Text.literal("BazaarUI"), true) {
    // default sigmoid for best results
    var smoothingType = ImInt(SmoothingTypes.SIGMOID.ordinal)
    var priceLimit = ImDouble(0.toDouble()) // start as 0 to not show in ui
    var priceLimitIf0 = 1e32
    var weeklySalesLimit = ImDouble(0.toDouble())
    var weeklySalesLimitIf0 = 1e32
    // default 10 results to display
    var displayResults = ImInt(10)
    var searchQuery = ImString("", 256)
    var displayList: MutableList<Product> = mutableListOf()

    const val minPrice = 100
    const val minWeeklyVolume = 10

    private lateinit var alreadyInitialisedField: Field

    /**
     * Hacky reflection shenanigans, but we need it to not duplicate imgui windows in memory
     */
    init {
        try {
            alreadyInitialisedField = ImGuiScreen::class.java.getDeclaredField("alreadyInitialised")
            alreadyInitialisedField.isAccessible = true
        } catch (e: Exception) {
            log.error("Failed to get alreadyInitialised field", e)
        }
    }

    private fun setAlreadyInitialised(value: Boolean) {
        try {
            alreadyInitialisedField.setBoolean(this, value)
        } catch (e: Exception) {
            log.error("Failed to set alreadyInitialised", e)
        }
    }

    /**
     * main ui render entry point
     */
    override fun initImGui(): List<ImGuiWindow?>? {
        return listOf(
            ImGuiWindow(
                ImGuiKutilsTheme(),
                Text.literal("Bazaar Settings"),
                {
                    ImGui.setWindowPos(50f, 50f, ImGuiCond.Once)
                    ImGui.setWindowSize(400f, 200f, ImGuiCond.Once)

                    ImGui.text("Smoothing Type")
                    ImGui.sameLine()
                    ImGui.combo(
                        "##smoothingType",
                        smoothingType,
                        SmoothingTypes.entries.map { it.displayName }.toTypedArray()
                    )
                    ImGui.text("Price Limit")
                    ImGui.sameLine()
                    ImGui.inputDouble("##priceLimit", priceLimit)
                    ImGui.text("Weekly Sales Limit")
                    ImGui.sameLine()
                    ImGui.inputDouble("##weeklySalesLimit", weeklySalesLimit)
                    ImGui.text("Search")
                    ImGui.sameLine()
                    ImGui.inputText("##search", searchQuery) // fucked for whatever reason, can not input text
                    ImGui.text("Get and calculate data")
                    ImGui.sameLine()
                    if (ImGui.button("Calculate")) {
                        try {
                            log.info("Getting bazaar data...")
                            var b = BazaarAPI.getBazaar()
                            products = emptyMap()
                            // doing this couse of how this is displayed, might use something else
                            if (priceLimit.get() == 0.0) {
                                priceLimit.set(priceLimitIf0)
                            }
                            if (weeklySalesLimit.get() == 0.0) {
                                weeklySalesLimit.set(weeklySalesLimitIf0)
                            }
//                            b.products.filter { (_, p) ->
//                                productFilter(p)
//                            }.forEach { products[it.key] = it.value }

                            // store for later
                            products = b.products
                            displayList = productFilter(b.products)

                            // reset the display values
                            if (priceLimit.get() == 1e32) {
                                priceLimit.set(0.0)
                            }
                            if (weeklySalesLimit.get() == 1e32) {
                                weeklySalesLimit.set(0.0)
                            }
                            log.info("Got ${products.size} products")
                        } catch (e: Exception) {
                            log.error("Something went wrong while filtering the bazaar data", e)
                        }
                    }
                },
                false,
            ),
            ImGuiWindow(
                ImGuiKutilsTheme(),
                Text.literal("Bazaar Results"),
                {
                    ImGui.setWindowPos(500f, 50f, ImGuiCond.Once)
                    ImGui.setWindowSize(800f, 900f, ImGuiCond.Once)

                    ImGui.text("Bazaar data")
                    if (ImGui.beginTable("##bazaarTable", 6, ImGuiTableFlags.Borders or ImGuiTableFlags.Resizable)) {
                        ImGui.tableSetupColumn("Product", ImGuiTableFlags.Sortable)
                        ImGui.tableSetupColumn("Sell Price", ImGuiTableFlags.Sortable)
                        ImGui.tableSetupColumn("Buy Price", ImGuiTableFlags.Sortable)
                        ImGui.tableSetupColumn("Difference", ImGuiTableFlags.Sortable)
                        ImGui.tableSetupColumn("Weekly traffic", ImGuiTableFlags.Sortable)
                        ImGui.tableSetupColumn("Prediction/Confidence", ImGuiTableFlags.Sortable)
                        ImGui.tableHeadersRow()
                        try {
                            // Filter products based on the search query
//                            val filteredProducts = products.filter { (_, p) ->
//                                p.product_id.contains(searchQuery.get(), ignoreCase = true)
//                            }

//                            filteredProducts.forEach { (_, p) ->
//                                renderProductRow(p)
//                            }

                            displayList.forEach { p ->
                                renderProductRow(p)
                            }
                        } catch (e: Exception) {
                            log.error("Something went wrong while trying to display the bazaar data", e)
                        }
                        ImGui.endTable()
                    }
                },
                false,
            )
        )
    }

    // direct port of the go version
    fun getDiff(p: Product): Double {
        return -(p.quick_status.sellPrice - p.quick_status.buyPrice)
    }

    // direct port of the go version
    fun productFilter(p: Map<String, Product>): MutableList<Product> {
        val filtered: MutableList<Product> = mutableListOf()
        for (v in p) {
            if (isProductEligible(v.value)) {
                filtered.addLast(v.value)
            }
        }

        filtered.sortByDescending { getDiff(it) }

        // omit this to show everything in this version
//        if (filtered.size > displayResults.get()) {
//            filtered.subList(displayResults.get(), filtered.size).clear()
//        }

        return filtered
    }

    // direct port of the go version, double-checked it
    fun isProductEligible(p: Product): Boolean {
        if (searchQuery.get() != "") {
            if (!p.product_id.lowercase().contains(searchQuery.get().lowercase())) {
                return false
            }
            return (p.quick_status.buyPrice < priceLimit.get() || priceLimit.get() == 1e32) &&
                    (p.quick_status.sellMovingWeek > weeklySalesLimit.get() || weeklySalesLimit.get() == 1e32)
        }

        return p.quick_status.buyPrice > minPrice &&
                p.quick_status.sellPrice > minPrice &&
                p.quick_status.sellMovingWeek > minWeeklyVolume &&
                p.quick_status.buyMovingWeek > minWeeklyVolume &&
                (p.quick_status.buyPrice < priceLimit.get() || priceLimit.get() == 1e32) &&
                p.quick_status.sellMovingWeek > weeklySalesLimit.get() || weeklySalesLimit.get() == 1e32
    }

    private fun renderProductRow(p: Product) {
        ImGui.tableNextRow()

        // Product ID
        ImGui.tableNextColumn()
        coloredText("#cba6f7", p.product_id)

        // Sell Price
        ImGui.tableNextColumn()
        coloredText("#94e2d5", "%.2f".format(p.quick_status.sellPrice))

        // Buy Price
        ImGui.tableNextColumn()
        coloredText("#eba0ac", "%.2f".format(p.quick_status.buyPrice))

        // Difference
        ImGui.tableNextColumn()
        val difference = p.quick_status.buyPrice - p.quick_status.sellPrice
        coloredText("#89b4fa", "%.2f".format(difference))

        // Weekly traffic
        ImGui.tableNextColumn()
        coloredText("#fab387", "Sell: ${p.quick_status.sellMovingWeek}, Buy: ${p.quick_status.buyMovingWeek}")

        // Prediction
        ImGui.tableNextColumn()
//        val (prediction, confidence) = getPred(mapOf("key" to p).entries.first())
        val (prediction, confidence) = BazaarMath.getPrediction(p, SmoothingTypes.entries[smoothingType.get()])
        val predictionText = when {
            prediction > 0 -> "+ %.2f/%.2f%%".format(prediction, confidence)
            prediction < 0 -> "- %.2f/%.2f%%".format(prediction, confidence)
            else -> "N/A"
        }
        val predictionColor = when {
            prediction > 0 -> "#a6e3a1"  // Green for positive
            prediction < 0 -> "#f38ba8"  // Red for negative
            else -> "#ffffff"  // White for N/A
        }
        coloredText(predictionColor, predictionText)
    }



    fun reset() {
        log.debug("reset called")
//        smoothingType.set(SmoothingTypes.SIGMOID.ordinal)
//        priceLimit.set(1e32)
//        weeklySalesLimit.set(1e32)
//        displayResults.set(10)
        setAlreadyInitialised(false)
    }
}