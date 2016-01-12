package com.beust.kobalt

import com.beust.kobalt.app.MainModule
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.KobaltSettingsXml
import com.beust.kobalt.maven.LocalRepo
import com.google.inject.Scopes
import java.io.File

val TEST_KOBALT_SETTINGS = KobaltSettings(KobaltSettingsXml()).apply {
    localRepo = SystemProperties.homeDir + File.separatorChar + "" +
            ".kobalt-test"
}

class TestLocalRepo: LocalRepo(TEST_KOBALT_SETTINGS)

public class TestModule : MainModule(Args(), TEST_KOBALT_SETTINGS) {
    override fun configureTest() {
        bind(LocalRepo::class.java).to(TestLocalRepo::class.java).`in`(Scopes.SINGLETON)
    }
}
