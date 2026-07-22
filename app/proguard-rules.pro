# Keep data classes so R8 doesn't strip or rename fields used in Compose states
-keep class com.example.pemutarmusik.data.** { *; }

# Keep ViewModel & Compose state holders
-keep class com.example.pemutarmusik.player.** { *; }