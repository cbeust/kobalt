import com.beust.kobalt.*
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.kotlin.*

val repos = repos()


val p = kotlinProject {

    name = "mixed"
    group = "com.example"
    artifactId = name
    version = "0.1"

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
