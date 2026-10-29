package com.scanipcamera.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log

class ScanCamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installOemCrashGuardian()
        Log.i(TAG, "ScanCamApp initialized with OEM crash guardian")
    }

    /**
     * Prevents OEM vendor framework bugs (e.g. MediaTek boostfwk Choreographer NullPointer)
     * from terminating the application process.
     */
    private fun installOemCrashGuardian() {
        // 1. Thread Default Handler
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isOemVendorCrash(throwable)) {
                Log.w(TAG, "Suppressed OEM vendor crash in Thread [${thread.name}]: ${throwable.message}")
            } else {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }

        // 2. Main Looper Watchdog (Cockroach Pattern)
        // Keeps the Main Looper alive if a vendor OEM framework bug (like MediaTek boostfwk Choreographer NullPointer)
        // throws an exception on the main looper during frame rendering or dialog Vsync.
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    if (isOemVendorCrash(t)) {
                        Log.w(TAG, "Main looper caught and suppressed OEM vendor crash: ${t.message}")
                    } else {
                        val handler = Thread.getDefaultUncaughtExceptionHandler()
                        if (handler != null) {
                            handler.uncaughtException(Thread.currentThread(), t)
                        } else {
                            throw t
                        }
                    }
                }
            }
        }
    }

    private fun isOemVendorCrash(t: Throwable): Boolean {
        var current: Throwable? = t
        while (current != null) {
            val trace = current.stackTrace
            if (trace.any { elem ->
                    val cls = elem.className.lowercase()
                    cls.contains("mediatek") ||
                    cls.contains("boostfwk") ||
                    cls.contains("boostmodule") ||
                    (cls.contains("choreographer") && current is NullPointerException) ||
                    cls.contains("smartclip") ||
                    (cls.contains("viewrootimpl") && current is NullPointerException)
                }) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        private const val TAG = "ScanCamApp"
    }
}
