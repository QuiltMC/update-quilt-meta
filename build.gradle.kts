plugins {
    alias(libs.plugins.shadow)
    application
    idea
}

val javaVersion = 25

group = "org.quiltmc"

version = providers.environmentVariable("VERSION").getOrElse("development")

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jetbrains.annotations)

    implementation(platform(libs.aws.bom))
    implementation(libs.aws.s3)

    implementation(libs.bundles.b2)
    implementation(libs.bouncycastle)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.picocli)
    annotationProcessor(libs.picocli.annotations)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.bundles.tinylog)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.bundles.junit)
}

application {
    mainClass = "org.quiltmc.meta.update.Main"
}

tasks.named<JavaExec>("run").configure {
    args.add("--local-output-dir")
    args.add("../__quilt_meta_test")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.named<Jar>("jar").configure {
    manifest.attributes["Main-Class"] = "org.quiltmc.meta.update.Main"
    manifest.attributes["Implementation-Version"] = project.version.toString()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

tasks.named<Test>("test").configure {
    useJUnitPlatform()
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
    }
}
