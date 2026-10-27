# OnCue 2.0.1 naming and signing reset

All active package, resource, preference, signing-tool and documentation names
now use OnCue. Obsolete reports were removed rather than relabeled as new results.
Historical Git history, releases and private recovery archives are unchanged.

The registered application ID remains `io.github.mutebutton.oncue`. The new
certificate subject is `CN=MuteButton, OU=OnCue, O=MuteButton`.
Public certificate SHA-256:

```
3ffc50b37250307dde9bbcb787f278a6a6325f03ae1950a5fc9f8e2dfb65e938
```

Add this fingerprint to the existing package registration in Android Developer
Console before distributing this identity. It is not the APK file hash.
Uninstall the previously signed app before installing this APK; then enable
notification access and re-add widgets. Local preferences start fresh.

The old signing material is preserved privately. New recovery material is in
Documents/OnCue signing backup with restricted access. Copy it to an encrypted
off-device vault; the local recovery password file is plaintext.

The newly signed APK is a local candidate, not a replacement for published 2.0.
Build, unit tests, release lint and Android smoke tests are required; these are
not a claim of full media integration regression on this new signing identity.
