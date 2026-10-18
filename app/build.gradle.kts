import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "app.libretv.tv"
 compileSdk = 36
 defaultConfig { applicationId = "app.libretv.tv"; minSdk = 23; targetSdk = 35; versionCode = 2; versionName = "1.1.0"; testInstrumentationRunner = "app.libretv.tv.TvInstrumentation" }
 val signingFile = rootProject.file(".signing/signing.properties")
 if (signingFile.exists()) {
  val props=Properties().apply { signingFile.inputStream().use { load(it) } }
  val localRelease=signingConfigs.create("localRelease") { storeFile=rootProject.file(".signing/release.jks"); storePassword=props.getProperty("storePassword"); keyAlias=props.getProperty("keyAlias"); keyPassword=storePassword }
  buildTypes.getByName("release").signingConfig=localRelease
  buildTypes.getByName("debug").signingConfig=localRelease
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.recyclerview:recyclerview:1.4.0")
 implementation("androidx.media3:media3-exoplayer:1.8.0")
 implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
 implementation("androidx.media3:media3-ui:1.8.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
}
