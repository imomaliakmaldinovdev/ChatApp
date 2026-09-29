import java.util.Properties
plugins { id("com.android.application") }
// Keep a fresh checkout buildable while waiting for the teacher's Firebase file.
val hasGoogleServices = file("google-services.json").exists() ||
    file("src").walkTopDown().any { it.isFile && it.name == "google-services.json" }
if (hasGoogleServices) apply(plugin = "com.google.gms.google-services")
val firebase = Properties().apply {
    val file = rootProject.file("firebase.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
android {
    namespace = "com.imomali.chatapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.imomali.chatapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(firebase.getProperty("projectId", "")))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", quoted(firebase.getProperty("applicationId", "")))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(firebase.getProperty("apiKey", "")))
        buildConfigField("String", "EMULATOR_HOST", quoted(firebase.getProperty("emulatorHost", "10.0.2.2")))
    }
    buildTypes {
        debug { buildConfigField("boolean", "USE_EMULATORS", providers.gradleProperty("useFirebaseEmulators").orElse(firebase.getProperty("useEmulators", "false")).get().toBoolean().toString()) }
        release {
            buildConfigField("boolean", "USE_EMULATORS", "false")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    lint { abortOnError = true }
}
dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.1")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.firebase:firebase-auth:24.2.0")
    implementation("com.google.firebase:firebase-firestore:26.6.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
