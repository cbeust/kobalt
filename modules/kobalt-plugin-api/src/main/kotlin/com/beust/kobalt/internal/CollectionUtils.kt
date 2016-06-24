package com.beust.kobalt.internal

fun <T> Collection<T>.doWhile(condition: (T) -> Boolean, action: (T) -> Unit) {
    var i = 0
    var done = false
    while (i < size && ! done) {
        elementAt(i).let { element ->
            if (! condition(element)) done = true
            else action(element)
        }
        i++
    }
}

