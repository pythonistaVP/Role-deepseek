# ProGuard / R8 правила для release-сборки Role DeepSeek.

# ---------- Kotlin / Coroutines ----------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------- kotlinx.serialization ----------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pythonistavp.roledeepseek.**$$serializer { *; }
-keepclassmembers class com.pythonistavp.roledeepseek.** {
    *** Companion;
}
-keepclasseswithmembers class com.pythonistavp.roledeepseek.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-dontwarn dagger.hilt.**

# ---------- Coil ----------
-dontwarn okio.**
-dontwarn okhttp3.**
-keep class coil.** { *; }

# ---------- ZXing ----------
-dontwarn com.google.zxing.**

# ---------- uCrop ----------
-dontwarn com.yalantis.ucrop.**

# ---------- Наши модели (используются в JSON-сериализации и Room) ----------
-keep class com.pythonistavp.roledeepseek.data.model.** { *; }
-keep class com.pythonistavp.roledeepseek.data.local.entity.** { *; }

# ---------- AppWidget (обновляется системой через reflection) ----------
-keep class com.pythonistavp.roledeepseek.widget.** { *; }

# Убираем логи в релизе
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Сохраняем номера строк для нормальных стектрейсов
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
