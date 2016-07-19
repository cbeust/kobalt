package com.beust.kobalt.misc

fun <T> benchmarkMillis(run: () -> T) : Pair<Long, T> {
    val start = System.currentTimeMillis()
    val result = run()
    return Pair(System.currentTimeMillis() - start, result)
}

fun <T> benchmarkSeconds(run: () -> T) : Pair<Long, T> {
    val result = benchmarkMillis(run)
    return Pair(result.first / 1000, result.second)
}



