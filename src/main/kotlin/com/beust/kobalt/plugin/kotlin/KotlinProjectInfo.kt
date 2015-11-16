package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import com.beust.kobalt.internal.IProjectInfo
import com.google.inject.Singleton

@Singleton
class KotlinProjectInfo : IProjectInfo {
    override val sourceDirectory = "kotlin"
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin", "src/main/resources")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin", "src/test/resources")

    private fun generate(type: String, name: String, value: Any) =
            "        val $name : $type = $value"

    override fun generateBuildConfig(packageName: String, variant: Variant, buildConfigs: List<BuildConfig>) : String {
        val lines = arrayListOf<String>()
        with(lines) {
            add("package $packageName")
            add("")
            add("class BuildConfig {")
            add("    companion object {")
            if (variant.productFlavor != null) {
                add(generate("String", "PRODUCT_FLAVOR", "\"" + variant.productFlavor.name + "\""))
            }
            if (variant.buildType != null) {
                add(generate("String", "BUILD_TYPE", "\"" + variant.buildType.name + "\""))
            }
            buildConfigs.forEach {
                it.fields.forEach { field ->
                    add(generate(field.type, field.name, field.value))
                }
            }
            add("    }")
            add("}")
            add("")
        }

        return lines.joinToString("\n")
    }
}

