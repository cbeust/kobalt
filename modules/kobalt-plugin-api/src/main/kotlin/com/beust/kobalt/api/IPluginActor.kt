package com.beust.kobalt.api

interface IPluginActor {
    /**
     * Clean up any state that your actor might have saved so it can be run again.
     */
    fun shutdownActors() {}
}

interface IContributor : IPluginActor

interface IInterceptor : IPluginActor
