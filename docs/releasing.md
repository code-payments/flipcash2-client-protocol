# Releasing

`.github/workflows/publish.yml`, run from the Actions tab with a version like `0.1.0`. One
version covers both languages: the Kotlin artifact goes to Maven Central, and the git tag the
workflow pushes *is* the Swift Package release, since SPM resolves source straight from this
repo.

The workflow refuses a version that is already tagged, builds and signs everything before it
uploads anything, and tags last — so a broken POM or a stale `Sources/` fails while the version
number is still spendable. Maven Central will not accept a re-published version either, which is
what makes the pre-flight check worth having. `dry_run` does everything except upload and tag.

Consuming the result:

```kotlin
implementation("com.flipcash:flipcash2-client-protocol:0.1.0")
```

```swift
.package(url: "https://github.com/code-payments/flipcash2-client-protocol", from: "0.1.0")
```

## Coordinates vs namespace

The artifact coordinates and the generated namespace are deliberately different. The artifact
publishes under `com.flipcash`, the verified Central namespace; the code inside stays in
`com.codeinc.flipcash.gen.*`, because that is what the app imports and it is set by
`java_package` in the protos.

## Secrets

Set in both this repo and `ocp-client-protocol`.

| Secret | What it is |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token, not the account login |
| `MAVEN_CENTRAL_PASSWORD` | the matching token password |
| `MAVEN_SIGNING_KEY` | ASCII-armored private key, `gpg --armor --export-secret-keys` |
| `MAVEN_SIGNING_KEY_ID` | last 8 characters of the key id |
| `MAVEN_SIGNING_KEY_PASSWORD` | the key's passphrase |

CI passes the key to Gradle through the `ORG_GRADLE_PROJECT_signingInMemoryKey*` properties.
Locally that is a no-op unless the same properties are set, so `publishToMavenLocal` still works
unsigned.

## One-time setup, per key and per namespace

**The signing key's public half must be on a keyserver Central queries.** Producing valid `.asc`
files is not enough: Central fetches the public key by fingerprint to check them, and one it
cannot find fails the whole deployment with `Could not find a public key by the key fingerprint`
against every signed file.

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <fingerprint>
```

The `com.flipcash` namespace is verified in the Central Portal. That was the other one-time human
step, and the one that needs a DNS TXT record.
