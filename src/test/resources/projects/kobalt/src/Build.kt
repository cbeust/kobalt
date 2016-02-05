
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
        }
    }
}

val mixed1 = project {
    name = "mixed1"
    group = "com.guatec"
    artifactId = name
    version = "0.1"
    directory = name

    assemble {
        jar {
        }
    }
}


val nonStandard = project {
    name = "nonStandard"
    group = "com.example"
    directory = name

    sourceDirectories {
        path("src/generated/java")
    }

    assemble {
        jar {
        }
    }
}
