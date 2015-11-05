package com.beust.kobalt.misc

fun <T> toString(name: String, vararg o: T) : String {
    val sb = StringBuilder()

    for (i in 0..o.size - 1 step 2) {
        if (i > 0) sb.append(", ")
        sb.append(o[i].toString() + ":" + o[i + 1])
    }
    return "{$name $sb}"
}
