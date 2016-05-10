package com.beust.kobalt.api.annotation

/**
 * Plugins that export directives should annotated those with this annotation so they can be documented and also
 * receive special treatment for auto completion in the plug-in.
 */
annotation class Directive

@Retention(AnnotationRetention.RUNTIME)
annotation class Task(
    /* This task's name */
    val name: String,

    /* The documentation for this task */
    val description: String = "",

    /** Used to show the task in the correct group in the IDE */
    val group: String = "other",

    /** Dependency: tasks this task depends on */
    val dependsOn: Array<String> = arrayOf(),

    /** Dependency: tasks this task will be made dependend upon */
    val reverseDependsOn: Array<String> = arrayOf(),

    /** Ordering: tasks that need to be run before this one */
    val runBefore: Array<String> = arrayOf(),

    /** Ordering: tasks this task runs after */
    val runAfter: Array<String> = arrayOf(),

    /** Wrapper tasks */
    val alwaysRunAfter: Array<String> = arrayOf()
)

@Retention(AnnotationRetention.RUNTIME)
annotation class IncrementalTask(
    /* This task's name */
    val name: String,

    /* The documentation for this task */
    val description: String = "",

    /** Used to show the task in the correct group in the IDE */
    val group: String = "other",

    /** Dependency: tasks this task depends on */
    val dependsOn: Array<String> = arrayOf(),

    /** Dependency: tasks this task will be made dependend upon */
    val reverseDependsOn: Array<String> = arrayOf(),

    /** Tasks that this task depends on */
    val runBefore: Array<String> = arrayOf(),

    /** Ordering: tasks this task runs after */
    val runAfter: Array<String> = arrayOf(),

    /** Wrapper tasks */
    val alwaysRunAfter: Array<String> = arrayOf()
)

/**
 * Plugins that export properties should annotate those with this annotation so they can be documented.
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ExportedPluginProperty(
    /** Documentation for this property */
    val doc: String = "",

    /** The type of this property */
    val type: String = ""
)

/**
 * Plugins that export properties on the Project instance should annotate those with this annotation so
 * they can be documented.
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ExportedProjectProperty(
        /** Documentation for this property */
        val doc: String = "",

        /** The type of this property */
        val type: String = ""
)
