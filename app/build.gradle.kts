plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
  namespace = "com.geovision.geoviento"; compileSdk = 34
  defaultConfig { applicationId = "com.geovision.geoviento"; minSdk = 26; targetSdk = 34; versionCode = 4; versionName = "1.0.3" }
  buildFeatures { compose = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.activity:activity-compose:1.9.2")
  implementation("androidx.compose.ui:ui:1.7.2"); implementation("androidx.compose.material3:material3:1.3.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
  implementation("com.google.android.gms:play-services-location:21.3.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("com.squareup.retrofit2:retrofit:2.11.0"); implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation("org.maplibre.gl:android-sdk:10.2.0")
}
