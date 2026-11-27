# Completed proposals (DONE)

| #  | Proposal                                                                    |
|----|-----------------------------------------------------------------------------|
| 1  | Web admin: send the host's CSRF token with every change it makes            |
| 2  | Web admin: no HTML from data                                                |
| 3  | Web admin: front-end libraries bundled in the jar and brought up to date    |
| 4  | Web admin: the page filter serves only its own files, with security headers |
| 5  | API: no class is loaded by a name the caller sent, unless it is a job       |
| 6  | README: how to secure the UI and the API; release 1.0.3                     |
| 7  | Web admin: auto-refresh choice remembered, and a dark theme                 |
| 8  | Improve the UI / UX                                                         |

# 4. (DONE) Web admin: the page filter serves only its own files, with security headers

**Why:** `jobsAdminFilter` takes whatever follows `web-admin.uri` in the request path and loads it from the class path
under `org/quartzplus/webadmin`. Nothing in the filter stops `..`: a host is protected today only because Spring
Security's default `StrictHttpFirewall` refuses such paths before the filter sees them; a host without it, or with a
looser firewall, could be asked for `/scheduler/admin/../../../application.properties`. A missing file is answered
with an empty `200` (the failure is only logged), a file with an unknown extension gets no `Content-Type`, and the path
is cut with `String.replace`, which removes the prefix wherever it occurs, not only at the start. The pages also send
no headers against framing or content sniffing, so another site can frame the UI and trick a signed-in administrator
into clicking.

**Change:**
- The filter serves only files that exist under `webadmin/` in the jar: the path after the prefix is normalised, and
  anything that is not a plain relative path to such a file (`..`, `\`, an encoded `/` or `.`, a leading `/` after
  normalising) is answered `404`. A missing file is `404` too.
- The prefix is removed with `substring`, only at the start.
- A `Content-Type` for every file type the UI ships (after #3, also the font types), and `404` for any other.
- On every response of the UI: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and
  `Content-Security-Policy: frame-ancestors 'none'`; once #3 has removed the outside hosts, a restrictive policy for
  the rest (`default-src 'self'`, with what AngularJS 1.8 needs for styles). `Cache-Control: no-store` on `index.html`
  (it carries a CSRF token after #1).
- The setting `quartz-plus.web-admin.security-headers` (default `true`) for a host that sets these headers itself.

**Where:** `QuartzPlusWebAdminConfiguration`, `QuartzPlusProperties`, README's settings table

**Done when:** Tests in `WebAdminConfigurationTest`, with the filter reached directly (a `MockHttpServletRequest`, not
through a firewall): `../`, `..%2f`, `%2e%2e/` and a backslash after the prefix are answered `404` and load nothing; a
missing file is `404`; `index.html` and `app.js` are served with their content types and the headers; with the setting
off, the headers are absent.

**Watch out:** A host that already sets a Content-Security-Policy for its whole site gets two; the setting lets it turn
the library's off.

-------------

# 1. (DONE) Web admin: send the host's CSRF token with every change it makes

**Why:** A host that authenticates the UI with a session or cookie must protect its changing requests against CSRF;
Spring Security does by default. The UI makes its changes with jQuery (`controllers.js`: `$.post` to pause and resume,
`$.ajax` `POST` to run a job now, `$.ajax` `DELETE` to remove a trigger) and sends no token, so every one of them is
refused with 403 behind such a host. The read-only `$.get` calls are not affected.

Sending the `XSRF-TOKEN` cookie's value as an `X-XSRF-TOKEN` header, as AngularJS does, is not enough: since Spring
Security 6 the default `XorCsrfTokenRequestAttributeHandler` expects the masked token that the server hands out per
request, and refuses the raw cookie value. The token has to come from the server with the page.

**Change:**
- When `index.html` is served, `QuartzPlusWebAdminConfiguration`'s filter adds Spring Security's usual two meta tags
  if the request carries a CSRF token (request attribute `_csrf`, of type
  `org.springframework.security.web.csrf.CsrfToken`): `<meta name="_csrf" content="{token}">` and
  `<meta name="_csrf_header" content="{headerName}">`, the token from `getToken()` (already masked by the host's
  handler) and the header name from `getHeaderName()`. With no token on the request (no Spring Security, or CSRF
  switched off) nothing is added and the page works as today.
- `app.js` reads the two meta tags once and, through `$.ajaxSetup({beforeSend: ...})`, sets that header on every
  request whose method is not `GET`, `HEAD` or `OPTIONS`. If AngularJS's `$http` is ever used for a change, the same
  header is set there through `$httpProvider` (and its own `XSRF-TOKEN` cookie handling is switched off, so the raw
  cookie value is never sent alongside).
- Spring Security as an optional dependency (`<optional>true</optional>`), and the token read only when its class is
  on the classpath, so a host without it needs nothing new.
- `index.html` is rendered per request from now on: the filter caches the file's bytes, never the page with a token in
  it, since a token belongs to one visitor.

**Where:** `QuartzPlusWebAdminConfiguration.jobsAdminFilter`, `webadmin/index.html` (a marker in `<head>` the filter
replaces), `webadmin/app.js`, `pom.xml`

**Done when:** Tests in `WebAdminConfigurationTest`, with Spring Security in the test application and its default CSRF
setup: `index.html` carries both meta tags, with a token that differs between two requests; a `POST` to pause a job
with the header and token from the page is accepted, without them refused 403; a second test application without
Spring Security serves `index.html` without the tags. By hand in a browser, behind chat-core's portal: pause, resume,
run now and remove trigger all work.

**Watch out:** The filter must run after the host's security filter chain, so the token is on the request: it does
today (Spring Security's filter is registered at order -100, this filter at the default, later). The token must not be
cached with the page, or one visitor's token would be served to the next.

-------------

# 2. (DONE) Web admin: no HTML from data

**Why:** The `shorten` filter in `app.js` builds HTML from its input and marks it trusted:
`$sce.trustAsHtml("<span title='" + input + "'>" + input.substring(0, size).trim() + "...</span>")`, and returns
short input through `$sce.trustAsHtml(input)` as it is. Two pages render it as HTML with `ng-bind-html`:
`inc/log-details.html` for `log.errorMessage` and `inc/nodes.html` for `node.instanceName`. A job's error message is
the text of an exception, and an exception thrown while handling outside data (a webhook body, a file name, a user's
input) can carry `<img src=x onerror=...>`; it would then run as script in the administrator's session, which can
pause, run and remove jobs. The title attribute is built by string concatenation too, so a single quote in the text
breaks out of it.

`controllers.js` also writes names with jQuery's `.html()` (`$('#executeJobModal .groupName').html(group)`, and the job
and trigger names) and reads them back with `.html()` to send them. Job names come from code, so this is not a hole
today, but it is the same pattern.

**Change:**
- `shorten` returns plain text: the first `size` characters and `...` when longer. The pages show it with `{{ }}` or
  `ng-bind`, which escape, and the full text as a tooltip with `ng-attr-title="{{ value }}"`. Nothing in the UI is
  marked trusted with `$sce` any more, and `ng-bind-html` is gone from every page.
- The job modal uses `.text()` to write and read the names.

**Where:** `webadmin/app.js`, `webadmin/controllers.js`, `webadmin/inc/log-details.html`, `webadmin/inc/nodes.html`,
`webadmin/inc/metrics.html` (uses `shorten` with `{{ }}` already, unchanged in behaviour)

**Done when:** A test job that throws an exception whose message is `<img src=x onerror="window.__xss=1">` leaves a
log entry; the logs page shows that text literally and `window.__xss` stays undefined (by hand in a browser, or with a
browser test if one is added). No `trustAsHtml`, `ng-bind-html` or `.html(` with data is left in `webadmin/`.

**Watch out:** Long values lose nothing: the full text is in the tooltip.

-------------

# 5. (DONE) API: no class is loaded by a name the caller sent, unless it is a job

**Why:** Two endpoints call `Class.forName` with a name taken from the request: `GET {api-uri}/jobs?instanceClass=...`
(`SchedulerRestController.getGroupJobs`) and `PUT {api-uri}/create` (`createJob`, its `instanceClass` and `calendar`
parameters). `Class.forName(name)` loads and initialises the class, running its static initialisers, before anything
checks that it is a `Job` or a `Calendar`; `QuartzExecutorServiceImpl` checks `Job.class.isAssignableFrom` only
afterwards. Any class on the host's class path can be initialised this way, and `GET /jobs` is a plain link, which a
browser follows with the administrator's cookies (CSRF protection does not cover `GET`). `getGroupJobs` also throws a
`NullPointerException` for a class without `@JobSpec`.

**Change:**
- `getGroupJobs`: an `instanceClass` is matched against the job classes the scheduler already has (their names from
  `scheduler.getJobKeys` and each `JobDetail.getJobClass()`), never loaded; an unknown name is answered `400` without
  loading anything.
- `createJob`: the class is loaded with `Class.forName(name, false, classLoader)`, which does not initialise it, then
  refused with `400` unless `Job.class.isAssignableFrom` holds; the calendar class the same way against
  `org.quartz.Calendar`. Only then is it initialised and instantiated.
- `createJob` behind a setting, `quartz-plus.api.create-enabled` (default `true`, so nothing changes for current
  users), for hosts that never create jobs at run time and would rather not expose it.

**Where:** `SchedulerRestController`, `QuartzPlusProperties`, README's settings table and API section

**Done when:** Tests in `SchedulerRestControllerTest`: `GET /jobs?instanceClass=` with a class whose static initialiser
sets a flag leaves the flag unset and is answered `400`; the same for `PUT /create` with a non-job class and with a
non-calendar `calendar`; a real job class still creates; with `quartz-plus.api.create-enabled=false`, `PUT /create` is
answered `404`.

**Watch out:** A host that already creates jobs through `PUT /create` with a job class that is not yet scheduled keeps
working: only the order of loading and checking changes.

-------------

# 3. (DONE) Web admin: front-end libraries bundled in the jar and brought up to date

**Why:** `index.html` loads eleven scripts and stylesheets from third-party hosts (`code.angularjs.org`,
`code.jquery.com`, `maxcdn.bootstrapcdn.com`, `oss.maxcdn.com`, `cdnjs.cloudflare.com`, `gitcdn.github.io`), over
protocol-relative URLs and without Subresource Integrity, so whoever controls one of those hosts or the file it serves
runs code in the administrator's session. They all still answer today, but none is under the library's control. The
versions are old and carry known vulnerabilities: AngularJS 1.2.6 (end of life, sandbox escapes; `angular-animate` is
a different version, 1.2.0), jQuery 1.12.0 (CVE-2015-9251, CVE-2019-11358, CVE-2020-11022, CVE-2020-11023), Bootstrap
3.3.6 (CVE-2018-14040, CVE-2018-14041, CVE-2018-14042, CVE-2019-8331). `html5shiv` and `respond.js` exist only for
Internet Explorer 8. A host also cannot set a strict Content-Security-Policy while the page needs outside origins.

**Change:**
- Every script and stylesheet the page needs is shipped in the jar under `webadmin/vendor/` and loaded from there with
  relative URLs. No request leaves the host.
- Upgraded within their lines, so the pages need little change: AngularJS and `angular-route`/`angular-animate` to the
  final 1.8.x (all three the same version), jQuery to the latest 3.x, Bootstrap and the Bootswatch "spacelab" theme to
  3.4.1, `moment` to its latest 2.x, `angularjs-toaster` and `bootstrap-toggle` to the latest releases that support
  those versions, and `jquery-cookie` (no longer maintained) replaced by `js-cookie`. `html5shiv` and `respond.js`
  dropped.
- Each vendored file's version and source URL listed in `webadmin/vendor/README.md`, so they can be refreshed later.
- The page checked against each upgrade: AngularJS 1.3+ removed global controller functions and tightened `$sce`;
  jQuery 3 dropped `.success()`/`.error()` on its promises in favour of `.done()`/`.fail()`; the toaster's markup
  changed between major versions.

**Where:** `webadmin/index.html`, `webadmin/app.js`, `webadmin/controllers.js`, `webadmin/inc/*.html`, new
`webadmin/vendor/`, and the filter's content types (it must serve `.css`, `.js` and the font files Bootstrap's icons
need, `.woff`, `.woff2`, `.ttf`, `.svg`, `.eot`, with the right `Content-Type`; see #4)

**Done when:** `index.html` references no URL outside the page's own directory; every page of the UI works in a
current browser (scheduler, jobs, triggers, logs, log details, nodes, metrics, configuration, timeline; pause, resume,
run now, remove trigger); a browser's network panel shows no request to another host; the browser console shows no
error.

**Watch out:** AngularJS itself is out of support since January 2022, even at 1.8.3; this brings the UI to the last
fixed version and removes the outside hosts, it does not make AngularJS supported. Replacing it (plain JavaScript or a
current framework) is a larger change for a later proposal. The jar grows by roughly 1 MB.

-------------

# 6. (DONE) README: how to secure the UI and the API; release 1.0.3

**Why:** The library ships both the UI and an API that pauses, runs, removes and creates jobs, with no authentication
of its own, and the README does not say that the host must protect them. After #1 to #5 it should also say what the UI
expects from the host.

**Change:**
- A README section "Securing the web admin and the API":
  - Both paths must be protected by the host; an example Spring Security rule that requires an administrator role on
    `quartz-plus.web-admin.uri` and `quartz-plus.api-uri` and everything below them.
  - Put both under one prefix of the host's own administration area (for example `/admin/scheduler` and
    `/admin/scheduler/api`), so one rule covers them.
  - CSRF: the UI sends the token Spring Security gives the page (#1); a host with CSRF on needs nothing else. A host
    that authenticates with a header only (no cookie) can leave CSRF off for these paths.
  - The headers the UI sets and the setting to turn them off (#4); the `create` endpoint and its switch (#5).
- `CHANGES.md` for the release (new, as `CLAUDE.md` asks), version 1.0.3 in `pom.xml`, released to Maven Central as
  `RELEASE.md` describes.

**Where:** `README.md`, `CHANGES.md` (new), `pom.xml`

**Done when:** The README section exists and its example rule is the one the tests of #1 use; 1.0.3 is on Maven
Central; chat-core builds against it.

**Watch out:** 1.0.3 changes the UI's look slightly (Bootstrap 3.4.1, a newer theme build) and its behaviour behind a
CSRF-protected host (it now works there); nothing in the Java API changes except the new settings, which default to
today's behaviour.

-------------

# 7. (DONE) Web admin: auto-refresh choice remembered, and a dark theme

**Why:** The navbar's "refresh every 10 sec" toggle does not reliably keep the administrator's choice. Today
(`app.js`), switching it off sets a `__autoRefreshToggle` cookie and the page reads it on load, so "off" survives a
reload of the same page, but:
- the cookie has no expiry, so it is a session cookie: the choice is lost when the browser closes (the same in 1.0.2,
  with `jquery-cookie`);
- only "off" is stored; "on" is the absence of the cookie;
- switching it back on does nothing until the administrator moves to another page: the change handler only updates the
  cookie, and the refresh loop (`reloadController` in `controllers.js`) is only started when a page's controller loads;
- the cookie is sent to the server with every request, for a value only the browser needs.

The UI also has only one, light, look (Bootswatch "spacelab"). Administrators working in a dark environment, or whose
system is set to dark mode, get a bright page; there is no way to choose a dark one.

**Change:**
- Both preferences are kept in the browser's `localStorage` (persistent, never sent to the server) under
  `quartz-plus.auto-refresh` (`on` / `off`) and `quartz-plus.theme` (`light` / `dark`). Reading and writing are wrapped
  in `try`/`catch`, so a browser that blocks storage (private mode, blocked site data) simply falls back to the
  defaults. An existing `__autoRefreshToggle` cookie is read once as "off", saved to `localStorage`, and removed.
- Auto-refresh: the toggle is set from the stored value on load (default "on"); switching it on starts refreshing the
  current page right away, without a page change; switching it off stops it, as today.
- Dark theme:
  - The Bootswatch "darkly" theme, 3.4.1, from the same `bootswatch@3.4.1` npm tarball as "spacelab" (its integrity is
    already in `webadmin/vendor/README.md`), vendored as `vendor/bootstrap/css/bootstrap-darkly.min.css`, next to
    "spacelab", so its `../fonts/` references reach the same Glyphicons fonts. Its Google Fonts `@import` is removed, as
    for "spacelab", and the change is listed in the vendor README.
  - `index.html` links both themes; the dark one starts `disabled`. A small `theme.js`, loaded in `<head>` right after
    the stylesheets (an external file, so the Content-Security-Policy needs no change), reads the stored theme and
    enables one stylesheet and disables the other before the page is painted, and sets `data-theme="dark"` or `"light"`
    on `<html>`. This avoids a flash of the light theme when a dark-mode page is reloaded.
  - With no stored choice, the theme follows the system setting (`prefers-color-scheme: dark`); once the administrator
    picks one, that choice wins.
  - `style.css` has hard-coded light colours that "darkly" does not cover: the sidebar (`#f5f5f5`, border `#eee`), the
    page headings (`#395c82` with a light text shadow), `.text-shadow`, `.menu_item A` (`#888`), `code` (`#555`, border
    `#bbb`), `.page-link-selected` (`#bbb`), `.page-size-dropdown-button` and the navbar (`#226`). Each gets a dark value
    under `html[data-theme="dark"]`. The toaster, the Bootstrap toggle, the job modal and tooltips are checked against
    the dark theme and given overrides where they stay light.
- A theme button in the navbar, left of the refresh toggle: a small Bootstrap button with the `glyphicon-adjust` icon
  and the name of the theme it switches to ("dark" / "light"), with a `title` tooltip. A click switches the theme at
  once, without a reload, and stores the choice.
- No new server setting: both preferences live in the browser only.

**Where:** `webadmin/index.html`, `webadmin/app.js`, `webadmin/controllers.js` (`reloadController`), `webadmin/style.css`,
new `webadmin/theme.js`, new `webadmin/vendor/bootstrap/css/bootstrap-darkly.min.css`, `webadmin/vendor/README.md`,
README's "Web Administration UI" section

**Done when:**
- `WebAdminConfigurationTest`'s check that every file `index.html` references is served covers `theme.js` and the dark
  stylesheet without change; nothing new is loaded from another host.
- By hand in a browser:
  - auto-refresh switched off stays off after a reload, a new tab and a browser restart; switched on, the current page
    refreshes within 10 seconds without navigating; stays on after a restart;
  - the theme button switches between light and dark at once; the choice survives a reload, a new tab and a browser
    restart; a dark-mode page reloads without a flash of the light theme;
  - with no stored choice, a browser in dark mode opens the UI dark, and one in light mode opens it light;
  - every page is readable in the dark theme (scheduler, configuration, triggers, trigger details, job classes, logs,
    log details, cluster nodes, metrics), as are toasts, the execute-job modal, the toggle and tooltips;
  - with storage blocked, the UI works with the defaults (refresh on, theme from the system setting) and the console
    shows no error;
  - the browser console shows no error and no Content-Security-Policy violation in either theme.

**Watch out:**
- `localStorage` belongs to the origin, not the path: two quartz-plus UIs on the same host (or other pages of the host
  using the same keys) share the preferences. The `quartz-plus.` key prefix keeps them apart from the host's own keys.
- The jar grows by the size of the dark stylesheet (about 120 KB uncompressed).
- The timeline page (`inc/timeline.html`, its CSS at the end of `style.css`) has no route and is not reachable; it is
  not themed.
- To ship after 1.0.3, in the next version.

-------------

# 8. (DONE) Improve the UI / UX
We need to rewrite the UI / UX to make it more modern, also it would be great to add charts / graphs / diagrams where appropriate.
This is the time to re-think the UI/UX.
Make sure to allow theme modes: device (default), light, dark
Use easy to understand icons when helpful. 
Provide multiple style examples for us to choose from.

**Decisions (2026-11-27, from `PROPOSAL_8_DECISIONS.html`):**
- Stack: Preact + htm as plain ES modules, no build step; AngularJS, jQuery, Bootstrap 3, Bootswatch, Moment, Toaster,
  Bootstrap Toggle and js-cookie are removed. Jar-served, Maven-only, current CSP and CSRF meta tags stay.
- Style: "Slate Console" (left sidebar with icons and labels, neutral greys, one indigo accent), on CSS variables.
- Navigation: a new Dashboard as the landing page; the seven existing pages stay as they are (8 entries).
- Dashboard and charts: KPI tiles, executions over time (1 h / 24 h / 7 d), trigger state breakdown, duration trend per
  job, recent failures, upcoming fires, execution timeline.
- Chart data: new read-only `GET {api-uri}/stats`, aggregated from the execution log (`default` method on
  `JobExecutionLogService`, JDBC and in-memory implementations, no schema change).
- Chart library: Chart.js 4, vendored; small pieces (segmented bar) as inline SVG.
- Icons: Lucide subset as one same-origin SVG sprite, always paired with a label or tooltip.
- Theme: device (default) / light / dark as a three-segment control in the header; existing `quartz-plus.theme` key.
- UX in scope: triggers search + state chips + sortable columns; row actions on the triggers list with a styled confirm
  dialog; refresh control (interval, refresh now, pause while hidden); executions filtered by result and time range on
  the server; cron in words plus next five fire times; responsive layout; better execution detail page.
- Rollout: replaces the current UI in the next release, same URL and settings.
- Version: `1.0.5`.
- Testing: Java integration tests for served files, headers and the API; manual browser walk-through; no new tooling.

**Plan (two checkpoints):**
1. New shell, design tokens, theme modes, icons and every existing page at parity; old templates, scripts and vendored
   libraries deleted; filter tests updated. Review running in a browser. *(Done 2026-11-27; the refresh control from
   the UX list was built here too.)*
2. `/stats` endpoint, log filters, fire-times endpoint, Dashboard, charts, timeline and the UX items above; README and
   `CHANGES.md`. Review, then full build. *(Done 2026-11-27.)*

**Where:** `webadmin/` rewritten (`index.html`, `theme.js`, `style.css`, `js/**`, `vendor/{preact,htm,lucide,chartjs,
cronstrue}`, `vendor/README.md`); `JobExecutionLogService` (default methods `getJobExecutionLogList(..., success, from,
to)` and `getJobExecutionStats`), both implementations, new `ExecutionStatsAccumulator` and `JobExecutionStats`;
`SchedulerRestController` (`/log` filters, `/log/timeline`, `/stats`, `/trigger/fire-times`, `501` for unsupported
operations); `pom.xml` (version, no filtering of `webadmin/`); README's UI and API sections; `CHANGES.md`.

**Done:**
- Tests: `WebAdminConfigurationTest` follows every module import from `js/main.js` and checks each file is served;
  `SchedulerRestControllerTest` covers the log filters, `/stats` (buckets, filters, refused ranges), `/log/timeline`
  and `/trigger/fire-times` on H2; `InMemoryJobExecutionLogServiceImplTest` covers newest-first paging, the filters and
  statistics; `ExecutionStatsAccumulatorTest` and `JobExecutionLogServiceDefaultsTest` cover the bucket arithmetic and
  the default methods.
- Walked every page in a browser against a demo application (light, dark, device mode following the system, narrow
  screen), with CSRF protection on: no console errors and no Content-Security-Policy violation.

**Watch out:**
- A host's own `JobExecutionLogService` answers `501` for the new filters and statistics until it overrides the two
  default methods; the Dashboard then shows the error in its chart and tiles.
- Runs started with "run now" are logged with the group of Quartz's temporary trigger (`DEFAULT`), so they appear as
  their own row in the timeline and are not counted in `/stats` filtered by the job's group. Unchanged behaviour of the
  execution log, not of the UI.
- The trigger page's duration chart covers the trigger's job; there is no separate job page.
