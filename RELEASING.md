# Releasing to Maven Central

All of this is free. The one-time setup takes about 20 minutes.

## One-time setup

1. **Central account.** Sign in at https://central.sonatype.com with your GitHub account. The namespace `io.github.rrezartprebreza` is then verified for you.
2. **User token.** In the Central Portal, open your account and choose *Generate User Token*. Keep the username and password it shows.
3. **GPG key.** On your Mac:
   ```
   brew install gnupg
   gpg --quick-gen-key "Rrezart Prebreza <rrezart.prebreza@gmail.com>" rsa4096 sign 2y
   gpg --list-secret-keys --keyid-format long          # copy the key id after rsa4096/
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID> > private.asc
   ```
4. **Repository secrets.** On GitHub: *Settings → Secrets and variables → Actions → New repository secret*:
   - `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`: the user token from step 2
   - `GPG_PRIVATE_KEY`: the whole content of `private.asc`
   - `GPG_PASSPHRASE`: the passphrase you chose in step 3

   Then delete `private.asc` from your Mac.

## Each release

```
git tag v0.1.0
git push origin v0.1.0
```

The `release` workflow sets the version from the tag, builds, signs, uploads to Central and creates a GitHub release. The example module is not published. New versions usually show up on Maven Central within about 30 minutes.
