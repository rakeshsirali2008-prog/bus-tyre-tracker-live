package com.bustyre.tracker

import android.app.Application
import com.bustyre.tracker.data.AppDatabase
import com.bustyre.tracker.data.FleetRepository

class BusTyreApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: FleetRepository by lazy { FleetRepository(database) }
}
