package com.beust.kobalt.app

import com.beust.kobalt.Args
import com.beust.kobalt.internal.PluginInfo
import com.beust.kobalt.maven.ArtifactFetcher
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.maven.Pom
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.plugin.publish.JCenterApi
import com.google.inject.AbstractModule
import com.google.inject.Provider
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.google.inject.assistedinject.FactoryModuleBuilder
import java.util.concurrent.ExecutorService

public open class MainModule(val args: Args) : AbstractModule() {
    val executors = KobaltExecutors()

    open fun configureTest() {
        bind(LocalRepo::class.java)
    }

    override fun configure() {
        configureTest()
        val builder = FactoryModuleBuilder()
        arrayListOf(
                PomGenerator.IFactory::class.java,
                JCenterApi.IFactory::class.java,
                Pom.IFactory::class.java,
                BuildFileCompiler.IFactory::class.java,
                ArtifactFetcher.IFactory::class.java)
                .forEach {
                    install(builder.build(it))
                }

        //        bind(javaClass<TaskManager>()).toProvider(javaClass<TaskManagerProvider>())
        //                .`in`(Scopes.SINGLETON)
        bind(object : TypeLiteral<KobaltExecutors>() {}).toInstance(executors)
        bind(object : TypeLiteral<ExecutorService>() {}).annotatedWith(DependencyExecutor::class.java)
                .toInstance(executors.dependencyExecutor)
        bind(Args::class.java).toProvider(Provider<Args> {
            args
        })
        bind(PluginInfo::class.java).toProvider(Provider<PluginInfo> {
            PluginInfo.readKobaltPluginXml()
        }).`in`(Singleton::class.java)


        //        bindListener(Matchers.any(), object: TypeListener {
        //            override fun <I> hear(typeLiteral: TypeLiteral<I>?, typeEncounter: TypeEncounter<I>?) {
        //                val bean = object: InjectionListener<I> {
        //                    override public fun afterInjection(injectee: I) {
        //                        if (Scopes.isCircularProxy(injectee)) {
        //                            println("CYCLE: " + typeLiteral?.getRawType()?.getName());
        //                        }
        //                    }
        //                }
        //                typeEncounter?.register(bean)
        //            }
        //        })
    }
}