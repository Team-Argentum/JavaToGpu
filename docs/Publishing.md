# Publishing Guide

This guide describes how to publish the JavaToGpu Maven artifacts: the main `processor` module and
the optional `ir-validation` strict-build add-on.

## Artifact Coordinates

```text
groupId: io.github.deussixik
artifactId: javatogpu
version: 0.1.0-alpha.1
```

The Gradle project that owns the main publication is:

```text
:processor
```

## What Is Published

The `mavenJava` publication includes:

- compiled jar
- sources jar
- javadoc jar
- generated POM metadata
- PGP signatures for release publishing

Gradle module metadata is disabled for publication so a build-host-specific LWJGL native classifier is not published as universal metadata. Consumers should add their own LWJGL native classifier for their operating system when they need runtime OpenCL execution.

## Optional IR Validation Artifact

The `ir-validation` module is a separate strict-build add-on:

```text
groupId: io.github.deussixik
artifactId: javatogpu-ir-validation
version: 0.1.0-alpha.1
```

It contributes an optional compiler IR validation provider through Java `ServiceLoader`. Users add it to the annotation-processor path and enable `-Ajavatogpu.irValidation=diagnostic`, `strictSafety`, or `strictOptimizer` when they want extra lowered-IR checks before OpenCL emission.

The Gradle project that owns the optional publication is:

```text
:ir-validation
```

## Release Dependency Baseline

The OpenCL runtime depends on the published Packager release:

```text
io.github.deussixik:packager:1.0.0-alpha.1
```

Release publishing is guarded by `validateMavenCentralReleaseReadiness`, which fails if the generated POM contains snapshot dependencies or JitPack branch dependencies.

## Secrets

Never commit Maven Central credentials or signing keys.

Put local release secrets in:

```text
~/.gradle/gradle.properties
```

Recommended properties:

```properties
mavenCentralUsername=<central-token-username>
mavenCentralPassword=<central-token-password>
signingInMemoryKey=<ascii-armored-private-key>
signingInMemoryKeyPassword=<private-key-password>
```

Equivalent environment variables are also supported:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_IN_MEMORY_KEY
SIGNING_IN_MEMORY_KEY_PASSWORD
```

The local `gradle.properties` file in this repository root is ignored by Git as an extra safety net, but the preferred location is still `~/.gradle/gradle.properties`.

## Local Staging Check

Before publishing remotely, build the local staging repository:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToLocalStagingRepository --console=plain
.\gradlew.bat :ir-validation:publishMavenJavaPublicationToLocalStagingRepository --console=plain
```

Output is written under:

```text
processor/build/maven-staging/
ir-validation/build/maven-staging/
```

Inspect the generated POMs before release:

```text
processor/build/publications/mavenJava/pom-default.xml
ir-validation/build/publications/mavenJava/pom-default.xml
```

## Snapshot Publishing

Use a snapshot version when publishing to the Central snapshot repository:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToCentralSnapshotsRepository -Pjavatogpu.version=0.1.0-SNAPSHOT --console=plain
.\gradlew.bat :ir-validation:publishMavenJavaPublicationToCentralSnapshotsRepository -Pjavatogpu.version=0.1.0-SNAPSHOT --console=plain
```

PowerShell users can quote the Gradle property if the shell splits `-P` incorrectly:

```powershell
.\gradlew.bat ":processor:publishMavenJavaPublicationToCentralSnapshotsRepository" "-Pjavatogpu.version=0.1.0-SNAPSHOT" --console=plain
.\gradlew.bat ":ir-validation:publishMavenJavaPublicationToCentralSnapshotsRepository" "-Pjavatogpu.version=0.1.0-SNAPSHOT" --console=plain
```

## Release Publishing

For the first public alpha:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToCentralReleasesRepository -Pjavatogpu.version=0.1.0-alpha.1 --console=plain
.\gradlew.bat :ir-validation:publishMavenJavaPublicationToCentralReleasesRepository -Pjavatogpu.version=0.1.0-alpha.1 --console=plain
```

PowerShell-safe form:

```powershell
.\gradlew.bat ":processor:publishMavenJavaPublicationToCentralReleasesRepository" "-Pjavatogpu.version=0.1.0-alpha.1" --console=plain
.\gradlew.bat ":ir-validation:publishMavenJavaPublicationToCentralReleasesRepository" "-Pjavatogpu.version=0.1.0-alpha.1" --console=plain
```

After upload, complete the release from the Maven Central / Sonatype portal if the deployment lands in a staging flow that requires manual close/release. Release the main artifact and the optional IR validation artifact with the same version.

## Recommended Release Flow

1. Run the normal tests for both published modules:

```powershell
.\gradlew.bat :processor:test :ir-validation:test --console=plain
```

2. Run the OpenCL operational routine on the validated GPU machine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

3. Run Maven Central release-readiness guards for both publications:

```powershell
.\gradlew.bat :processor:validateMavenCentralReleaseReadiness :ir-validation:validateMavenCentralReleaseReadiness --console=plain
```

4. Build local Maven staging:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToLocalStagingRepository --console=plain
.\gradlew.bat :ir-validation:publishMavenJavaPublicationToLocalStagingRepository --console=plain
```

5. Inspect both generated POM files:

```text
processor/build/publications/mavenJava/pom-default.xml
ir-validation/build/publications/mavenJava/pom-default.xml
```

6. Publish snapshot or release for both modules with the commands above.

## Consumer Example

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1' // optional strict IR checks

    runtimeOnly 'org.lwjgl:lwjgl::natives-windows'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

Use the correct LWJGL native classifier for the consumer platform, such as `natives-windows`, `natives-linux`, or `natives-macos`.
