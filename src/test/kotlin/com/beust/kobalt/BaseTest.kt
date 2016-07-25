package com.beust.kobalt

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.maven.aether.KobaltAether
import org.testng.annotations.BeforeClass

open class BaseTest(open val aether: KobaltAether) {
    val context = KobaltContext(Args())

    @BeforeClass
    fun bc() {
        context.aether = aether
    }
}