package com.scanipcamera.app

import android.app.Application
import android.util.Log

class ScanCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ScanCamApp initialized")
    }

    companion object {
        private const val TAG = "ScanCamApp"
    }
}
