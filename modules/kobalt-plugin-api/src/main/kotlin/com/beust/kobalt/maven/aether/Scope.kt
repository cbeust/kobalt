package com.beust.kobalt.maven.aether

import com.beust.kobalt.api.IClasspathDependency
import com.beust.kobalt.api.Project
import org.eclipse.aether.util.artifact.JavaScopes

sealed class Scope(val scope: String, val dependencyLambda: (Project) -> List<IClasspathDependency>) {

    companion object {
        fun toScopes(isTest: Boolean) = if (isTest) listOf(TEST, COMPILE) else listOf(COMPILE)
    }

    object COMPILE : Scope(JavaScopes.COMPILE, Project::compileDependencies)
    object PROVIDED : Scope(JavaScopes.PROVIDED, Project::compileProvidedDependencies)
    object COMPILEONLY : Scope("compileOnly", Project::compileOnlyDependencies)
    object SYSTEM : Scope(JavaScopes.SYSTEM, { project -> emptyList() })
    object RUNTIME : Scope(JavaScopes.RUNTIME, Project::compileRuntimeDependencies)
    object TEST : Scope(JavaScopes.TEST, Project::testDependencies)
}