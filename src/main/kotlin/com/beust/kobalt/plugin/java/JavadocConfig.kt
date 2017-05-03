package com.beust.kobalt.plugin.java

import com.beust.kobalt.api.Project
import com.google.inject.Singleton
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Singleton
class JavadocConfig(val project: Project) {
    val args = arrayListOf("-Xdoclint:none", "-Xmaxerrs", "1", "-quiet")

    private fun removeArg(match: String, startsWith: Boolean = false, pair: Boolean = false) {
        val it = args.iterator()
        while (it.hasNext()) {
            val next = it.next()
            var removed = false
            if (startsWith) {
                if (next.startsWith(match)) {
                    it.remove()
                    removed = true
                }
            } else if (next == match) {
                it.remove()
                removed = true
            }
            // If it's a pair, delete the next arg too.
            if (pair && removed && it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    private fun addInt(option: String, value: Int): Int {
        args.add("-$option")
        args.add(value.toString())
        return value
    }

    private fun addBoolean(option: String, value: Boolean): Boolean {
        args.remove("-$option")
        if (value) {
            args.add("-$option")
        }
        return value
    }

    private fun addString(option: String, value: String): String {
        if (value.isNotEmpty()) {
            args.add("-$option")
            args.add("\"$value\"")
        }
        return value
    }

    private fun addStrings(option: String, vararg value: String) {
        value.forEach {
            addString(option, it)
        }
    }

    private fun addOptions(option: String, first: String, second: String) {
        if (first.isNotEmpty() && second.isNotEmpty()) {
            args.add("-$option")
            args.add("\"$first\"")
            args.add("\"$second\"")
        }
    }

    private fun addFile(option: String, value: String): String {
        val f = File(value)
        if (f.exists()) {
            args.add("-$option")
            args.add("\"${f.absolutePath}\"")
        }
        return value
    }

    /**
     * Set arguments manually.
     */
    fun args(vararg options: String) = args.addAll(options)

    //
    // Jvm Options
    //

    /**
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/unix/javac.html">-Xdoclint</a>
     */
    var docLint: String = "none"
        set(value) {
            removeArg("-Xdoclint:", startsWith = true)
            addString("Xdoclint:", value)
        }

    /**
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/unix/javac.html">-Xmaxerrs</a>
     */
    var maxErrs: Int = 1
        set(value) {
            removeArg("-Xmaxerrs", startsWith = true, pair = true)
            addInt("Xmaxerrs", value)
        }

    /**
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/unix/javac.html">-Xmaxwarns</a>
     */
    var maxWarns: Int = 1
        set(value) {
            removeArg("-Xmaxwarns", startsWith = true, pair = true)
            addInt("Xmaxwarns", value)
        }

    //
    // Javadoc Options
    //

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#BEJICGGH">-overview</a>
     */
    var overview: String = ""
        set(value) {
            addFile("overview", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCHFEB">-public</a>
     */
    var public: Boolean = false
        set(value) {
            addBoolean("public", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCIFFD">-protected</a>
     */
    var protected: Boolean = false
        set(value) {
            addBoolean("protected", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDEDJJJ">-pakage</a>
     */
    var pkg: Boolean = false
        set(value) {
            addBoolean("package", pkg)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDJDFJG">-private</a>
     */
    var private: Boolean = false
        set(value) {
            addBoolean("private", private)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCGDCA">-doclet</a>
     */
    var doclet: String = ""
        set(value) {
            addString("doclet", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDBGIED">-docletpath</a>
     */
    var docletPath: String = ""
        set(value) {
            addString("docletpath", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDBGDFI">-source</a>
     */
    var source: String = ""
        set(value) {
            addString("source", source)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDEHCDG">-sourcepath</a>
     */
    var sourcePath: String = ""
        set(value) {
            addString("sourcepath", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGAHAJ">-classpath</a>
     */
    var classPath: String = ""
        set(value) {
            addString("classpath", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDJEDJI">-subpackages</a>
     */
    var subPackages: String = ""
        set(value) {
            addString("subpackages", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFBDCF">-exclude</a>
     */
    var exclude: String = ""
        set(value) {
            addString("exclude", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHDACA">-bootClassPath</a>
     */
    var bootClassPath: String = ""
        set(value) {
            addString("bootclasspath", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCJGIC">-extdirs</a>
     */
    var extDirs: String = ""
        set(value) {
            addString("extdirs", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGHFJJ">-verbose</a>
     */
    var verbose: Boolean = false
        set(value) {
            addBoolean("verbose", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGFHAA">-quiet</a>
     */
    var quiet: Boolean = true
        set(value) {
            addBoolean("quiet", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHHIDI">-breakiterator</a>
     */
    var breakIterator: Boolean = false
        set(value) {
            addBoolean("breakiterator", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDEBGCE">-locale</a>
     */
    var locale: String = ""
        set(value) {
            addString("locale", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDEIGDC">-encoding</a>
     */
    var encoding: String = ""
        set(value) {
            addString("encoding", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGDEEE">-Jflag</a>
     */
    var jFlag: String = ""
        set(value) {
            addString("J-", value)
        }

    //
    // Standard Doclet
    //

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGCJEG">-use</a>
     */
    var use: Boolean = false
        set(value) {
            addBoolean("use", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGCEFG">-version</a>
     */
    var version: Boolean = false
        set(value) {
            addBoolean("version", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCBHDB">-author</a>
     */
    var author: Boolean = false
        set(value) {
            addBoolean("author", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFGBHB">-splitindex</a>
     */
    var splitIndex: Boolean = false
        set(value) {
            addBoolean("splitindex", value)
        }

    /**
     * Set both the [windowTitle] and [docTitle]
     */
    var title: String = ""
        set(value) {
            addString("windowtitle", value)
            addString("doctitle", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDBIEEI">-windowtitle</a>
     */
    var windowTitle: String = ""
        set(value) {
            addString("windowtitle", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDJGBIE">-doctitle</a>
     */
    var docTitle: String = ""
        set(value) {
            addString("doctitle", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDDAEGD">-header</a>
     */
    var header: String = ""
        set(value) {
            addString("header", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFACCA">-footer</a>
     */
    var footer: String = ""
        set(value) {
            addString("footer", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHHGBF">-top</a>
     */
    var top: String = ""
        set(value) {
            addString("top", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDECAJE">-bottom</a>
     */
    var bottom: String = ""
        set(value) {
            addString("bottom", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFBBID">-linksource</a>
     */
    var linkSource: Boolean = false
        set(value) {
            addBoolean("linksource", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCDFGD">-nodeprecated</a>
     */
    var noDeprecated: Boolean = false
        set(value) {
            addBoolean("nodeprecated", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGFHJJ">-nodeprecatedlist</a>
     */
    var noDeprecatedList: Boolean = false
        set(value) {
            addBoolean("nodeprecatedlist", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFICFB">-nosince</a>
     */
    var noSince: Boolean = false
        set(value) {
            addBoolean("nosince", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDBGJBI">-notree</a>
     */
    var noTree: Boolean = false
        set(value) {
            addBoolean("notree", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHHHEI">-noindex</a>
     */
    var noIndex: Boolean = false
        set(value) {
            addBoolean("noindex", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHHDBI">-nohelp</a>
     */
    var noHelp: Boolean = false
        set(value) {
            addBoolean("nohelp", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDEDCCG">-nonavbar</a>
     */
    var noNavBar: Boolean = false
        set(value) {
            addBoolean("nonavbar", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDJICID">-helpfile</a>
     */
    var helpFile: String = ""
        set(value) {
            addFile("helpfile", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#BEJFCAIH">-stylesheet</a>
     */
    var stylesheet: String = ""
        set(value) {
            addFile("stylesheet", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFJAFC">-serialwarn</a>
     */
    var serialWarn: Boolean = false
        set(value) {
            addBoolean("serialwarn", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHDEAD">-charset</a>
     */
    var charSet: String = ""
        set(value) {
            addString("charset", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGIHCH">-docencoding</a>
     */
    var docEncoding: String = ""
        set(value) {
            addString("docencoding", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDBHIGE">-keywords</a>
     */
    var keywords: Boolean = false
        set(value) {
            addBoolean("keywords", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDJFHDG">-tagletpath</a>
     */
    var tagletPath: String = ""
        set(value) {
            addString("tagletpath", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDCBDHI">-docfilessubdirs</a>
     */
    var docFilesSubDirs: Boolean = false
        set(value) {
            addBoolean("docfilessubdirs", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGHIAE">-excludedocfilessubdir</a>
     */
    var excludeDocFilesSubDir: String = ""
        set(value) {
            addString("excludedocfilessubdir", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFJFBE">-noqualifiers</a>
     */
    var noQualifiers: String = ""
        set(value) {
            addString("noqualifier", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDGBABE">-notimestamp</a>
     */
    var noTimestamp: Boolean = false
        set(value) {
            addBoolean("notimestamp", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFCGJD">-nocomment</a>
     */
    var noComment: Boolean = false
        set(value) {
            addBoolean("nocomment", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDIAGAG">-sourcetab</a>
     */
    var sourceTab: String = ""
        set(value) {
            addString("sourcetab", value)
        }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDIGGII">-group</a>
     */
    fun group(groupHeading: String, packagePattern: String) = addOptions("group", groupHeading, packagePattern)

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDFIIJH">-linkoffline</a>
     */
    fun linkOffline(extdocURL: String, packagelistLoc: String) = addOptions("linkoffline", extdocURL, packagelistLoc)

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDEDJFI">-link</a>
     */
    fun links(vararg links: String) = addStrings("link", *links)

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#tag">-tag</a>
     */
    fun tags(vararg tags: String) = addStrings("tag", *tags)

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html#CHDHEFHH">-taglets</a>
     */
    fun taglets(vararg taglets: String) = addStrings("taglet", *taglets)
}

fun main(args: Array<String>) {
    fun fromStream(ins: InputStream): List<String> {
        val result = arrayListOf<String>()
        val br = BufferedReader(InputStreamReader(ins))
        var line = br.readLine()

        while (line != null) {
            result.add(line)
            println(line)
            line = br.readLine()
        }

        return result
    }

    val config = JavadocConfig(Project())
    config.verbose = true
    config.quiet = false
    config.links("http://docs.oracle.com/javase/8/docs/api/")
    config.args.add(0, ".\\kobaltBuild\\docs\\javadoc")
    config.args.add(0, "-d")
    config.args.add(0, "javadoc")
    config.args.add(".\\modules\\wrapper\\src\\main\\java\\com\\beust\\kobalt\\wrapper\\Config.java")
    config.args.add(".\\modules\\wrapper\\src\\main\\java\\com\\beust\\kobalt\\wrapper\\Main.java")

    println(config.args.joinToString(" "))

    val pb = ProcessBuilder().command(config.args.toList())
    pb.directory(File("."))
    val proc = pb.start()
    val err = proc.waitFor(30, TimeUnit.SECONDS)
    val stdout = if (proc.inputStream.available() > 0) fromStream(proc.inputStream) else emptyList()
    val stderr = if (proc.errorStream.available() > 0) fromStream(proc.errorStream) else emptyList()
}