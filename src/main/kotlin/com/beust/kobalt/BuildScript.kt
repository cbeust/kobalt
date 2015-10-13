package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.SystemProperties
import java.io.File

@Directive
fun homeDir(vararg dirs: String) : String = SystemProperties.homeDir +
        File.separator + dirs.toArrayList().join(File.separator)

@Directive
fun file(file: String) : String = IClasspathDependency.PREFIX_FILE + file

@Directive
fun plugins(vararg dependency : IClasspathDependency) {
    Plugins.dynamicPlugins.addAll(dependency)
}

@Directive
fun plugins(vararg dependencies : String) {
    val executor = Kobalt.INJECTOR.getInstance(KobaltExecutors::class.java).miscExecutor
    val factory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)
    dependencies.forEach {
        Plugins.dynamicPlugins.add(factory.create(it, executor))
    }
}

@Directive
fun repos(vararg repos : String) {
    repos.forEach { Kobalt.addRepo(it) }
}

@Directive
fun glob(g: String) : IFileSpec.Glob = IFileSpec.Glob(g)
