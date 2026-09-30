package com.qqmmxx.piaojia

import android.app.Application
import com.qqmmxx.piaojia.data.AppDatabase
import com.qqmmxx.piaojia.data.ImageManager

class ExpenseApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val imageManager: ImageManager by lazy { ImageManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ExpenseApplication
            private set
    }
} 