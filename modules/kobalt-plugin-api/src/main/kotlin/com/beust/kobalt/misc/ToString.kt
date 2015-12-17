package com.beust.kobalt.misc

fun <T> toString(name: String, vararg o: T) : String {
    val sb = StringBuilder()

    var i = 0
    while (i < o.size) {
        if (i > 0) sb.append(", ")
        sb.append(o[i].toString() + ":" + o[i + 1])
        i += 2
    }
    return "{$name $sb}"
}
