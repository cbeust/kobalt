package com.beust.kobalt

import com.beust.kobalt.Args
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.app.MainModule
import com.google.inject.Scopes
import java.io.File

class TestLocalRepo: LocalRepo(localRepo = SystemProperties.homeDir + File.separatorChar + ".kobalt-test")

public class TestModule : MainModule(Args()) {
    override fun configureTest() {
        bind(LocalRepo::class.java).to(TestLocalRepo::class.java).`in`(Scopes.SINGLETON)
    }
}
