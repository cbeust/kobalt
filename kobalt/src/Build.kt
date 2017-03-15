import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.homeDir
import com.beust.kobalt.plugin.application.application
import com.beust.kobalt.plugin.java.javaCompiler
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.plugin.publish.github
import com.beust.kobalt.project
import com.beust.kobalt.test
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object Versions {
    val okhttp = "3.2.0"
    val okio = "1.6.0"
    val retrofit = "2.1.0"
    val gson = "2.6.2"
    val maven = "3.3.9"
    val mavenResolver = "1.0.3"
    val slf4j = "1.7.3"
    val kotlin = "1.1.0"
    val aether = "1.0.2.v20150114"
}

fun mavenResolver(vararg m: String)
        = m.map { "org.apache.maven.resolver:maven-resolver-$it:${Versions.mavenResolver}" }
    .toTypedArray()

fun aether(vararg m: String)
        = m.map { "org.eclipse.aether:aether-$it:${Versions.aether}" }
    .toTypedArray()

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

    pom = Model().apply {
        name = project.name
        description = "A build system in Kotlin"
        url = "http://beust.com/kobalt"
        licenses = listOf(License().apply {
            name = "Apache 2.0"
            url = "http://www.apache .org/licenses/LICENSE-2.0"
        })
        scm = Scm().apply {
            url = "http://github.com/cbeust/kobalt"
            connection = "https://github.com/cbeust/kobalt.git"
            developerConnection = "git@github.com:cbeust/kobalt.git"
        }
        developers = listOf(Developer().apply {
            name = "Cedric Beust"
            email = "cedric@beust.com"
        })
    }

    dependencies {
        compile(
                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "javax.inject:javax.inject:1",
                "com.google.guava:guava:19.0",
                "org.apache.maven:maven-model:${Versions.maven}",
                "io.reactivex:rxjava:1.1.5",
                "com.squareup.okio:okio:${Versions.okio}",
                "com.google.code.gson:gson:${Versions.gson}",
                "com.squareup.okhttp3:okhttp:${Versions.okhttp}",
                "com.squareup.retrofit2:retrofit:${Versions.retrofit}",
                "com.squareup.retrofit2:converter-gson:${Versions.retrofit}",
                "com.beust:jcommander:1.48",
                "org.eclipse.jgit:org.eclipse.jgit:4.5.0.201609210915-r",
                "org.slf4j:slf4j-simple:${Versions.slf4j}",
                *mavenResolver("api", "spi", "util", "impl", "connector-basic", "transport-http", "transport-file"),
                "org.apache.maven:maven-aether-provider:3.3.9"
        )
        exclude(*aether("impl", "spi", "util", "api"))
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
        compile("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")

        // Used by the main app
        compile("com.github.spullara.mustache.java:compiler:0.9.1",
                "javax.inject:javax.inject:1",
                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "com.beust:jcommander:1.65",
                "org.apache.maven:maven-model:${Versions.maven}",
                "com.google.code.findbugs:jsr305:3.0.1",
                "com.google.code.gson:gson:${Versions.gson}",
                "com.squareup.retrofit2:retrofit:${Versions.retrofit}",
                "com.squareup.retrofit2:converter-gson:${Versions.retrofit}",
                "com.squareup.okhttp3:okhttp-ws:${Versions.okhttp}",
                "biz.aQute.bnd:bndlib:2.4.0",
                *mavenResolver("spi"),

                "com.squareup.okhttp3:logging-interceptor:3.2.0",

                "com.sparkjava:spark-core:2.5",
                "org.codehaus.groovy:groovy:2.4.8"

//                "org.eclipse.jetty:jetty-server:${Versions.jetty}",
//                "org.eclipse.jetty:jetty-servlet:${Versions.jetty}",
//                "org.glassfish.jersey.core:jersey-server:${Versions.jersey}",
//                "org.glassfish.jersey.containers:jersey-container-servlet-core:${Versions.jersey}",
//                "org.glassfish.jersey.containers:jersey-container-jetty-http:${Versions.jersey}",
//                "org.glassfish.jersey.media:jersey-media-moxy:${Versions.jersey}",
//                "org.wasabi:wasabi:0.1.182"
        )

    }

    dependenciesTest {
        compile("org.testng:testng:6.10",
                "org.assertj:assertj-core:3.4.1",
                *mavenResolver("util")
                )
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
            include(from("dist"), to("$dir/bin"), "kobaltw.bat")
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
        autoGitTag = true
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

@Task(name = "copyVersionForWrapper", reverseDependsOn = arrayOf("assemble"), runAfter = arrayOf("clean"))
fun taskCopyVersionForWrapper(project: Project) : TaskResult {
    if (project.name == "kobalt-wrapper") {
        val toString = "modules/wrapper/kobaltBuild/classes"
        File(toString).mkdirs()
        val from = Paths.get("src/main/resources/kobalt.properties")
        val to = Paths.get("$toString/kobalt.properties")
        // Only copy if necessary so we don't break incremental compilation
        if (! to.toFile().exists() || (from.toFile().readLines() != to.toFile().readLines())) {
            Files.copy(from,
                    to,
                    StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return TaskResult()
}
