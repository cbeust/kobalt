
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.project

val javaFirst = project {
    name = "javaFirst"
    group = "com.example"
    artifactId = name
    version = "0.1"
    directory = name

    assemble {
        jar {
            fatJar = true
            manifest {
                attributes("Main-Class", "example.KotlinMainKt")
            }
        }
    }
}

val kotlinFirst = project {
    name = "kotlinFirst"
    group = "com.example"
    artifactId = name
    version = "0.1"
    directory = name

    assemble {
        jar {
            fatJar = true
            manifest {
                attributes("Main-Class", "example.KotlinMainKt")
            }
        }
    }
}

val mixed1 = project {
    name = "mixed1"
    group = "com.guatec"
    artifactId = name
    version = "0.1"
    directory = name

    dependenciesTest {
        compile("org.testng:testng:6.9.5")
    }

    assemble {
        jar {
        }
    }
}
