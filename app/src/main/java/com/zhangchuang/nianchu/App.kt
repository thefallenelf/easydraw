package com.zhangchuang.nianchu

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** 应用类：应用级初始化、全局配置入口 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        // 跟随系统深色模式（浅色纸白 / 深色画布，图标细线深色系）
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}