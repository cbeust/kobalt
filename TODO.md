To do:

-[ ] "All artifacts successfully uploaded" is shown before the upload is actually done
-[ ] use groupId/artifactId
-[ ] Console mode with watch service, so recompilation can occur as soon as a source file is modified
-[ ] ProjectGenerator: support migration from pom.xml (starting with dependencies)
-[ ] repos() must appear before plugins(): fix that
-[ ] Support version ranges
-[ ] Generate .idea and other IDEA files
-[ ] logs for users should not show any timestamp, class file or thread id, this should only be in --dev mode
-[ ] Fetch .pom with DynamicGraph
-[ ] Centralize all the executors
-[ ] Archetypes (e.g. "--initWith kobalt-plug-in")
-[ ] Compile TestNG (including generating Version.java and OSGi headers)
-[ ] Storage API: Plugins.storage("myplugin").get/set()
-[ ] Support additional .kt files in ~/.kobalt/src
-[ ] generateArchive() should use Path instead of File
-[ ] archetypes, e.g. --initWith kotlin
-[ ] --init: import dependencies from build.gradle
-[ ] Bug: --tasks displays multiple tasks when there are multiple projects
-[ ] Bug: ./kobaltw --dryRun kobalt:uploadJcenter runs "kobalt-wrapper:clean" twice
-[ ] Replace File with java.nio.Files and Path
-[ ] Create a wiki page for plugins
-[ ] Make kobaltw executable in the zip file
-[ ] --resolve <dep>

Done:

-[x] Project ordering: kotlinProject(wrapper) {}
-[x] Make files appear in download list automatically on bintray (undocumented API)
-[x] Link kobalt to JCenter
-[x] uploadMaven
  + Create sources.jar
  + Create javadoc.jar
-[x] --update
-[x] Support .pom pointing to other deps (and no jars)
-[x] provided scope
-[x] --dryRun
-[x] --init: import dependencies from pom.xml
-[x] --checkVersions: displays which plugins have a newer version than the one specified in the build
-[x] Make it possible to target jar for individual projects: ./kobaltw kobalt:uploadJcenter
-[x] --buildFile doesn't use the local .kobalt directory
-[x] Better plugin() parsing
-[x] kobalt-wrapper.jar contains too much stuff, should be just com.beust.kobalt.wrapper.* + kotlin runtime or maybe
just a straight Java class with minimal dependencies for fast start up
-[x] Add repos from the build file
-[x] Support tasks added in the build file
-[x] Replace "removePrefixes" with an overloaded include(prefix, file)
-[x] --init: Don't overwrite existing file
-[x] Handle snapshots/metadata: https://repository.jboss.org/nexus/content/repositories/root_repository//commons-lang/commons-lang/2.7-SNAPSHOT/commons-lang-2.7-SNAPSHOT.jar
-[x] JUnit
-[x] Compiler nowarn section
-[x] Parse plugins and repos in build files
-[x] Stop always recompiling preBuildScript.jar
-[x] Upload non maven files
-[x] Jar packaging: include the jar in the jar (not supported by JarFile)
-[x] Encapsulate BuildFile for better log messages


