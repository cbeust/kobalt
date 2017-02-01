package com.beust.kobalt.maven.aether

import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.util.artifact.JavaScopes

object Filters {
    val COMPILE_FILTER = DependencyFilter { p0, p1 ->
        p0.dependency.scope == "" || p0.dependency.scope == JavaScopes.COMPILE
    }
    val TEST_FILTER = DependencyFilter { p0, p1 -> p0.dependency.scope == JavaScopes.TEST }

    val EXCLUDE_OPTIONAL_FILTER = DependencyFilter { p0, p1 ->
        ! p0.dependency.optional
    }
}
