# Development signing key

`inknote-dev.jks` provides a stable signature for GitHub Release test APKs so Android can
install later versions as updates. The key is intentionally public and must never be used for
Google Play or another production distribution channel. A production release must use a private
key stored outside this repository and will require a one-time reinstall when changing signatures.

Certificate SHA-256:

```text
59:BD:95:A3:7B:99:16:C7:DC:07:3C:AF:54:47:4E:5F:ED:5E:A1:12:27:0D:A2:2F:7D:F1:4C:33:69:2E:D1:2D
```
