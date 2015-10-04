package com.beust.kobalt.misc

public class ToString<T>(val name: String, vararg o: T) {
    val sb = StringBuffer()

    init {
        for (i in 0..o.size() - 1 step 2) {
            if (i > 0) sb.append(", ")
            sb.append(o.get(i).toString() + ":" + o.get(i + 1))
        }
    }

    val s : String get() = "{${name} ${sb}}"
}
