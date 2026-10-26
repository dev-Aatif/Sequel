package dev.sequel.app

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom AndroidJUnitRunner that replaces the default Application class
 * with [HiltTestApplication] to enable Hilt dependency injection in instrumentation tests,
 * and initializes WorkManager with a test configuration.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        try {
            val config = Configuration.Builder().build()
            WorkManager.initialize(targetContext, config)
        } catch (_: IllegalStateException) {
            // Already initialized
        }
    }
}
