package com.beust.kobalt.misc

public fun benchmark(message: String, run: () -> Unit) {
    val start = System.currentTimeMillis()
    run()
    println("############# Time to ${message}: ${System.currentTimeMillis() - start} ms")
}

