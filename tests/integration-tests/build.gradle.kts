plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("apollo.test")
  id("com.apollographql.apollo3")
}

apolloTest {
  mpp {}
}

kotlin {
  /**
   * Extra target to test the java codegen
   */
  jvm("javaCodegen") {
    withJava()
  }

  sourceSets {
    findByName("commonMain")?.apply {
      dependencies {
        implementation(libs.apollo.api)
        implementation(libs.apollo.normalizedcache)
        implementation(libs.apollo.adapters)
        implementation(libs.apollo.runtime)
        implementation(libs.apollo.engine.ktor)
      }
    }

    findByName("commonTest")?.apply {
      dependencies {
        implementation(libs.apollo.mockserver)
        implementation(libs.apollo.testingsupport)
        implementation(libs.kotlinx.coroutines)
        implementation(libs.kotlinx.serialization.json.asProvider())
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
      }
    }

    findByName("javaCodegenTest")?.apply {
      dependencies {
        // Add test-junit manually because configureMppTestsDefaults did not do it for us
        implementation(libs.kotlin.test.junit)
      }
    }

    findByName("jvmTest")?.apply {
      dependencies {
        implementation(libs.okhttp.logging)
      }
    }
  }
}

fun configureApollo(generateKotlinModels: Boolean) {
  val extra = if (generateKotlinModels) "kotlin" else "java"

  configure<com.apollographql.apollo3.gradle.api.ApolloExtension> {
    file("src/main/graphql/com/apollographql/apollo3/integration").listFiles()!!
        .filter { it.isDirectory }
        .forEach {
          service("${it.name}-$extra") {
            when (it.name) {
              "httpcache" -> {
                generateOperationOutput.set(true)
                mapScalar("Date", "kotlinx.datetime.LocalDate")
              }

              "upload" -> {
                operationManifestFormat.set("persistedQueryManifest")
                mapScalar("Upload", "com.apollographql.apollo3.api.Upload")
              }

              "normalizer" -> {
                generateFragmentImplementations.set(true)
                mapScalar("Date", "kotlinx.datetime.LocalDate")
                if (generateKotlinModels) {
                  mapScalar("Instant", "kotlinx.datetime.Instant", "com.apollographql.apollo3.adapter.KotlinxInstantAdapter")
                } else {
                  mapScalar("Instant", "kotlinx.datetime.Instant", "com.apollographql.apollo3.adapter.KotlinxInstantAdapter.INSTANCE")
                }
                sealedClassesForEnumsMatching.set(listOf("Episode"))
              }

              "fullstack" -> {
                mapScalar("Date", "com.example.MyDate")
              }
            }

            srcDir(file("src/main/graphql/com/apollographql/apollo3/integration/${it.name}/"))
            packageName.set("com.apollographql.apollo3.integration.${it.name}")
            codegenModels.set("operationBased")
            this.generateKotlinModels.set(generateKotlinModels)
            generateOptionalOperationVariables.set(false)
            configureConnection(generateKotlinModels)
          }
        }
    file("src/commonTest/kotlin/test").listFiles()!!
        .filter { it.isDirectory }
        .forEach {
          service("${it.name}-$extra") {
            srcDir(it)

            when (it.name) {
              "fragment_normalizer" -> {
                generateFragmentImplementations.set(true)
              }
            }
            if (it.name == "schema") {
              generateSchema.set(true)
              alwaysGenerateTypesMatching.set(listOf(".*"))
            }
            this.generateKotlinModels.set(generateKotlinModels)
            codegenModels.set("operationBased")
            packageName.set(it.name)
            generateOptionalOperationVariables.set(false)
            configureConnection(generateKotlinModels)
          }
        }
  }
}

fun com.apollographql.apollo3.gradle.api.Service.configureConnection(generateKotlinModels: Boolean) {
  outputDirConnection {
    if (System.getProperty("idea.sync.active") == null) {
      if (generateKotlinModels) {
        connectToKotlinSourceSet("jvmTest")
        connectToKotlinSourceSet("jsTest")
        connectToKotlinSourceSet("appleTest")
      } else {
        connectToJavaSourceSet("main")
      }
    } else {
      // For autocomplete to work
      connectToKotlinSourceSet("commonTest")
    }
  }
}
configureApollo(true)
configureApollo(false)

// See https://youtrack.jetbrains.com/issue/KT-56019
val myAttribute = Attribute.of("com.apollographql.test", String::class.java)

configurations.named("jvmApiElements").configure {
  attributes {
    attribute(myAttribute, "jvm")
  }
}

configurations.named("javaCodegenApiElements").configure {
  attributes {
    attribute(myAttribute, "java")
  }
}

configurations.named("jvmRuntimeElements").configure {
  attributes {
    attribute(myAttribute, "jvm-runtime")
  }
}

configurations.named("javaCodegenRuntimeElements").configure {
  attributes {
    attribute(myAttribute, "java-runtime")
  }
}

configurations.named("jvmSourcesElements").configure {
  attributes {
    attribute(myAttribute, "jvm")
  }
}

configurations.named("javaCodegenSourcesElements").configure {
  attributes {
    attribute(myAttribute, "java")
  }
}

val checkPersistedQueryManifest = tasks.register("checkPersistedQueryManifest") {
  dependsOn("generateApolloSources")
  val buildFile = file("build/generated/manifest/apollo/upload-kotlin/persistedQueryManifest.json")
  val fixtureFile = file("testFixtures/manifest.json")
  doLast {
    check(
        buildFile.readText() == fixtureFile.readText()
    ) {
      "Persisted Query Manifest has changed"
    }
  }
}
tasks.named("jvmTest").configure {
  dependsOn(checkPersistedQueryManifest)
}
