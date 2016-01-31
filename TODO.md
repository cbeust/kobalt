To do:

Android:

- [ ] Dex dependencies into kobaltBuild/intermediates/pre-dexed and preserve those across builds

General

- [ ] Apt should run from serviceloader
- [ ] Console mode with watch service, so recompilation can occur as soon as a source file is modified
- [ ] ProjectGenerator: support migration from pom.xml (starting with dependencies)
- [ ] Specify where to upload snapshots
- [ ] repos() must appear before plugins(): fix that
- [ ] Generate .idea and other IDEA files
- [ ] Fetch .pom with DynamicGraph
- [ ] Centralize all the executors
- [ ] Archetypes (e.g. "--initWith kobalt-plug-in")
- [ ] Compile TestNG (last piece missing: OSGi headers)
- [ ] Support additional .kt files in ~/.kobalt/src
- [ ] --init: import dependencies from build.gradle
- [ ] --init: also extract kobalt.bat (or generate it along with kobaltw)
- [ ] Encapsulate ProcessBuilder code

Done:

- [x] Compile with javax.tool
- [x] Android: multiple -source/-target flags
- [x] Dokka: allow multiple format outputs e.g `outputFormat("html", "javadoc")`
- [x] Finish abstracting everything in `JvmCompilerPlugin`
- [x] Dependent projects: kobalt should automatically add the dependency on the jar file
- [x] Make what version preBuildScript.jar was compiled with and wipe on new version
- [x] Get rid of the $JAVA_HOME requirement
- [x] getDependencies() should return the transitive dependencies
- [x] Storage API: Plugins.storage("myplugin").get/set()
- [x] Add the % download display in the Kotlin wrapper
- [x] archetypes, e.g. --initWith kotlin
- [x] Project ordering: kotlinProject(wrapper) {}
- [x] Make files appear in download list automatically on bintray (undocumented API)
- [x] Link kobalt to JCenter
- [x] uploadMaven
  + Create sources.jar
  + Create javadoc.jar
- [x] --update
- [x] Support .pom pointing to other deps (and no jars)
- [x] provided scope
- [x] --dryRun
- [x] --init: import dependencies from pom.xml
- [x] --checkVersions: displays which plugins have a newer version than the one specified in the build
- [x] Make it possible to target jar for individual projects: ./kobaltw kobalt:uploadJcenter
- [x] --buildFile doesn't use the local .kobalt directory
- [x] Better plugin() parsing
- [x] kobalt-wrapper.jar contains too much stuff, should be just com.beust.kobalt.wrapper.* + kotlin runtime or maybe
just a straight Java class with minimal dependencies for fast start up
- [x] Add repos from the build file
- [x] Support tasks added in the build file
- [x] Replace "removePrefixes" with an overloaded include(prefix, file)
- [x] --init: Don't overwrite existing file
- [x] Handle snapshots/metadata: https://repository.jboss.org/nexus/content/repositories/root_repository//commons-lang/commons-lang/2.7-SNAPSHOT/commons-lang-2.7-SNAPSHOT.jar
- [x] JUnit
- [x] Compiler nowarn section
- [x] Parse plugins and repos in build files
- [x] Stop always recompiling preBuildScript.jar
- [x] Upload non maven files
- [x] Jar packaging: include the jar in the jar (not supported by JarFile)
- [x] Encapsulate BuildFile for better log messages
- [x] The test runner only selects classes with a parameterless constructor, which works for JUnit but not for TestNG
- [x] Add a "Auto complete Build.kt" menu in the plug-in
- [x] "All artifacts successfully uploaded" is shown before the upload is actually done
- [x] use groupId/artifactId
 factories
- [x] Support version ranges
- [x] Upload in a thread pool
- [x] logs for users should not show any timestamp, class file or thread id, this should only be in --dev mode
- [x] Bug: --tasks displays multiple tasks when there are multiple projects
- [x] Bug: ./kobaltw --dryRun kobalt:uploadJcenter runs "kobalt-wrapper:clean" twice
- [x] Create a wiki page for plugins
- [x] Make kobaltw executable in the zip file
- [x] --resolve <dep>

- [x] Move the calculated applicationId back into the merged AndroidManifest.xml
- [x] Dex from android builder
- [x] Keep exploded aars between runs
- [x] aars keep being refetched
- [x] See if there is an android manifest file in builder
- [x] Auto add variant

