# Vendored front-end libraries

Every script and icon the web admin UI needs is shipped here and loaded with relative URLs, so the UI makes no request
to another host and needs no build step. Each file was taken from the package's npm tarball; the tarball's `sha512`
integrity was checked against the one the npm registry publishes for that version. Each library's license is next to
its files.

| Library      | Version | Files here                                                  | Source (npm tarball)                                                | Tarball integrity                                                                                 |
|--------------|---------|-------------------------------------------------------------|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Preact       | 11.2.0  | `preact/preact.js`, `preact/hooks.js`                       | https://registry.npmjs.org/preact/-/preact-11.2.0.tgz               | `sha512-/HfHI/7N84e6GrWBJ3J280z3mLaRJiDcSvj/A+y/4AbLnND/1UlGHe1sR6oYuTGwId0nP7dqDU9OnLxMPZ7xBg==` |
| htm          | 3.1.1   | `htm/htm.js` (from `dist/htm.module.js`)                    | https://registry.npmjs.org/htm/-/htm-3.1.1.tgz                      | `sha512-983Vyg8NwUE7JkZ6NmOqpCZ+sh1bKv2iYTlUkzlWmA5JD2acKoxd4KVxbMmxX/85mtfdnDmTFoNKcg5DGAvxNQ==` |
| Lucide icons | 1.82.0  | `lucide/icons.svg` (built from `icons/*.svg`)               | https://registry.npmjs.org/lucide-static/-/lucide-static-1.82.0.tgz | `sha512-Xzzpfkx06hHXjXoLWOk1RWg52OhfaRcIOIK72hCbRty8Jq29WQUV4J4Z9GvjAKDoPxGf7tlcqBbJX4qrr1U8+w==` |
| Chart.js     | 4.6.2   | `chartjs/chart.umd.min.js` (from `dist/chart.umd.min.js`)   | https://registry.npmjs.org/chart.js/-/chart.js-4.6.2.tgz            | `sha512-xachB+pPHjp0syJi7llUBA1w3kMYl6NF3o7eVrvjnWCT+IB2zMaF2pX5UYDiwbbfsioFEIRnAjreNOMsGj5TIA==` |
| cronstrue    | 3.51.0  | `cronstrue/cronstrue.min.js` (from `dist/cronstrue.min.js`) | https://registry.npmjs.org/cronstrue/-/cronstrue-3.51.0.tgz         | `sha512-yWLe6YxfbDx/5OSzpocD6SyZD1nVo0aO22hUizh7eQX5xO1iDu7qY6w8+2gevRmIu1pElKbIiXrGbeoLEbKmmw==` |

## Changes made to the published files

- `preact/preact.js` is `dist/preact.mjs` and `preact/hooks.js` is `hooks/dist/hooks.mjs`, renamed to `.js` because
  the web admin filter serves only the file types the UI ships. In `hooks.js` the import of `"preact"` is changed to
  `"./preact.js"`, since a browser cannot resolve a bare package name without an import map (which the
  Content-Security-Policy would block as an inline script).
- `preact/preact.js`, `preact/hooks.js`: the trailing `//# sourceMappingURL=` comment is removed, since the source maps
  are not shipped.
- `lucide/icons.svg` is a sprite of only the icons the UI uses: each `<symbol id="name">` holds the shapes of
  `icons/name.svg`, unchanged. The icons are `layout-dashboard info sliders-horizontal clock package scroll-text server
  activity sun moon monitor refresh-cw pause play trash-2 zap search x check chevron-left chevron-right chevron-up
  chevron-down chevrons-up-down menu triangle-alert circle-alert circle-check circle-x copy wrap-text calendar-clock
  file-text external-link history timer arrow-left`. The stroke attributes the icon files carry on their `<svg>` are set
  by `style.css` (`.icon`) instead.
- `chartjs/chart.umd.min.js`: the trailing `//# sourceMappingURL=` comment is removed. The file bundles Chart.js's one
  dependency, `@kurkle/color` 0.6.6 (MIT), with its license banner. It is loaded as a plain script and sets
  `window.Chart`; the UI imports nothing from it.

`htm/htm.js` and `cronstrue/cronstrue.min.js` are byte-for-byte as published. cronstrue is loaded as a plain script and
sets `window.cronstrue`; its English descriptions are built in, the other languages are not shipped.

## Refreshing a library

1. Read the version's metadata from `https://registry.npmjs.org/<package>/<version>`: `dist.tarball` and
   `dist.integrity`.
2. Download the tarball and check it: `echo "sha512-$(openssl dgst -sha512 -binary <file>.tgz | base64)"` must equal
   `dist.integrity`.
3. Copy the files listed above from the tarball's `package/` directory, redo the changes listed above, and update this
   table. To add an icon to the sprite, copy the inner shapes of `icons/<name>.svg` into a new
   `<symbol id="<name>" viewBox="0 0 24 24">` and add its name to the list above.
4. Check every page of the UI in a browser, in the light and the dark theme, with the console open: no errors, no
   Content-Security-Policy violations and no request to another host.
