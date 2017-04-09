package com.beust.kobalt.maven.aether

import com.beust.kobalt.misc.kobaltLog
import org.eclipse.aether.graph.DependencyFilter
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.util.artifact.JavaScopes

object Filters {
    val COMPILE_FILTER = DependencyFilter { p0, p1 ->
        p0.dependency.scope == "" || p0.dependency.scope == JavaScopes.COMPILE
    }
    val TEST_FILTER = DependencyFilter { p0, p1 -> p0.dependency.scope == JavaScopes.TEST }

    val EXCLUDE_OPTIONAL_FILTER = object: DependencyFilter {
        override fun accept(p0: DependencyNode, p1: MutableList<DependencyNode>): Boolean {
            val result = p0.dependency != null && ! p0.dependency.optional
            if (! result) {
                kobaltLog(3, "Excluding from optional filter: $p0")
            }
            return result
        }

        override fun toString() = "EXCLUDE_OPTIONAL_FILTER"
    }
}
