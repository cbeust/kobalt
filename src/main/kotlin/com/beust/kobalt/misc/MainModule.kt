package com.beust.kobalt.misc

import com.google.inject.BindingAnnotation

//@Singleton
//class TaskManagerProvider @Inject constructor(val plugins: Plugins) : Provider<TaskManager> {
//    override fun get(): TaskManager? {
//        return TaskManager(plugins)
//    }
//}

@BindingAnnotation
@Retention(AnnotationRetention.RUNTIME)
annotation class DependencyExecutor

