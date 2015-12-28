package com.beust.kobalt.plugin.java

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import com.beust.kobalt.api.BuildConfigField
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.BaseProjectInfo
import com.google.inject.Singleton

@Singleton
class JavaProjectInfo : BaseProjectInfo {
    override val sourceDirectory = "java"
    override val defaultSourceDirectories = hashSetOf("src/main/java", "src/main/resources", "src/main/res")
    override val defaultTestDirectories = hashSetOf("src/test/java", "src/test/resources", "src/test/res")

    override fun generate(field: BuildConfigField) = with(field) {
        "    public static final $type $name = $value;"
    }

    override fun generateBuildConfig(project: Project, context: KobaltContext, packageName: String, variant: Variant,
                                     buildConfigs: List<BuildConfig>): String {
        val lines = arrayListOf<String>()
        with(lines) {
            add("package $packageName;")
            add("")
            add("public final class BuildConfig {")
            add(generate("String", "FLAVOR", "\"" + variant.productFlavor.name + "\""))
            add(generate("String", "BUILD_TYPE", "\"" + variant.buildType.name + "\""))
            add(generate("boolean", "DEBUG",
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
            add("}")
            add("")
        }

        return lines.joinToString("\n")
    }

}
