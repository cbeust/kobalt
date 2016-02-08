package com.beust.kobalt

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.buildType
import com.beust.kobalt.api.productFlavor
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.util.*

class VariantTest : KobaltTest() {

    @DataProvider(name = "projectVariants")
    fun projectVariants() = arrayOf(
            arrayOf(emptySet<String>(), Project().apply {
            }),
            arrayOf(hashSetOf("compileDev"), Project().apply {
                productFlavor("dev") {}
            }),
            arrayOf(hashSetOf("compileDev", "compileProd"), Project().apply {
                productFlavor("dev") {}
                productFlavor("prod") {}
            }),
            arrayOf(hashSetOf("compileDevDebug"), Project().apply {
                productFlavor("dev") {}
                buildType("debug") {}
            }),
            arrayOf(hashSetOf("compileDevRelease", "compileDevDebug", "compileProdDebug", "compileProdRelease"),
                    Project().apply {
                        productFlavor("dev") {}
                        productFlavor("prod") {}
                        buildType("debug") {}
                        buildType("release") {}
            })
            )

    @Test(dataProvider = "projectVariants", description =
            "Make sure we generate the correct dynamic tasks based on the product flavor and build types.")
    fun taskNamesShouldWork(expected: Set<String>, project: Project) {
        val variantNames = HashSet(Variant.allVariants(project).map { it.toTask("compile") })
        Assert.assertEquals(variantNames, expected)
    }
}
