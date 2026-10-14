# Vendored front-end libraries

Every script, stylesheet and font the web admin UI needs is shipped here and loaded with relative URLs, so the UI makes
no request to another host. Each file was taken from the package's npm tarball; the tarball's `sha512` integrity was
checked against the one the npm registry publishes for that version. Each library's license is next to its files.

| Library                | Version | Files here                                                                       | Source (npm tarball)                                                     | Tarball integrity                                                                                         |
|------------------------|---------|----------------------------------------------------------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| AngularJS              | 1.8.3   | `angular/angular.min.js`, `angular/angular-csp.css`                              | https://registry.npmjs.org/angular/-/angular-1.8.3.tgz                   | `sha512-5qjkWIQQVsHj4Sb5TcEs4WZWpFeVFHXwxEBHUhrny41D8UrBAd6T/6nPPAsLngJCReIOqi95W3mxdveveutpZw==`         |
| angular-route          | 1.8.3   | `angular/angular-route.min.js`                                                   | https://registry.npmjs.org/angular-route/-/angular-route-1.8.3.tgz       | `sha512-kpIcRmDR2+o1FxDVVYy8Rvfab86/7LDbOgTRb9T+X9ewPQiBRuDEnZtM3oJYBiQLvAXDYTJXHV48n/bGE9Mv2g==`         |
| angular-animate        | 1.8.3   | `angular/angular-animate.min.js`                                                 | https://registry.npmjs.org/angular-animate/-/angular-animate-1.8.3.tgz   | `sha512-/LtTKvy5sD6MZbV0v+nHgOIpnFF0mrUp+j5WIxVprVhcrJriYpuCZf4S7Owj1o76De/J0eRzANUozNJ6hVepnQ==`         |
| jQuery                 | 3.7.1   | `jquery/jquery.min.js`                                                           | https://registry.npmjs.org/jquery/-/jquery-3.7.1.tgz                     | `sha512-m4avr8yL8kmFN8psrbFFFmB/If14iN5o9nw/NgnnM+kybDJpRsAynV2BsfpTYrTRysYUdADVD7CkUUizgkpLfg==`         |
| Bootstrap              | 3.4.1   | `bootstrap/js/bootstrap.min.js`, `bootstrap/fonts/*`                             | https://registry.npmjs.org/bootstrap/-/bootstrap-3.4.1.tgz               | `sha512-yN5oZVmRCwe5aKwzRj6736nSmKDX7pLYwsXiCj/EYmo16hODaBiT4En5btW/jhBF/seV+XMx3aYwukYC3A49DA==`         |
| Bootswatch "spacelab"  | 3.4.1   | `bootstrap/css/bootstrap.min.css` (from `spacelab/bootstrap.min.css`)            | https://registry.npmjs.org/bootswatch/-/bootswatch-3.4.1.tgz             | `sha512-0hL4A8OUiqABgPipGrojf/hyhr5RS257xCNARlbK34HaMfhV5fXvwEooN4/ri9+jgX47J4Wg24ZPmfZ2xD2cKw==`         |
| Moment.js              | 2.31.0  | `moment/moment.min.js` (from `min/moment.min.js`)                                | https://registry.npmjs.org/moment/-/moment-2.31.0.tgz                    | `sha512-0acOTfMiWOheYS4eoWb80yYMb/JLvVv9SHbs2PehaDzfUG0Bw855SKyk0IKTnPGa5+U2bmi3W68l1+sGLX/pvw==`         |
| AngularJS-Toaster      | 3.0.0   | `angularjs-toaster/toaster.min.js`, `angularjs-toaster/toaster.min.css`          | https://registry.npmjs.org/angularjs-toaster/-/angularjs-toaster-3.0.0.tgz | `sha512-Upv5lkyrWWv9aIK4Jo7X9nCp1tPaWerSEeUrdjibm0GhwuW0/aoaUjn8Ou7IS8/QIGZspXrXHWGTa/CRBOj6Jg==`       |
| Bootstrap Toggle       | 2.2.2   | `bootstrap-toggle/bootstrap-toggle.min.js`, `bootstrap-toggle/bootstrap-toggle.min.css` | https://registry.npmjs.org/bootstrap-toggle/-/bootstrap-toggle-2.2.2.tgz | `sha512-AkYD/i3NGCEksIb/PmmUrrxpa0DYi7Si30e9Ldhou2t1HTd9npoXUsxJJjFPvlV7iLUptlysWTcYD7i3DKV6qg==` |
| JavaScript Cookie      | 3.0.8   | `js-cookie/js.cookie.min.js` (from `dist/js.cookie.min.js`)                      | https://registry.npmjs.org/js-cookie/-/js-cookie-3.0.8.tgz               | `sha512-yeJd4aNAdYZQjaon2bpD/Gb0B/omw7HQOsynXXcOiWVCacbBcPlgn8S/d1X6blFSaHao7ozqtW7NZW19xpCtIw==`         |

## Changes made to the published files

- `bootstrap/css/bootstrap.min.css`: the `@import` of the "Open Sans" font from `fonts.googleapis.com` is removed, so
  the theme falls back to the next fonts in its stack (Helvetica Neue, Helvetica, Arial).
- `angular/angular.min.js`, `angular/angular-route.min.js`, `angular/angular-animate.min.js`, `moment/moment.min.js`,
  `bootstrap-toggle/bootstrap-toggle.min.js`: the trailing `//# sourceMappingURL=` comment is removed, since the source
  maps are not shipped.

All other files are byte-for-byte as published.

## Refreshing a library

1. Read the version's metadata from `https://registry.npmjs.org/<package>/<version>`: `dist.tarball` and
   `dist.integrity`.
2. Download the tarball and check it: `echo "sha512-$(openssl dgst -sha512 -binary <file>.tgz | base64)"` must equal
   `dist.integrity`.
3. Copy the files listed above from the tarball's `package/` directory, redo the changes listed above, and update this
   table.
4. Check every page of the UI in a browser, with the console open: no errors and no request to another host.
