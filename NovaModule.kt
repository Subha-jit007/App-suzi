package com.nova.ai.utils

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NovaModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }

    // All other @Inject @Singleton classes are auto-provided by Hilt
    // SecureStorage, SpeechEngine, WakeWordDetector, TtsEngine,
    // IntentParser, CommandExecutor, ClaudeRepository, FileGenerator
    // are all @Singleton with @Inject constructors — no extra config needed.
}
