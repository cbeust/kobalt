package com.beust.kobalt.misc

public fun benchmark(run: () -> Unit) : Long {
    val start = System.currentTimeMillis()
    run()
    return (System.currentTimeMillis() - start) / 1000
}

