package com.beust.kobalt.archive

import com.beust.kobalt.api.Project
import com.beust.kobalt.glob

class War(override val project: Project, override var name: String = Archives.defaultArchiveName(project) + ".war")
        : Jar(project, name), AttributeHolder {
    init {
        include(from("src/main/webapp"), to(""), glob("**"))
        include(from("kobaltBuild/classes"), to("WEB-INF/classes"), glob("**"))
    }
}

