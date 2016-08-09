package com.beust.kobalt

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.app.MainModule
import com.beust.kobalt.internal.ILogger
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.KobaltSettingsXml
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.aether.Aether
import com.beust.kobalt.maven.aether.KobaltAether
import com.google.common.eventbus.EventBus
import com.google.inject.Provider
import com.google.inject.Scopes
import java.io.File

val LOCAL_CACHE = File(SystemProperties.homeDir + File.separatorChar + ".kobalt-test")

val TEST_KOBALT_SETTINGS = KobaltSettings(KobaltSettingsXml()).apply {
    localCache = LOCAL_CACHE
}

class TestLocalRepo: LocalRepo(TEST_KOBALT_SETTINGS)

class TestModule : MainModule(Args(), TEST_KOBALT_SETTINGS) {
    override fun configureTest() {
        val localRepo = TestLocalRepo()
        bind(LocalRepo::class.java).toInstance(localRepo)
        val localAether = Aether(LOCAL_CACHE, TEST_KOBALT_SETTINGS, EventBus())
        val testAether = KobaltAether(KobaltSettings(KobaltSettingsXml()), localAether)
        bind(KobaltAether::class.java).to(testAether)
        bind(KobaltContext::class.java).toProvider(Provider<KobaltContext> {
            KobaltContext(args).apply {
                aether = testAether
                logger = object: ILogger {
                    override fun log(tag: CharSequence, level: Int, message: CharSequence, newLine: Boolean) {
                        println("TestLog: [$tag $level] " + message)
                    }
                }
            }
        }).`in`(Scopes.SINGLETON)
    }
}
