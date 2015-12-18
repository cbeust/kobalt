package com.beust.kobalt.internal

/**
 * SpekRunner triggers if it finds a dependency on org.jetbrains.spek but other than that, it just
 * uses the regular JUnitRunner.
 */
public class SpekRunner() : JUnitRunner() {
    override val dependencyName = "org.jetbrains.spek"
}

