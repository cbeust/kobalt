import com.beust.kobalt.*
import com.beust.kobalt.internal.test
import com.beust.kobalt.plugin.java.javaProject
import com.beust.kobalt.plugin.kotlin.kotlinProject
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.publish.jcenter
//import com.beust.kobalt.plugin.linecount.lineCount
//val plugins = plugins(
//        "com.beust.kobalt:kobalt-line-count:0.15"
////        file(homeDir("kotlin/kobalt-line-count/kobaltBuild/libs/kobalt-line-count-0.14.jar"))
//)
//
//val lc = lineCount {
//    suffix = "**.md"
//}

fun readVersion() : String {
    val p = java.util.Properties()
    p.load(java.io.FileReader(java.io.File("src/main/resources/kobalt.properties")))
    return p.getProperty("kobalt.version")
}

val wrapper = javaProject {
    name = "kobalt-wrapper"
    version = readVersion()
    directory = homeDir("kotlin/kobalt/modules/wrapper")
}

val assembleWrapper = assemble(wrapper) {
    jar {
        name = wrapper.name + ".jar"
        manifest {
            attributes("Main-Class", "com.beust.kobalt.wrapper.Main")
        }
    }
}
val kobalt = kotlinProject(wrapper) {
    name = "kobalt"
    group = "com.beust"
    artifactId = name
    version = readVersion()
    description = "A build system in Kotlin"
    url = "http://beust.com/kobalt"
    licenses = listOf(com.beust.kobalt.api.License("Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0"))
    scm = com.beust.kobalt.api.Scm(
            url = "http://github.com/cbeust/kobalt",
            connection = "https://github.com/cbeust/kobalt.git",
            developerConnection = "git@github.com:cbeust/kobalt.git")

    dependenciesTest {
        compile("org.testng:testng:6.9.9")
    }

    dependencies {
        compile("org.jetbrains.kotlin:kotlin-stdlib:1.0.0-beta-1038",
                "org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0.0-beta-1038",

//                file(homeDir("java/jcommander/target/jcommander-1.47.jar")),
                "com.beust:jcommander:1.48",
                "com.squareup.okhttp:okhttp:2.5.0",
                "org.jsoup:jsoup:1.8.3",
                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "javax.inject:javax.inject:1",
                "com.google.guava:guava:19.0-rc2",
                "org.apache.maven:maven-model:3.3.3",
                "com.github.spullara.mustache.java:compiler:0.9.1",
                "io.reactivex:rxjava:1.0.14",
                "com.google.code.gson:gson:2.4"
              )
    }
}

val testKobalt = test(kobalt) {
    args("-log", "2", "src/test/resources/testng.xml")
}

val assembleKobalt = assemble(kobalt) {
    mavenJars {
        fatJar = true
        manifest {
            attributes("Main-Class", "com.beust.kobalt.MainKt")
        }
    }
    zip {
        include("kobaltw")
        include(from("${kobalt.buildDirectory}/libs"), to("kobalt/wrapper"),
                "${kobalt.name}-${kobalt.version}.jar")
        include(from("modules/wrapper/${kobalt.buildDirectory}/libs"), to("kobalt/wrapper"),
                "${kobalt.name}-wrapper.jar")
    }
}

val cs = kotlinCompiler {
    args("-nowarn")
}

val jc = jcenter(kobalt) {
    publish = true
    file("${kobalt.buildDirectory}/libs/${kobalt.name}-${kobalt.version}.zip",
            "${kobalt.name}/${kobalt.version}/${kobalt.name}-${kobalt.version}.zip")
}
