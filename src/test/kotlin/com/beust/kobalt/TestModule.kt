package com.beust.kobalt

import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.MainModule
import com.beust.kobalt.plugin.java.SystemProperties
import com.google.inject.Inject
import com.google.inject.Scopes
import java.io.File

class TestLocalRepo: LocalRepo(localRepo = SystemProperties.homeDir + File.separatorChar + ".kobalt-test")

public class TestModule @Inject constructor(args: Args) : MainModule(args) {
    override fun configureTest() {
        bind(LocalRepo::class.java).to(TestLocalRepo::class.java).`in`(Scopes.SINGLETON)
    }
}
