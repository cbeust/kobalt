
import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.plugin.application.application
import com.beust.kobalt.plugin.dokka.*
import com.beust.kobalt.plugin.java.javaCompiler
import com.beust.kobalt.plugin.java.javaProject
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.kotlin.kotlinProject
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.github
import com.beust.kobalt.plugin.publish.jcenter
import com.beust.kobalt.test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

val r = repos("http://dl.bintray.com/kotlin/kotlinx.dom")

val wrapper = javaProject {
    name = "kobalt-wrapper"
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

    productFlavor("dev") {
    }

    buildType("debug") {
    }

    application {
        mainClass = "com.beust.kobalt.wrapper.Main"
    }
}

val kobalt = kotlinProject(wrapper) {
    name = "kobalt"
    group = "com.beust"
    artifactId = name
    version = readVersion()
    description = "A build system in Kotlin"
    url = "http://beust.com/kobalt"
    licenses = arrayListOf(License("Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0"))
    scm = Scm(url = "http://github.com/cbeust/kobalt",
            connection = "https://github.com/cbeust/kobalt.git",
            developerConnection = "git@github.com:cbeust/kobalt.git")

    dependenciesTest {
        compile("org.testng:testng:6.9.9")
    }

    dependencies {
        compile("org.jetbrains.kotlin:kotlin-stdlib:1.0.0-beta-2423",
                "org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0.0-beta-2423",
                "org.jetbrains.dokka:dokka-fatjar:0.9.2",
                "org.jetbrains.kotlinx:kotlinx.dom:0.0.4",

                "com.beust:jcommander:1.48",
                "com.squareup.okhttp:okhttp:2.5.0",
                "org.jsoup:jsoup:1.8.3",
                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "javax.inject:javax.inject:1",
                "com.google.guava:guava:19.0-rc2",
                "org.apache.maven:maven-model:3.3.3",
                "com.github.spullara.mustache.java:compiler:0.9.1",
                "io.reactivex:rxjava:1.0.16",
                "com.google.code.gson:gson:2.4",
                "com.squareup.retrofit:retrofit:1.9.0",
                "com.squareup.okio:okio:1.6.0"
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
            include("kobaltw")
            include(from("$buildDirectory/libs"), to("kobalt/wrapper"),
                    "$projectName-$version.jar")
            include(from("modules/wrapper/$buildDirectory/libs"), to("kobalt/wrapper"),
                    "$projectName-wrapper.jar")
        }
    }

//    install {
//        libDir = "lib-test"
//    }

    test {
        args("-log", "1", "src/test/resources/testng.xml")
    }

    kotlinCompiler {
        args("-nowarn")
    }

    dokka {
        outputFormat = "markdown"
    }

    dokka {
        outputFormat = "html"
    }

    github {
        file("$buildDirectory/libs/$name-$version.zip", "$name/$version/$name-$version.zip")
    }

    jcenter {
        publish = true
    }
}

fun readVersion() : String {
    val p = java.util.Properties()
    var localFile = java.io.File("src/main/resources/kobalt.properties")
    if (! localFile.exists()) {
        localFile = File(homeDir("kotlin", "kobalt", "src/main/resources/kobalt.properties"))
    }
    p.load(java.io.FileReader(localFile))
    return p.getProperty("kobalt.version")
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
