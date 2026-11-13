package com.maxence.offlinebaseltour

import android.app.Application
import com.maxence.offlinebaseltour.database.AppDatabase
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class BaselTourApp : Application() {
    
    companion object {
        lateinit var database: AppDatabase
            private set
        lateinit var instance: BaselTourApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AndroidGraphicFactory.createInstance(this)
        database = AppDatabase.getDatabase(this)
    }
}
