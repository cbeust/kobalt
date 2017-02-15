package com.beust.kobalt

import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

@Directive
fun Project.test(init: TestConfig.() -> Unit): TestConfig = let { project ->
    with(testConfigs) {
        val tf = TestConfig(project).apply { init() }
        if (! map { it.name }.contains(tf.name)) {
            add(tf)
            tf
        } else {
            throw KobaltException("Test configuration \"${tf.name}\" already exists, give it a different "
                    + "name with test { name = ... }")
        }
    }
}
