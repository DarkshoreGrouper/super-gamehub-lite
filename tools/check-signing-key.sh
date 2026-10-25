#!/usr/bin/env bash
# Only the public certificate fingerprint is emitted. Missing/wrong keys fail closed.
set -euo pipefail
sign_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${KEYSTORE:?Set KEYSTORE to the permanent PKCS12 backup}"
: "${KEYSTORE_PASS:?Set KEYSTORE_PASS to its password}"
: "${KEY_ALIAS:=supergamehub}"
if [[ ! -f "$KEYSTORE" ]]; then
    echo 'Permanent keystore is missing. Restore the original backup; do not generate a new key.' >&2
    exit 1
fi
cert_temp="$(mktemp)"
trap 'rm -f "$cert_temp"' EXIT
export KEYSTORE_PASS
keytool -exportcert -keystore "$KEYSTORE" -storepass:env KEYSTORE_PASS \
    -alias "$KEY_ALIAS" -file "$cert_temp" >/dev/null 2>&1
actual_cert="$(sha256sum "$cert_temp" | awk '{print $1}')"
expected_cert="$(tr -d '\r\n' < "$sign_root/signing/certificate-sha256.txt")"
if [[ "$actual_cert" != "$expected_cert" ]]; then
    echo 'Signing certificate mismatch. Refusing to produce an incompatible update.' >&2
    exit 1
fi
echo "Permanent signing certificate verified: $actual_cert"
