package com.beust.kobalt.misc

fun toString<T>(name: String, vararg o: T) : String {
    val sb = StringBuffer()

    for (i in 0..o.size - 1 step 2) {
        if (i > 0) sb.append(", ")
        sb.append(o.get(i).toString() + ":" + o.get(i + 1))
    }
    return "{$name $sb}"
}
