package com.beust.kobalt

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

class TestConfig(val project: Project) {
    fun args(vararg arg: String) {
        project.testArgs.addAll(arg)
    }

    fun jvmArgs(vararg arg: String) {
        project.testJvmArgs.addAll(arg)
    }

    fun includes(vararg arg: String) {
        project.testIncludes.apply {
            clear()
            addAll(arg)
        }
    }

    fun excludes(vararg arg: String) {
        project.testExcludes.apply {
            clear()
            addAll(arg)
        }
    }
}

@Directive
fun Project.test(init: TestConfig.() -> Unit) = TestConfig(this).apply { init() }
