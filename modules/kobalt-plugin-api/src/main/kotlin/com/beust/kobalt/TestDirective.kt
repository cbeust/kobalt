package com.beust.kobalt

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

class TestConfig(val project: Project) {
    val testArgs = arrayListOf<String>()
    val jvmArgs = arrayListOf<String>()
    val testIncludes = arrayListOf("**/*Test.class")
    val testExcludes = arrayListOf<String>()

    fun args(vararg arg: String) {
        testArgs.addAll(arg)
    }

    fun jvmArgs(vararg arg: String) {
        jvmArgs.addAll(arg)
    }

    fun includes(vararg arg: String) {
        testIncludes.apply {
            clear()
            addAll(arg)
        }
    }

    fun excludes(vararg arg: String) {
        testExcludes.apply {
            clear()
            addAll(arg)
        }
    }
}

@Directive
fun Project.test(init: TestConfig.() -> Unit) = let { project ->
    with(this.testConfigs) {
        clear()
        add(TestConfig(project).apply { init() })
    }
}
