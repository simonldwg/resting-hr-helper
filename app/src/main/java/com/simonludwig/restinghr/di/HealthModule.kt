package com.simonludwig.restinghr.di

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureClient
import com.simonludwig.restinghr.data.HealthServicesHeartRateSensor
import com.simonludwig.restinghr.domain.HeartRateSensor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HealthModule {

    @Provides
    @Singleton
    fun provideMeasureClient(@ApplicationContext context: Context): MeasureClient =
        HealthServices.getClient(context).measureClient

    @Provides
    @Singleton
    fun provideHeartRateSensor(measureClient: MeasureClient): HeartRateSensor =
        HealthServicesHeartRateSensor(measureClient)
}
