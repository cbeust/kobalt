package com.beust.kobalt

import com.beust.kobalt.api.Kobalt
import org.testng.annotations.BeforeSuite

open class KobaltTest: BaseTest() {
    companion object {
        @BeforeSuite
        fun bs() {
            Kobalt.INJECTOR = com.google.inject.Guice.createInjector(TestModule())
        }
    }
}