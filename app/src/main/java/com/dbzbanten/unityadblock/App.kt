package com.dbzbanten.unityadblock

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UnityBypassManager.init(this)
    }
}
