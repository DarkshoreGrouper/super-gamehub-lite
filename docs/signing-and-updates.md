# Permanent signing and APK updates

The app package remains `super.gamehub.lite`. Distributable APKs use the one
permanent signing key whose public certificate is in
`signing/super-gamehub-lite.pem` and whose SHA-256 digest is pinned in
`signing/certificate-sha256.txt`.

## One-time migration

Earlier builds generated an ephemeral key. Android cannot update those builds
with the permanent key. Back up any Super app settings you need, uninstall that
old Super build once, then install the first permanently signed APK. Normal
GameHub Lite is a separate package. Later Super builds install as updates and
preserve Android app data, provided the original key and package are retained.

## GitHub configuration

The following repository Actions secrets are required:

- `SUPER_SIGNING_KEYSTORE_B64`: Base64 of the original PKCS12 keystore.
- `SUPER_SIGNING_PASSWORD`: Its original password.

Alias: `supergamehub` (not secret). The private key and password must never be
committed, placed in public artifacts, printed in logs, or regenerated as part
of a build. A private recovery backup was prepared separately. Keep it private.
Losing or replacing both the secret and backup would break update continuity.

The workflow restores the key only for trusted master builds. Pull requests
never receive the signing secrets. It checks the pinned certificate before
building and checks the actual APK certificate, package and version afterward.
Missing or mismatched signing material stops the build; no fallback key exists.
The restored runner copy is removed after signing even when a step fails.

Android versionCode is `100000 + GITHUB_RUN_NUMBER`; versionName is
`0.1.GITHUB_RUN_NUMBER`. New workflow runs increase the code; reruns of the same
run keep it. Keep this workflow file and numbering scheme when maintaining CI.
Installing an older APK over a newer one is a downgrade, not an ordinary update.

## Local builds

Restore the original private backup to the ignored `signing-private/` folder.
Set KEYSTORE, KEYSTORE_PASS and KEY_ALIAS, plus explicit SUPER_VERSION_CODE and
SUPER_VERSION_NAME. Choose a code higher than the installed build. `patch.sh`
verifies the same pinned certificate as CI. Python 3 and keytool are required.

## Recovery using GitHub CLI

The private backup contains a helper that sets the two secrets using `gh`,
reading values from local files through stdin. This avoids pasting private keys
into chat, public files or shell command arguments. Authenticate `gh` as the
repository owner first. Restore the same backup, not a newly generated key.
