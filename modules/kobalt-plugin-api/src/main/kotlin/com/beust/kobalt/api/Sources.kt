package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive
import java.util.HashSet

class Sources(val project: Project, val sources: HashSet<String>) {
    @Directive
    fun path(vararg paths: String) {
        sources.addAll(paths)
    }
}