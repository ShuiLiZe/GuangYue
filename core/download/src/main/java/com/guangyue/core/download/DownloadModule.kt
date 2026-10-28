package com.guangyue.core.download

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    /**
     * 下载专用 OkHttp：长读超时 + 大连接池。
     *
     * 播放用的客户端读超时 30s，对「卡住的直链」够用；下载一首几十 MB 的 FLAC 可能几十秒
     * 一个数据块，因此单独给 60s 读超时，避免误判超时后反复 re-range。
     */
    @Provides
    @Singleton
    @com.guangyue.core.plugin.di.DownloadHttp
    fun downloadOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}
