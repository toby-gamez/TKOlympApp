# Keep Kotlin metadata
-keepclassmembers class kotlin.Metadata { *; }

# Keep kotlinx.serialization generated serializers
-keepclassmembers class **$$serializer { public static final *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep Composable-annotated methods (used by Compose at runtime)
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Activities and Fragments entrypoints
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment

# Keep ViewModel API
-keep class androidx.lifecycle.ViewModel { *; }

# Keep Java serialization UID fields if present
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
}
