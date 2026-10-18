import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "app.libretv.tv"
 compileSdk = 36
 defaultConfig { applicationId = "app.libretv.tv"; minSdk = 23; targetSdk = 35; versionCode = 4; versionName = "1.3.0"; testInstrumentationRunner = "app.libretv.tv.TvInstrumentation" }
 val signingFile = rootProject.file(".signing/signing.properties")
 if (signingFile.exists()) {
  val props=Properties().apply { signingFile.inputStream().use { load(it) } }
  val localRelease=signingConfigs.create("localRelease") { storeFile=rootProject.file(".signing/release.jks"); storePassword=props.getProperty("storePassword"); keyAlias=props.getProperty("keyAlias"); keyPassword=storePassword }
  buildTypes.getByName("release").signingConfig=localRelease
  buildTypes.getByName("debug").signingConfig=localRelease
 }
 compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
 implementation("com.google.zxing:core:3.5.4")
 implementation("androidx.recyclerview:recyclerview:1.4.0")
 implementation("androidx.media3:media3-exoplayer:1.8.0")
 implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
 implementation("androidx.media3:media3-ui:1.8.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
}
