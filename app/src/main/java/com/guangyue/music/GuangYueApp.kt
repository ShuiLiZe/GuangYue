package com.guangyue.music

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The application entry point; Hilt builds the object graph from here.
 *
 * 0.0.14 真机「下载一直解析」：DownloadWorker 是 @HiltWorker（构造函数带注入参数），
 * 默认 WorkerFactory 造不出来，任务直接失败、进度停在「解析中」。这里把 WorkManager
 * 交给 HiltWorkerFactory，并在清单里关掉默认初始化。
 */
@HiltAndroidApp
class GuangYueApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
