# Changelog

Contract changes from a consumer's point of view: what appeared, what changed shape, and what breaks
if you upgrade. Field and enum renumbering matters more than its diff size suggests, so it gets
called out explicitly even when nothing else did.

`publish.yml` reads the section matching the version it is publishing and uses it as the GitHub
release notes, so a version with no entry here does not release. Write the entry in the same PR that
syncs the contract, while the diff is still in front of you.

## 0.1.0

First release. The Flipcash contract is now generated once here and published for both platforms,
replacing the copies each app vendored and generated for itself.

- Kotlin, on Maven Central as `com.flipcash:flipcash2-client-protocol`, under
  `com.codeinc.flipcash.gen.*`.
- Swift, as the `Flipcash2ClientProtocol` module. The git tag is the SPM release.
