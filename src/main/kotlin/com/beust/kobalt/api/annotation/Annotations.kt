package com.beust.kobalt.api.annotation

/**
 * Plugins that export directives should annotated those with this annotation so they can be documented and also
 * receive special treatment for auto completion in the plug-in.
 */
annotation class Directive

@Retention(AnnotationRetention.RUNTIME)
annotation class Task(val name: String,
        val description: String,

        /** Tasks that this task depends on */
        val runBefore: Array<String> = arrayOf(),

        /** Tasks that this task will run after if they get run */
        val runAfter: Array<String> = arrayOf(),

        /** Tasks that this task will always run after */
        val alwaysRunAfter: Array<String> = arrayOf()
)

/**
 * Plugins that export properties should annotated those with this annotation so they can be documented.
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ExportedProperty