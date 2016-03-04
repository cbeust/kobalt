package com.beust.kobalt.archive

import com.beust.kobalt.api.annotation.Directive

class Manifest(val jar: AttributeHolder) {
    @Directive
    fun attributes(k: String, v: String) {
        jar.addAttribute(k, v)
    }
}

