# Publishing Guide

This guide describes how to publish the `processor` module as the public JavaToGpu Maven artifact.

## Artifact Coordinates

```text
groupId: io.github.deussixik
artifactId: javatogpu
version: 0.1.0-alpha.1
```

The Gradle project that owns the publication is:

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
```

Output is written under:

```text
processor/build/maven-staging/
```

Inspect the generated POM before release:

```text
processor/build/publications/mavenJava/pom-default.xml
```

## Snapshot Publishing

Use a snapshot version when publishing to the Central snapshot repository:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToCentralSnapshotsRepository -Pjavatogpu.version=0.1.0-SNAPSHOT --console=plain
```

PowerShell users can quote the Gradle property if the shell splits `-P` incorrectly:

```powershell
.\gradlew.bat ":processor:publishMavenJavaPublicationToCentralSnapshotsRepository" "-Pjavatogpu.version=0.1.0-SNAPSHOT" --console=plain
```

## Release Publishing

For the first public alpha:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToCentralReleasesRepository -Pjavatogpu.version=0.1.0-alpha.1 --console=plain
```

PowerShell-safe form:

```powershell
.\gradlew.bat ":processor:publishMavenJavaPublicationToCentralReleasesRepository" "-Pjavatogpu.version=0.1.0-alpha.1" --console=plain
```

After upload, complete the release from the Maven Central / Sonatype portal if the deployment lands in a staging flow that requires manual close/release.

## Recommended Release Flow

1. Run the normal tests:

```powershell
.\gradlew.bat :processor:test --console=plain
```

2. Run the OpenCL operational routine on the validated GPU machine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

3. Build local Maven staging:

```powershell
.\gradlew.bat :processor:publishMavenJavaPublicationToLocalStagingRepository --console=plain
```

4. Inspect `processor/build/publications/mavenJava/pom-default.xml`.

5. Publish snapshot or release with the command above.

## Consumer Example

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'

    runtimeOnly 'org.lwjgl:lwjgl::natives-windows'
}
```

Use the correct LWJGL native classifier for the consumer platform, such as `natives-windows`, `natives-linux`, or `natives-macos`.
