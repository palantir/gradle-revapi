<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-revapi"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-revapi

_A gradle plugin which runs [Revapi](https://revapi.org) to warn you when there are breaks to your Java library's
public API or ABI._

Using the plugin should be as simple as:

1. Adding the plugin to your buildscript:
    ```gradle
    buildscript {
        // ...

        dependencies {
            classpath 'com.palantir.gradle.revapi:gradle-revapi:<latest-version>'
        }
    }
    ```

1. And then apply the plugin to all the projects you want to ensure API compatibility:
    ```diff
    // In my Java project's build.gradle that publishes a jar
    +apply plugin: 'com.palantir.revapi'
    ```

1. Revapi will be run as part of `./gradlew check`. Alternatively, you can call `./gradlew revapi` directly.

## Motivation

Accidentally releasing API or ABI breaks in java libraries has bad consequences for library consumers.
In the case of API breaks, consumers have to perform some kind of manual action upgrade to newer library
versions, which may be difficult for them  are difficult as they will
require manual action to move past the breaks.

With ABI breaks, the situation can be even worse, as uses of the library compile but uses in jars of the old API fail at
runtime. An example from [Tritium](https://github.com/palantir/tritium) is where a
[method was changed from a `Map` to a `SortedMap`](https://github.com/palantir/tritium/pull/272#issuecomment-496526307).
This compiles against direct dependencies but transitive dependencies using the older API
would produce a `NoSuchMethodError` at runtime, which has caused a number of problems in production code. Similarly,
there was a covariant return type change to `docker-compose-rule` (`ImmtuableDockerComposeRule` -> `DockerComposeRule`)
which [caused ABI breaks in `docker-proxy-rule`](https://github.com/palantir/docker-proxy-rule/releases/tag/0.8.0),
among projects.

## Configuration

`gradle-revapi` should work out of the box for most uses cases once applied. By default it compares against the previous
version of the jar from the project it is applied in by finding the last tag using `git describe`. However, if you need
to need to override the artifact to compare against, you can do so:

```gradle
revapi {
    oldGroup = '<artifact-group>'
    oldNamed = '<artifact-name>'
    oldVersion = '<artifact-version>'
}
```

### Version overrides

Sometimes the previous release will have a successfully applied a git tag but a failed publish build. In this
case `gradle-revapi` will fail as it cannot resolve the previous API to compare against. To resolve this, you can
possible to set a *version override* that will use a different version instead of the last git tag. To do so, 
use the

```
./gradle revapiVersionOverride --replacement-version <last-published-version>
```

task to use correctly published version instead. This will creare an entry in `.palantir/revapi.yml` of the following
format:

```yml
versionOverrides:
  group:name:version: versionOverride
```
