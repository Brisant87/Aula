import java.time.Duration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.aula.nativeapp"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aula.nativeapp"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "0.6"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Debug builds sign with the shared debug.keystore at the project root.
  // The GitHub Actions workflow generates it (same approach as the Cognata donor).
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
      isCrunchPngs = false
      isMinifyEnabled = false
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
    buildConfig = true
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.security.crypto)
  implementation(libs.kotlinx.coroutines.core)
  debugImplementation(libs.androidx.compose.ui.tooling)
  testImplementation("junit:junit:4.13.2")
}

// Unit-test gate discipline (mirrors the Cognata donor): the CI workflow runs
// testDebugUnitTest BEFORE assembleDebug, so a red battery fails the build.
// This block only configures reporting — it changes no app behavior.
tasks.withType<Test> {
  failFast = false
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showStandardStreams = true
    events("passed", "failed", "skipped", "standard_out", "standard_error")
  }
}
