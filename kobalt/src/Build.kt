
import com.beust.kobalt.*
import com.beust.kobalt.api.License
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.Scm
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.plugin.application.application
import com.beust.kobalt.plugin.java.javaCompiler
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.plugin.publish.github
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

val r = repos("http://dl.bintray.com/kotlin/kotlinx.dom")

object Versions {
    val okhttp = "3.2.0"
    val okio = "1.6.0"
    val retrofit = "2.0.0"
    val gson = "2.6.2"
    val aether = "1.1.0"
    val sonatypeAether = "1.13.1"
    val maven = "3.3.9"
}

val wrapper = project {
    name = "kobalt-wrapper"
    group = "com.beust"
    artifactId = name
    version = readVersion()
    directory = "modules/wrapper"

    javaCompiler {
        args("-source", "1.7", "-target", "1.7")
    }

    assemble {
        jar {
            name = projectName + ".jar"
            manifest {
                attributes("Main-Class", "com.beust.kobalt.wrapper.Main")
            }
        }
    }

    application {
        mainClass = "com.beust.kobalt.wrapper.Main"
    }
}

val kobaltPluginApi = project {
    name = "kobalt-plugin-api"
    group = "com.beust"
    artifactId = name
    version = readVersion()
    directory = "modules/kobalt-plugin-api"
    description = "A build system in Kotlin"
    url = "http://beust.com/kobalt"
    licenses = arrayListOf(License("Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0"))
    scm = Scm(url = "http://github.com/cbeust/kobalt",
            connection = "https://github.com/cbeust/kobalt.git",
            developerConnection = "git@github.com:cbeust/kobalt.git")

    dependencies {
        compile("org.jetbrains.kotlinx:kotlinx.dom:0.0.10",

                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "javax.inject:javax.inject:1",
                "com.google.guava:guava:19.0-rc2",
                "org.apache.maven:maven-model:${Versions.maven}",
                "io.reactivex:rxjava:1.0.16",
                "com.google.code.gson:gson:${Versions.gson}",
                "com.squareup.okio:okio:${Versions.okio}",
                "com.squareup.retrofit2:retrofit:${Versions.retrofit}",
                "com.squareup.retrofit2:converter-gson:${Versions.retrofit}",
                "com.beust:jcommander:1.48",

                "org.slf4j:slf4j-nop:1.6.0",
                "org.eclipse.aether:aether-spi:${Versions.aether}",
                "org.eclipse.aether:aether-impl:${Versions.aether}",
                "org.eclipse.aether:aether-connector-basic:${Versions.aether}",
                "org.eclipse.aether:aether-transport-file:${Versions.aether}",
                "org.eclipse.aether:aether-transport-http:${Versions.aether}",
                "org.sonatype.aether:aether-api:${Versions.sonatypeAether}",
                "org.sonatype.aether:aether-connector-wagon:1.13.1",
                "org.apache.maven:maven-aether-provider:${Versions.maven}"
        )
    }


    assemble {
        mavenJars {
            fatJar = true
            manifest {
                attributes("Main-Class", "com.beust.kobalt.MainKt")
            }
        }
    }

//    install {
//        libDir = "lib-test"
//    }

    kotlinCompiler {
        args("-nowarn")
    }

    bintray {
        publish = true
    }
}

val kobaltApp = project(kobaltPluginApi, wrapper) {
    name = "kobalt"
    group = "com.beust"
    artifactId = name
    version = readVersion()

    dependencies {
        // Used by the plugins
        compile("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0.0")

        // Used by the main app
        compile("com.github.spullara.mustache.java:compiler:0.9.1",
                "javax.inject:javax.inject:1",
                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "com.beust:jcommander:1.48",
                "org.apache.maven:maven-model:${Versions.maven}",
                "com.google.code.findbugs:jsr305:3.0.1",
                "com.google.code.gson:gson:${Versions.gson}",
                "com.squareup.okhttp3:okhttp:${Versions.okhttp}",
                "org.codehaus.plexus:plexus-utils:3.0.22")

    }

    dependenciesTest {
        compile("org.testng:testng:6.9.10")
    }

    assemble {
        mavenJars {
            fatJar = true
            manifest {
                attributes("Main-Class", "com.beust.kobalt.MainKt")
            }
        }
        zip {
            val dir = "kobalt-$version"
            include(from("dist"), to("$dir/bin"), "kobaltw")
            include(from("$buildDirectory/libs"), to("$dir/kobalt/wrapper"),
                    "$projectName-$version.jar")
            include(from("modules/wrapper/$buildDirectory/libs"), to("$dir/kobalt/wrapper"),
                    "$projectName-wrapper.jar")
        }
    }

    kotlinCompiler {
        args("-nowarn")
    }

    bintray {
        publish = true
    }

    github {
        file("$buildDirectory/libs/$name-$version.zip", "$name/$version/$name-$version.zip")
    }

    test {
        args("-log", "2", "src/test/resources/testng.xml")
    }
}

fun readVersion() : String {
    val localFile =
            listOf("src/main/resources/kobalt.properties",
                homeDir("kotlin", "kobalt", "src/main/resources/kobalt.properties")).first { File(it).exists() }
    with(java.util.Properties()) {
        load(java.io.FileReader(localFile))
        return getProperty("kobalt.version")
    }
}

@Task(name = "copyVersionForWrapper", runBefore = arrayOf("assemble"), runAfter = arrayOf("compile"), description = "")
fun taskCopyVersionForWrapper(project: Project) : TaskResult {
    if (project.name == "kobalt-wrapper") {
        val toString = "modules/wrapper/kobaltBuild/classes"
        File(toString).mkdirs()
        val from = Paths.get("src/main/resources/kobalt.properties")
        val to = Paths.get("$toString/kobalt.properties")
        Files.copy(from,
                to,
                StandardCopyOption.REPLACE_EXISTING)
    }
    return TaskResult()
}
