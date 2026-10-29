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

# ---------------------------------------------------------------- kotlin-reflect（第二道保险）
# quickjs-kt 的 `Bindings_dslKt.adaptedCall` 每次都走
#   canConvertReturnInternally() → castValueOr(value, expectedType, fallback)
# 而 castValueOr 第一步就是 `expectedType.classifier as KClass` + `isInstance(value)`。
# 一旦 kotlin-reflect 被 R8 裁掉，KType 退化成 stub，isInstance 恒 false，于是去查
# TypeConverters.classTypeMap 并抛：
#   No such type converter to convert 'boolean (Kotlin reflection is not available)'
#   to 'java.lang.String'
# 0.0.1 的 release 包就是这样让 57 个插件在**加载阶段**全部失败的（`module.exports` 里
# `platform: true`、`version: 1` 之类的字段让求值结果不是 String）。
#
# 代码层已经不再依赖反射转换（PluginRuntime.evalBlocking 一律 `__gy_text(...)`，
# 宿主函数收 Array<Any?>、返回 String），这里把反射本身也保住，两条腿都站着。
-keep class kotlin.reflect.** { *; }
-keep interface kotlin.reflect.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keepclassmembers class kotlin.reflect.jvm.internal.impl.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin 标准库里的反射入口（KType / KClass / KCallable 的实现类）不能混淆
-keep class kotlin.KotlinNullPointerException { *; }
-keepclassmembers class kotlin.** {
    @kotlin.Metadata <methods>;
}
-keep,allowoptimization,allowshrinking class kotlin.Unit
-keep,allowoptimization,allowshrinking class kotlin.jvm.internal.** { *; }

# ---------------------------------------------------------------- OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------------------------------------------------------------- jaudiotagger (封面写入)
# jaudiotagger 的 StandardArtwork.getImage() 引用了 java.awt / javax.imageio —— 这些类在
# Android 上不存在，只有真正去读封面图片时才会走到。之前 release 没有开 R8，所以从没暴露。
-dontwarn java.awt.**
-dontwarn javax.imageio.**
