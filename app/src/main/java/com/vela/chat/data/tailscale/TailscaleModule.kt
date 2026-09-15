package com.vela.chat.data.tailscale

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the tailnet layer. Only the engine needs a binding —
 * [TailnetManager] and [PeerScanner] are constructor-injected `@Singleton`s and
 * need no module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TailscaleModule {

    /** Binds the session-based engine (rides on the official Tailscale app) as the active [TailnetEngine]. */
    @Binds
    @Singleton
    abstract fun bindTailnetEngine(impl: SessionTailnetEngine): TailnetEngine
}
