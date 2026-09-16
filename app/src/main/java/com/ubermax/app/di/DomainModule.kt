package com.ubermax.app.di

import com.ubermax.app.data.repository.TripRepository
import com.ubermax.app.domain.port.TripHistorySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings de puertos de dominio a implementaciones de datos.
 * Mantiene la capa de dominio desacoplada de Room/Data.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    abstract fun bindTripHistorySource(repository: TripRepository): TripHistorySource
}