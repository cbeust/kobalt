//package com.beust.kobalt.app.remote
//
//import com.beust.kobalt.Args
//import com.beust.kobalt.api.Kobalt
//import com.beust.kobalt.api.Project
//import com.google.gson.Gson
//import org.glassfish.jersey.jetty.JettyHttpContainerFactory
//import org.glassfish.jersey.server.ResourceConfig
//import org.glassfish.jersey.server.ServerProperties
//import javax.ws.rs.GET
//import javax.ws.rs.Path
//import javax.ws.rs.Produces
//import javax.ws.rs.QueryParam
//import javax.ws.rs.core.MediaType
//import javax.ws.rs.core.UriBuilder
//
//class JerseyServer(val initCallback: (String) -> List<Project>, val cleanUpCallback: () -> Unit)
//        : KobaltServer .IServer {
//
//    companion object {
//        lateinit var initCallback: (String) -> List<Project>
//        lateinit var cleanUpCallback: () -> Unit
//    }
//
//    init {
//        JerseyServer.initCallback = initCallback
//        JerseyServer.cleanUpCallback = cleanUpCallback
//    }
//
//    override fun run(port: Int) {
//        val baseUri = UriBuilder.fromUri("http://localhost/").port(port).build()
//        val config = ResourceConfig(KobaltResource::class.java)
//        with (JettyHttpContainerFactory.createServer(baseUri, config)) {
//            try {
//                start()
//                join()
//            } finally {
//                destroy()
//            }
//        }
//
//    }
//}
//
//@Path("/v0")
//class KobaltResource : ResourceConfig() {
//    init {
//        property(ServerProperties.TRACING, "ALL")
//    }
//
//    @GET
//    @Path("ping")
//    @Produces(MediaType.TEXT_PLAIN)
//    fun getDependencies() = "pong"
//
//    @GET
//    @Path("getDependencies")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun getDependencies(@QueryParam("buildFile") buildFile: String) : String {
//        try {
//            val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
//            val args = Kobalt.INJECTOR.getInstance(Args::class.java)
//
//            val projects = JerseyServer.initCallback(buildFile)
//            val dd = dependencyData.dependenciesDataFor(buildFile, args)
//            val result = Gson().toJson(dd)
//            return result
//        } catch(ex: Exception) {
//            return "Error: " + ex.message
//        } finally {
//            JerseyServer.cleanUpCallback()
//        }
//    }
//}
