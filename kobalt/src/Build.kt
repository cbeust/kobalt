import com.beust.kobalt.*
import com.beust.kobalt.internal.test
import com.beust.kobalt.plugin.java.javaProject
import com.beust.kobalt.plugin.kotlin.kotlinProject
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.publish.jcenter

val repos = repos("https://dl.bintray.com/cbeust/maven/")

//val plugins = plugins(
//        "com.beust:kobalt-example-plugin:0.42")
//val plugins2 = plugins(
//        "com.beust.kobalt:kobalt-line-count:0.2"
////        file(homeDir("kotlin/kobalt-line-count/kobaltBuild/libs/kobalt-line-count-0.1.jar"))
////        file(homeDir("kotlin/kobalt-example-plugin/kobaltBuild/libs/kobalt-example-plugin-0.17.jar"))
//)


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

    dependenciesTest {
//        compile("junit:junit:4.12")
        compile("org.testng:testng:6.9.5")
    }

//    sourceDirectories {
//        path("src/main/kotlin")
//        path("src/main/resources")
//        path("src/main/java")
//    }

    dependencies {
        compile("org.jetbrains.kotlin:kotlin-stdlib:0.14.449",
                "org.jetbrains.kotlin:kotlin-compiler-embeddable:0.14.449",
//                "org.jetbrains.kotlin:kotlin-compiler:0.14.449",

//                file(homeDir("java/jcommander/target/jcommander-1.47.jar")),
                "com.beust:jcommander:1.48",
                "com.beust:klaxon:0.16",
                "com.squareup.okhttp:okhttp:2.4.0",
                "org.slf4j:slf4j-api:1.7.12",
                "org.slf4j:slf4j-simple:1.7.12",
                "ch.qos.logback:logback-classic:1.1.2",
                "org.jsoup:jsoup:1.8.2",
                "com.google.inject:guice:4.0",
                "com.google.inject.extensions:guice-assistedinject:4.0",
                "com.google.guava:guava:18.0",
                "org.apache.maven:maven-model:3.3.3",
                "com.github.spullara.mustache.java:compiler:0.8.18"
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
            attributes("Main-Class", "com.beust.kobalt.KobaltPackage")
        }
    }
//    jar {
//        fatJar = true
//        name = "${kobalt.name}-wrapper.jar"
//        manifest {
//            attributes("Main-Class", "com.beust.kobalt.wrapper.WrapperPackage")
//        }
//    }
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

//val testng = javaProject {
//    name = "testng"
//    group = "org.testng"
//    artifactId = name
//    version = "6.9.6-SNAPSHOT"
//    directory = homeDir("java/testng")
//    buildDirectory = "kobaltBuild"
//
//    sourceDirectoriesTest {
//        path("src/test/java")
//        path("src/test/resources")
//    }
//    sourceDirectories {
//        path("src/main/java")
//        path("src/generated/java")
//    }
//    dependencies {
//        compile("org.apache.ant:ant:1.7.0",
//                "junit:junit:4.10",
//                "org.beanshell:bsh:2.0b4",
//                "com.google.inject:guice:4.0:no_aop",
//                "com.beust:jcommander:1.48",
//                "org.yaml:snakeyaml:1.15")
//    }
//}
//
//@Task(name = "generateVersionFile", description = "Generate the Version.java file", runBefore = arrayOf("compile"))
//fun createVersionFile(project: Project) : com.beust.kobalt.internal.TaskResult {
//    val dirFrom = testng.directory + "/src/main/resources/org/testng/internal/"
//    val dirTo = testng.directory + "/src/generated/java/org/testng/internal/"
//    println("COPYING VERSION FILE")
//    Files.copy(Paths.get(dirFrom + "VersionTemplateJava"), Paths.get(dirTo + "Version.java"),
//            StandardCopyOption.REPLACE_EXISTING)
//    return com.beust.kobalt.internal.TaskResult()
//}
//
