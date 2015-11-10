package com.beust.kobalt.misc

public fun benchmark(message: String, run: () -> Unit) : Long {
    val start = System.currentTimeMillis()
    run()
    return (System.currentTimeMillis() - start) / 1000
}

