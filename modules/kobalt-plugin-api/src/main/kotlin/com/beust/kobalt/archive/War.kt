package com.beust.kobalt.archive

import com.beust.kobalt.glob

class War(override var name: String? = null) : Jar(name), AttributeHolder {
    init {
        include(from("src/main/webapp"),to(""), glob("**"))
        include(from("kobaltBuild/classes"), to("WEB-INF/classes"), glob("**"))
    }
}

