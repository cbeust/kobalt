package com.beust.kobalt.plugin.java

import com.beust.kobalt.Variant
import com.beust.kobalt.api.BuildConfig
import com.beust.kobalt.internal.IProjectInfo
import com.google.inject.Singleton

@Singleton
class JavaProjectInfo : IProjectInfo {
    override val sourceDirectory = "java"
    override val defaultSourceDirectories = hashSetOf("src/main/java", "src/main/resources")
    override val defaultTestDirectories = hashSetOf("src/test/java", "src/test/resources")

    private fun generate(type: String, name: String, value: Any) =
            "    public static $type $name = $value;"

    override fun generateBuildConfig(packageName: String, variant: Variant, buildConfigs: List<BuildConfig>) : String {
        val lines = arrayListOf<String>()
        with(lines) {
            add("package $packageName;")
            add("")
            add("public class BuildConfig {")
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
            add("}")
        }

        return lines.joinToString("\n")
    }

}
