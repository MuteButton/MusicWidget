# OnCue 2.0 naming and signing reset

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

This signing reset is used by the replacement 2.0 build (version code 17).
Consult the GitHub release verification attachment for results tied to the exact
APK hash. Published 1.0 and historical Git commits retain their original identity.
