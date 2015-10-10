package com.beust.kobalt.api.annotation

import kotlin.annotation.Retention

annotation class Directive

@Retention(AnnotationRetention.RUNTIME)
annotation class Task(val name: String,
        val description: String,
        /** Tasks that this task depends on */
        val runBefore: Array<String> = arrayOf(),

        /** Tasks that this task will run after if they get run */
        val runAfter: Array<String> = arrayOf(),

        /** Tasks that this task will always run after */
        val wrapAfter: Array<String> = arrayOf()
)
