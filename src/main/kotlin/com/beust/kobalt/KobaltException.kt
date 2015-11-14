package com.beust.kobalt

class KobaltException(s: String? = null, ex: Throwable? = null, val docUrl: String? = null)
        : RuntimeException(s, ex) {
    constructor(ex: Throwable?) : this(null, ex, null)
}

