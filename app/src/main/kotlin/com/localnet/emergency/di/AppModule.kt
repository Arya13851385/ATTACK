package com.localnet.emergency.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.localnet.emergency.network.AlertWebSocketClient
import com.localnet.emergency.network.NsdDiscoveryManager
import com.localnet.emergency.repository.AlertRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideAlertRepository(
        @ApplicationContext context: Context,
        webSocketClient: AlertWebSocketClient,
        nsdDiscoveryManager: NsdDiscoveryManager
    ): AlertRepository = AlertRepository(context, webSocketClient, nsdDiscoveryManager)
}
