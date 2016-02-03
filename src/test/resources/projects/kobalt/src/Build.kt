import com.beust.kobalt.*
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.java.*
import com.beust.kobalt.plugin.kotlin.*

val javaFirst = kotlinProject {
    name = "javaFirst"
    group = "com.example"
    artifactId = name
    version = "0.1"
    directory = name

    sourceDirectories {
        path("src/main/java", "src/main/kotlin")
    }

    assemble {
        jar {
            fatJar = true
            manifest {
                attributes("Main-Class", "example.KotlinMainKt")
            }
        }
    }
}

val kotlinFirst = kotlinProject {
    name = "kotlinFirst"
    group = "com.example"
    artifactId = name
    version = "0.1"
    directory = name

    sourceDirectories {
        path("src/main/java", "src/main/kotlin")
    }

    assemble {
        jar {
            fatJar = true
            manifest {
                attributes("Main-Class", "example.KotlinMainKt")
            }
        }
    }
}

val javaFirst2 = javaProject {
    name = "javaFirst2"
    group = "com.guatec"
    artifactId = name
    version = "0.1"
    directory = name
    buildDirectory = "javaBuild"

    sourceDirectories {
        path("src/main/java", "src/main/kotlin")
    }

    sourceDirectoriesTest {
        path("src/test/java", "src/test/kotlin")
    }

    dependenciesTest {
        compile("org.testng:testng:6.9.5")
    }

    assemble {
        jar {
        }
    }
}
