package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import com.beust.kobalt.api.BuildConfigField
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.BaseProjectInfo
import com.google.inject.Singleton

@Singleton
class KotlinProjectInfo : BaseProjectInfo {
    override val sourceDirectory = "kotlin"
    override val defaultSourceDirectories = hashSetOf("src/main/kotlin", "src/main/resources", "src/main/res")
    override val defaultTestDirectories = hashSetOf("src/test/kotlin", "src/test/resources", "src/test/res")

    override fun generate(field: BuildConfigField) = with(field) {
        "        val $name : $type = $value"
    }

    override fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String, variant: Variant,
                                     buildConfigs: List<BuildConfig>): String {
        val lines = arrayListOf<String>()
        with(lines) {
            add("package $packageName")
            add("")
            add("class BuildConfig {")
            add("    companion object {")
            add(generate("String", "PRODUCT_FLAVOR", "\"" + variant.productFlavor.name + "\""))
            add(generate("String", "BUILD_TYPE", "\"" + variant.buildType.name + "\""))
            add(generate("String", "DEBUG",
                    if (variant.productFlavor.name.equals("debug", ignoreCase = true)) {
                        "true"
                    } else {
                        "false"
                    }))

            addAll(generateFieldsFromContributors(project, context))

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

