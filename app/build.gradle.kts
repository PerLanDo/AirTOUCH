plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.spatialmotion.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }

  androidResources {
    noCompress += "task"
  }
}

val repoBuildOutputs = rootProject.layout.projectDirectory.dir(".build-outputs")

tasks.register<Copy>("copyDebugApkToBuildOutputs") {
  description = "Copy debug APK to .build-outputs at repo root"
  from(layout.buildDirectory.dir("outputs/apk/debug"))
  include("app-debug.apk")
  into(repoBuildOutputs)
}

tasks.register<Copy>("copyReleaseApkToBuildOutputs") {
  description = "Copy release APK to .build-outputs at repo root"
  from(layout.buildDirectory.dir("outputs/apk/release"))
  include("*.apk")
  into(repoBuildOutputs)
}

afterEvaluate {
  tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApkToBuildOutputs")
  tasks.findByName("assembleRelease")?.finalizedBy("copyReleaseApkToBuildOutputs")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation("androidx.lifecycle:lifecycle-service:2.8.7")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.mediapipe.tasks.vision)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
