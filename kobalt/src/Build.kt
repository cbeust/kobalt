
import com.beust.kobalt.*
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.plugin.application.application
import com.beust.kobalt.plugin.java.javaCompiler
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.autoGitTag
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.plugin.publish.github
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

val bs = buildScript {
    repos("http://dl.bintray.com/cbeust/maven")
}

object Versions {
    val kotlin = "1.2.71"
    val okhttp = "3.9.1"
    val okio = "1.13.0"
    val retrofit = "2.3.0"
    val gson = "2.8.2"
    val guice = "4.2.2"
    val maven = "3.5.2"
    val mavenResolver = "1.1.0"
    val slf4j = "1.7.3"
    val aether = "1.0.2.v20150114"
    val testng = "6.12"
    val jcommander = "1.72"

    // JUnit 5
    val junit = "4.12"
    val junitPlatform = "1.1.0"
    val junitJupiter = "5.1.0"
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
        jar { }
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

    bintray {
        publish = true
        sign = true
    }

    pom = createPom(name, "Wrapper for Kobalt")
}

val kobaltPluginApi = project {
    name = "kobalt-plugin-api"
    group = "com.beust"
    artifactId = name
    version = readVersion()
    directory = "modules/kobalt-plugin-api"
    description = "A build system in Kotlin"
    url = "http://beust.com/kobalt"

    pom = createPom(name, "A build system in Kotlin")

    dependencies {
        compile(
                "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}",
                "com.google.inject:guice:${Versions.guice}",
                "com.google.inject.extensions:guice-assistedinject:4.1.0",
                "javax.inject:javax.inject:1",
                "com.google.guava:guava:27.0.1-jre",
                "org.apache.maven:maven-model:${Versions.maven}",
                "io.reactivex:rxjava:1.3.3",
                "com.squareup.okio:okio:${Versions.okio}",
                "com.google.code.gson:gson:${Versions.gson}",
                "com.squareup.okhttp3:okhttp:${Versions.okhttp}",
                "com.squareup.retrofit2:retrofit:${Versions.retrofit}",
                "com.squareup.retrofit2:converter-gson:${Versions.retrofit}",
                "com.beust:jcommander:${Versions.jcommander}",
                "org.eclipse.jgit:org.eclipse.jgit:4.9.0.201710071750-r",
                "org.slf4j:slf4j-simple:${Versions.slf4j}",
                *mavenResolver("api", "spi", "util", "impl", "connector-basic", "transport-http", "transport-file"),
                "org.apache.maven:maven-aether-provider:3.3.9",
                "org.testng.testng-remote:testng-remote:1.3.2",
                "org.testng:testng:${Versions.testng}",
                "org.junit.platform:junit-platform-surefire-provider:${Versions.junitPlatform}",
                "org.junit.platform:junit-platform-runner:${Versions.junitPlatform}",
                "org.junit.platform:junit-platform-engine:${Versions.junitPlatform}",
                "org.junit.platform:junit-platform-console:${Versions.junitPlatform}",
                "org.junit.jupiter:junit-jupiter-engine:${Versions.junitJupiter}",
                "org.junit.vintage:junit-vintage-engine:${Versions.junitJupiter}",
                "org.apache.commons:commons-compress:1.15",
                "commons-io:commons-io:2.6",

                // Java 9
                "javax.xml.bind:jaxb-api:2.3.0"
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

    kotlinCompiler {
        args("nowarn")
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
        compile(
                "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}",
                "com.github.spullara.mustache.java:compiler:0.9.5",
                "javax.inject:javax.inject:1",
                "com.google.inject:guice:${Versions.guice}",
                "com.google.inject.extensions:guice-assistedinject:${Versions.guice}",
                "com.beust:jcommander:${Versions.jcommander}",
                "org.apache.maven:maven-model:${Versions.maven}",
                "com.google.code.findbugs:jsr305:3.0.2",
                "com.google.code.gson:gson:${Versions.gson}",
                "com.squareup.retrofit2:retrofit:${Versions.retrofit}",
                "com.squareup.retrofit2:converter-gson:${Versions.retrofit}",
//                "com.squareup.okhttp3:okhttp-ws:3.4.2",
                "biz.aQute.bnd:biz.aQute.bndlib:3.5.0",
                *mavenResolver("spi"),

                "com.squareup.okhttp3:logging-interceptor:3.9.0",

                "com.sparkjava:spark-core:2.6.0",
                "org.codehaus.groovy:groovy:2.4.12",

                // Java 9
                "javax.xml.bind:jaxb-api:2.3.0",
                "com.sun.xml.bind:jaxb-impl:2.3.0",
                "com.sun.xml.bind:jaxb-core:2.3.0",
                "com.sun.activation:javax.activation:1.2.0"

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
        compile("org.jetbrains.kotlin:kotlin-test:${Versions.kotlin}",
                "org.testng:testng:${Versions.testng}",
                "org.assertj:assertj-core:3.8.0",
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
            val files = listOf(
                    "dist", "$dir/bin", "kobaltw",
                    "dist", "$dir/bin", "kobaltw.bat",
                    "$buildDirectory/libs", "$dir/kobalt/wrapper", "$projectName-$version.jar",
                    "modules/wrapper/$buildDirectory/libs", "$dir/kobalt/wrapper", "$projectName-wrapper.jar")

            (0 .. files.size - 1 step 3).forEach { i ->
                include(from(files[i]), to(files[i + 1]), files[i + 2])
            }

            // Package the sources
            val currentDir = Paths.get(".").toAbsolutePath().normalize().toString()
            zipFolders("$currentDir/$buildDirectory/libs/all-sources/$projectName-$version-sources.jar",
                    "$currentDir/$directory/src/main/kotlin",
                    "$currentDir/${kobaltPluginApi.directory}/src/main/kotlin")
            include(from("$buildDirectory/libs/all-sources"), to("$dir/kobalt/wrapper"), "$projectName-$version-sources.jar")
        }
    }

    kotlinCompiler {
        args("nowarn")
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

    autoGitTag {
        enabled = true
    }
}

fun zipFolders(zipFilePath: String, vararg foldersPath: String) {
    val zip = Paths.get(zipFilePath)
    Files.deleteIfExists(zip)
    Files.createDirectories(zip.parent)
    val zipPath = Files.createFile(zip)
    ZipOutputStream(Files.newOutputStream(zipPath)).use {
        foldersPath.map {Paths.get(it)}.forEach { folderPath ->
            Files.walk(folderPath)
                    .filter { path -> !Files.isDirectory(path) }
                    .forEach { path ->
                        val zipEntry = ZipEntry(folderPath.relativize(path).toString())
                        try {
                            it.putNextEntry(zipEntry)
                            Files.copy(path, it)
                            it.closeEntry()
                        } catch (e: Exception) {
                        }
                    }
        }
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

fun createPom(projectName: String, projectDescription: String) = Model().apply {
    name = projectName
    description = projectDescription
    url = "http://beust.com/kobalt"
    licenses = listOf(License().apply {
        name = "Apache-2.0"
        url = "http://www.apache.org/licenses/LICENSE-2.0"
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
