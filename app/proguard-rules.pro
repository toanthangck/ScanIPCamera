# Proguard rules for ScanIPCamera

# Keep models for JSON/XML reflection if needed
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
