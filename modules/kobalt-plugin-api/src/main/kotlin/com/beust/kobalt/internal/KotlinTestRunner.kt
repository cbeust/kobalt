package com.beust.kobalt.internal

/**
 * KotlinTestRunner triggers if it finds a dependency on io.kotlintest but other than that, it just
 * uses the regular JUnitRunner.
 */
class KotlinTestRunner : JUnitRunner() {
    override val dependencyName = "io.kotlintest"
}

