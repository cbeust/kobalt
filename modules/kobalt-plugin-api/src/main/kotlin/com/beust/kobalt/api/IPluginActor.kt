package com.beust.kobalt.api

interface IPluginActor {
    /**
     * Clean up any state that your actor might have saved so it can be run again.
     */
    fun cleanUpActors() {}
}

interface IContributor : IPluginActor

interface IInterceptor : IPluginActor

interface IListener : IPluginActor

