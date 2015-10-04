package com.beust.kobalt.api.annotation

import kotlin.annotation.Retention

annotation class Directive

@Retention(AnnotationRetention.RUNTIME)
annotation class Task(val name: String,
        val description: String,
        val runBefore: Array<String> = arrayOf(),
        val runAfter: Array<String> = arrayOf())
