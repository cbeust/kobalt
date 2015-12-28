package com.beust.kobalt.misc

fun benchmarkMillis(run: () -> Unit): Long {
    val start = System.currentTimeMillis()
    run()
    return System.currentTimeMillis() - start
}

fun benchmarkSeconds(run: () -> Unit) = benchmarkMillis(run) / 1000



