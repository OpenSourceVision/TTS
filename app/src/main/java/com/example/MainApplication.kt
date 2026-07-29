package com.example

import android.app.Application
import com.example.util.CrashHandler

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}
