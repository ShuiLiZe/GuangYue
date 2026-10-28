# GuangYue R8 / ProGuard 规则
#
# 为什么需要这份文件：
# 0.0.1 之前插件导入在真机上全量失败，报
#   No such type converter to convert 'boolean (Kotlin reflection is not available)'
#   to 'java.lang.String'
# 这句话里的「Kotlin reflection is not available」正是 R8 去掉 kotlin-reflect 后的提示：
# quickjs-kt 用它自己的 TypeConverter 把 JS 返回值反射转换成目标 Kotlin 类型，一旦
# kotlin-reflect 被裁掉，连 boolean 都会被误判成「未知类型」。
#
# 修复的主线是代码层面不再依赖反射转换（元信息一律 JS 侧 JSON.stringify + kotlinx.serialization
# 宽松解析）。下面的 keep 规则是第二道保险：让 quickjs-kt 的桥接类、kotlinx.serialization 的
# 生成器、以及插件模型全部躲过 shrink / obfuscate，确保将来打开 isMinifyEnabled 时不会因为
# 反射信息丢失而复发同一个错误。

# ---------------------------------------------------------------- QuickJS 桥接
-keep class com.dokar.quickjs.** { *; }
-keep interface com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**
-keepclassmembers class com.dokar.quickjs.binding.** { *; }

# ---------------------------------------------------------------- kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.guangyue.**$$serializer { *; }
-keepclassmembers class com.guangyue.** {
    *** Companion;
}
-keepclasseswithmembers class com.guangyue.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------- 插件模型 / 运行时
# PluginMeta / MusicFreeModels 会被 JSON 反序列化，字段名参与序列化，不能混淆
-keep class com.guangyue.core.plugin.** { *; }
-keepclassmembers class com.guangyue.core.plugin.** { *; }

# ---------------------------------------------------------------- Kotlin 基础
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn kotlin.**

# ---------------------------------------------------------------- OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
