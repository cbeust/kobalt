package com.beust.kobalt

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.addKotlinSourceRoot
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.script.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.lang.RuntimeException
import kotlin.reflect.*

fun main(args: Array<String>) {
    Scripting().run()
}

class Scripting {
    fun run() {
        val aClass = compileScript("fib.kts", ScriptWithIntParam::class, null)
        println("CLASS: " + aClass)
//        Assert.assertNotNull(aClass)
        if (aClass != null) {
            val result = aClass.getConstructor(Integer.TYPE).newInstance(5)
            println("Result: $result")
        } else {
            println("ERROR")
        }
    }

    private fun compileScript(
            scriptPath: String,
            scriptBase: KClass<out Any>,
            environment: Map<String, Any?>? = null,
            runIsolated: Boolean = true,
            suppressOutput: Boolean = false): Class<*>? =
            compileScriptImpl("/Users/beust/kotlin/kobalt/scripts/" + scriptPath, KobaltDescription(scriptBase),
                    true, false)
//                    KotlinScriptDefinitionFromTemplate(scriptBase, null, null, environment), runIsolated, suppressOutput)

    private fun compileScriptImpl(
            scriptPath: String,
            scriptDefinition: KotlinScriptDefinition,
            runIsolated: Boolean,
            suppressOutput: Boolean): Class<*>?
    {
        val resolver = object: ScriptDependenciesResolver {
//            override fun resolve(script: ScriptContents,
//                    environment: Map<String, Any?>?,
//                    report: (ReportSeverity, String, ScriptContents.Position?) -> Unit,
//                    previousDependencies: KotlinScriptExternalDependencies?
//            ): Future<KotlinScriptExternalDependencies?> = PseudoFuture(null) {
//                return PseudoFuture(null)
//        }
    }

        val paths = PathUtil.getKotlinPathsForDistDirectory()
        val messageCollector =
                if (suppressOutput) MessageCollector.NONE
                else PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)

        val rootDisposable = Disposer.newDisposable()
        try {
            val configuration = CompilerConfiguration()
//            val configuration = KotlinTestUtils.newConfiguration(ConfigurationKind.JDK_ONLY, TestJdkKind.FULL_JDK)
            configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, object: MessageCollector {
                private var hasErrors: Boolean = false
                override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
                    if (severity.isError) hasErrors = true
                    System.err.println("$location: $message")
                }

                override fun hasErrors(): Boolean {
                    return hasErrors
                }

            })
            configuration.put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
            configuration.addKotlinSourceRoot(scriptPath)
            configuration.add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, scriptDefinition)
//            configuration.put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, true)

            val environment : KotlinCoreEnvironment
                    = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration,
                            EnvironmentConfigFiles.JVM_CONFIG_FILES)

            try {
                return if (runIsolated) KotlinToJVMBytecodeCompiler.compileScript(environment, paths)
                else KotlinToJVMBytecodeCompiler.compileScript(environment, this.javaClass.classLoader)
            }
            catch (e: CompilationException) {
                messageCollector.report(CompilerMessageSeverity.EXCEPTION, OutputMessageUtil.renderException(e),
                        MessageUtil.psiElementToMessageLocation(e.element))
                return null
            }
            catch (t: Throwable) {
                MessageCollectorUtil.reportException(messageCollector, t)
                throw t
            }

        }
        finally {
            Disposer.dispose(rootDisposable)
        }
    }

}

class KobaltDescription(val template: KClass<out Any>) : KotlinScriptDefinition {
    override val name: String
        get() = "Kobalt build file"

    override fun getScriptName(script: KtScript): Name {
        return ScriptNameUtil.fileNameWithExtensionStripped(script, KotlinParserDefinition.STD_SCRIPT_EXT)
    }

    override fun getScriptParameters(scriptDescriptor: ScriptDescriptor): List<ScriptParameter> =
            template.primaryConstructor!!.parameters.map { ScriptParameter(Name.identifier(it.name!!),
                    getKotlinTypeByKType(scriptDescriptor, it.type)) }

    override fun <TF> isScript(file: TF): Boolean {
        return getFileName(file).endsWith(KotlinParserDefinition.STD_SCRIPT_EXT)
    }

    override fun <TF> getDependenciesFor(file: TF,
            project: Project, previousDependencies: KotlinScriptExternalDependencies?):
                KotlinScriptExternalDependencies? {
        val java = JavaInfo.create(File(SystemProperties.javaBase))
        val result = object: KotlinScriptExternalDependencies {
            override val javaHome = java.javaHome?.absolutePath
            override val classpath: Iterable<File> get() = listOf(java.runtimeJar!!)
            override val imports: Iterable<String> get() = emptyList()
            override val sources: Iterable<File> get() = emptyList()
            override val scripts: Iterable<File> get() = emptyList()
        }
        return result
    }

    private fun getKotlinTypeByKType(scriptDescriptor: ScriptDescriptor, kType: KType): KotlinType {
        val classifier = kType.classifier
        if (classifier !is KClass<*>) {
            println("ERROR Only classes are supported as parameters in script template: $classifier")
            throw RuntimeException("Only classes are supported as parameters in script template: $classifier")
        }

        val type = getKotlinType(scriptDescriptor, classifier)
        val typeProjections = kType.arguments.map { getTypeProjection(scriptDescriptor, it) }
        val isNullable = kType.isMarkedNullable

        val result =
                org.jetbrains.kotlin.types.KotlinTypeFactory.simpleType(Annotations.EMPTY, type.constructor,
                        typeProjections, isNullable)
        return result
    }

    private fun getTypeProjection(scriptDescriptor: ScriptDescriptor, kTypeProjection: KTypeProjection): TypeProjection {
        val kType = kTypeProjection.type ?: throw RuntimeException("Star projections are not supported")

        val type = getKotlinTypeByKType(scriptDescriptor, kType)

        val variance = when (kTypeProjection.variance) {
            KVariance.IN -> Variance.IN_VARIANCE
            KVariance.OUT -> Variance.OUT_VARIANCE
            KVariance.INVARIANT -> Variance.INVARIANT
            null -> throw RuntimeException("Star projections are not supported")
        }

        return TypeProjectionImpl(variance, type)
    }

}

abstract class ScriptWithIntParam(val num: Int)
