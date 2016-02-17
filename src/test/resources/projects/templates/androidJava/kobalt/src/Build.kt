import com.beust.kobalt.*
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.android.*
import com.beust.kobalt.plugin.java.*

val pl = plugins("com.beust:kobalt-android:0.36")

val p = project {

    name = "kobalt-demo"
    group = "com.example"
    artifactId = name
    version = "0.1"

    android {
        compileSdkVersion = "17"
        buildToolsVersion = "23.0.1"
        applicationId = "com.sample"
    }
}
