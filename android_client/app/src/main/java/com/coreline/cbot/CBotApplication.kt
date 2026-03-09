package com.coreline.cbot

import android.app.Application
import com.coreline.cbot.app.AppContainer

class CBotApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
