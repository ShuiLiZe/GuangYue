package com.guangyue.core.plugin.di

/**
 * 限定符：播放 / 插件解析直链用的 OkHttp。
 *
 * 为什么需要它：:core:download 另外提供了一个「长读超时 + 大连接池」的下载客户端，
 * 两个同类型的 @Singleton 绑定会触发 Dagger DuplicateBindings（Hilt 的
 * MissingBinding / DuplicateBindings 只在 :app 聚合时暴露）。这里用限定符把两者分开，
 * 注入点按用途取。
 *
 * 放在 :core:plugin 而不是 :core:player —— :core:plugin 不能反向依赖 :core:player，
 * 而 PlayerModule（:core:player）本来就已经依赖 :core:plugin。
 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackHttp

/** 限定符：下载专用的 OkHttp（长读超时，见 core:download 的 DownloadModule）。 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadHttp
