import com.beust.kobalt.*
import com.beust.kobalt.plugin.android.*
import com.beust.kobalt.plugin.java.*

val r = repos(file("d:\\android\\adt-bundle-windows-x86_64-20140321\\sdk\\extras\\android\\m2repository"))

val p = javaProject {
    name = "intro_android_demo"
    group = "com.example"
    artifactId = name
    version = "0.1"
    directory = homeDir("android/intro_android_demo")

    sourceDirectories {
        listOf(path("app/src/main/java"))
    }

    dependencies {
        compile(file("app/libs/android-async-http-1.4.3.jar"),
            "com.android.support:support-v4:aar:21.0.3")
    }

    android {
        compileSdkVersion = "21"
        applicationId = "codepath.apps.demointroandroid"
        buildToolsVersion = "21.1.2"
    }
}
