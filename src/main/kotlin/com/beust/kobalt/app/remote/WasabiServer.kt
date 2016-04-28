//package com.beust.kobalt.app.remote
//
//import com.beust.kobalt.Args
//import com.beust.kobalt.api.Project
//
//class WasabiServer(val initCallback: (String) -> List<Project>, val cleanUpCallback: () -> Unit)
//        : KobaltServer.IServer {
//    override fun run(port: Int) {
//        with(AppServer(AppConfiguration(port))) {
//            get("/", { response.send("Hello World!") })
//            get("/v0/getDependencies",
//                    {
//                        val buildFile = request.queryParams["buildFile"]
//                        if (buildFile != null) {
//
//                            val projects = initCallback(buildFile)
//                            val result = try {
//                                val dependencyData = Kobalt.INJECTOR.getInstance(DependencyData::class.java)
//                                val args = Kobalt.INJECTOR.getInstance(Args::class.java)
//
//                                val dd = dependencyData.dependenciesDataFor(buildFile, args)
//                                Gson().toJson(dd)
//                            } catch(ex: Exception) {
//                                "Error: " + ex.message
//                            } finally {
//                                cleanUpCallback()
//                            }
//
//                            response.send(result)
//                        }
//                    }
//            )
//            start()
//        }
//    }
//}
//
