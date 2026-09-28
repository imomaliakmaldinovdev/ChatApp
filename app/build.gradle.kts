import java.util.Properties
plugins { id("com.android.application") }
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
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(firebase.getProperty("projectId", "")))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", quoted(firebase.getProperty("applicationId", "")))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(firebase.getProperty("apiKey", "")))
        buildConfigField("String", "EMULATOR_HOST", quoted(firebase.getProperty("emulatorHost", "10.0.2.2")))
    }
    buildTypes {
        debug { buildConfigField("boolean", "USE_EMULATORS", firebase.getProperty("useEmulators", "false").toBoolean().toString()) }
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.firebase:firebase-auth:24.2.0")
    implementation("com.google.firebase:firebase-firestore:26.6.0")
    testImplementation("junit:junit:4.13.2")
}
