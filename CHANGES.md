# Changes

### Version 1.0.5 (2026-11-27)
- README shows a screenshot of the web admin Dashboard (`docs/images/`), light or dark to match the reader's GitHub theme
- Web admin UI rewritten: Preact 11 and htm as plain ES modules, no build step; AngularJS, jQuery, Bootstrap 3, Bootswatch, Moment.js, AngularJS-Toaster, Bootstrap Toggle and js-cookie are removed (about 940 KB of vendored files replaced by about 35 KB); same URL, same settings, same page addresses (`#/logs?...`, `#/trigger/<group>/<trigger>`), so links into the UI keep working
- Web admin has a new look: left navigation with icons (Lucide, shipped as one same-origin SVG sprite), cards and tables on one set of colour variables for the light and the dark theme
- Web admin theme switch with three modes in the top bar: device (the default; follows the operating system, also when it changes while the page is open), light and dark; a choice made in 1.0.4 is kept
- Web admin refresh control: interval (off, 5, 10, 30 or 60 seconds; a 1.0.4 "on" means 10 s), "refresh now", and how long ago the page was updated; no polling while the browser tab is hidden
- Web admin works on narrow screens: below 900 px the navigation opens from a menu button, tables scroll sideways
- Web admin times use a 24-hour clock (they were shown with a 12-hour clock and no AM/PM)
- Web admin trigger details: run-now dialog checks that the job data is a JSON object before sending it; removing a trigger asks in a dialog instead of the browser's `confirm()`; pause, resume and remove act on the trigger's own group (they used the job's group)
- Web admin passes names to the API URL-encoded, so groups, jobs and triggers with `&`, `#` or spaces work from the UI
- Web admin shows "Cluster nodes" in the navigation only for a clustered scheduler, from the first page opened (before, only after the Scheduler page had been opened)
- Web admin execution logs show the duration and, for failures, the message of each run; "run now" executions are marked instead of linking to a trigger that no longer exists
- Web admin pages that were opened with a filter (e.g. "Executions" of one job) name it and offer to show all
- Unused web admin timeline template and its CSS removed (no page loaded them)
- Build copies the whole web admin folder without resource filtering, since its scripts use `${...}` in template strings
- Web admin Dashboard, the new start page: triggers by state, runs, success rate and mean duration over the last hour, 24 hours or 7 days, a chart of successful and failed runs, the latest failures, the triggers that fire next and the cluster's nodes; the state bar links to the trigger list filtered by state
- Web admin trigger list: search, filter by state, sortable columns, and pause, resume and run now on each row
- Web admin trigger details: the cron expression in words (cronstrue), the next five fire times, and a chart of the job's mean and longest duration
- Web admin execution logs: filter by result and by time on the server (the text filter still searches the page shown), and a Timeline view of the last 15 minutes to 24 hours as bars per job; a click on a bar opens the run
- Web admin execution details: job data indented when it is JSON, copy and line-wrap buttons on the job data and the output
- Web admin charts drawn with Chart.js 4.6.2 and cron descriptions with cronstrue 3.51.0, both bundled in the jar
- API `GET {api-uri}/stats`: runs added up per time bucket (`from`, `to`, `bucketSeconds`, optional `groupName` and `jobName`), at most 31 days and 200 buckets
- API `GET {api-uri}/log` takes `success`, `from` and `to`; `GET {api-uri}/log/timeline` returns the latest runs of a time window without their job data and output
- API `GET {api-uri}/trigger/fire-times`: the next fire times of a trigger, skipping times its calendar excludes
- `JobExecutionLogService` gains `getJobExecutionLogList(..., success, from, to)` and `getJobExecutionStats(...)` as default methods, so a host's own implementation keeps compiling; until it overrides them, the new filters and the statistics are answered `501`
- In-memory execution log returns its entries newest first and one page at a time (it returned every entry, oldest first, whatever page was asked for)
- Project version `1.0.5`

### Version 1.0.4 (2026-10-25)
- Web admin remembers the refresh toggle in the browser's `localStorage` (previously a session cookie that only kept "refresh disabled" until the browser closed); switching it on refreshes the current page right away instead of at the next page change, and only one refresh loop runs at a time; an existing `__autoRefreshToggle` cookie is carried over once
- Web admin dark theme: a theme button in the top bar switches between light (Bootswatch "spacelab") and dark (Bootswatch "darkly" 3.4.1, vendored without its Google Fonts import), remembered in `localStorage`; without a stored choice the UI follows the system's light or dark setting; `theme.js` applies the theme before the page is painted, so a dark page does not flash light
- Project version `1.0.4-SNAPSHOT`

### Version 1.0.3
- README section "Securing the web admin and the API": both paths must be protected by the host, one prefix for both, an example Spring Security rule (the one the tests use), CSRF, the UI's headers and the create switch
- Web admin passes requests for the API on when `api-uri` lies below `web-admin.uri` (e.g. `/admin/scheduler` and `/admin/scheduler/api`); before, the UI's filter caught them and the API could not be reached in that layout
- Project version `1.0.3`
- Web admin front-end libraries are bundled in the jar under `webadmin/vendor/` and loaded with relative URLs, so the UI makes no request to another host; versions, npm sources and integrity hashes are listed in `webadmin/vendor/README.md`
- Front-end libraries upgraded within their lines: AngularJS, angular-route and angular-animate 1.8.3 (from 1.2.6/1.2.0), jQuery 3.7.1 (from 1.12.0), Bootstrap and the Bootswatch "spacelab" theme 3.4.1 (from 3.3.6), Moment.js 2.31.0, AngularJS-Toaster 3.0.0, Bootstrap Toggle 2.2.2; `jquery-cookie` replaced by `js-cookie` 3.0.8; `html5shiv`, `respond.js` and the unused `angular-css` dropped; the theme's Google Fonts import removed
- Web admin runs AngularJS in `ng-csp` mode and sends a restrictive `Content-Security-Policy` (`default-src 'self'`, inline styles and `data:` images allowed, no inline or eval'd scripts)
- Web admin fixes found while upgrading: error toasts no longer fail on a response that is not JSON, auto-refresh no longer reloads a page that was left (it requested a just-deleted trigger), and pause/resume/delete toasts name the trigger they acted on
- Build copies `webadmin/vendor/` without resource filtering, so fonts and scripts reach the jar unchanged
- API loads no class by a name the caller sent unless it is a job: `GET {api-uri}/jobs?instanceClass=` matches the name against the scheduler's job classes and registered job classes without loading it (unknown names `400`, previously a `NullPointerException` for a class without `@JobSpec`), and `PUT {api-uri}/create` loads the class without initialising it and refuses anything that does not extend `Job` with `400`
- New setting `quartz-plus.api.create-enabled` (default `true`): when `false`, `PUT {api-uri}/create` is answered `404`
- Execution log shows the job's own exception (class and message) instead of Spring's "Retry policy ... exhausted" wrapper; exceptions from earlier retry attempts are kept as suppressed exceptions in the stack trace (`onFailure` still receives the retry exception, as before)
- A job without retry settings runs once when it fails, as documented (`0` means no retries); in 1.0.2 it was retried once and so ran twice, because Spring Framework 7's `FixedBackOff` counts retries, not runs
- Web admin builds no HTML from data: the `shorten` filter returns plain text, the log details and cluster nodes pages show it with escaping bindings and the full text as a tooltip, nothing is marked trusted with `$sce`, and the execute-job dialog writes and reads names with `.text()`; an error message or node name containing HTML is shown literally instead of running as script
- Web admin works behind a host with Spring Security's CSRF protection: `index.html` is rendered per request with the `_csrf` and `_csrf_header` meta tags when the request carries a CSRF token, and the UI sends that token with every pause, resume, run-now and remove-trigger request; without Spring Security nothing changes
- Spring Security (`spring-security-web`) added as an optional dependency, only used when on the class path
- Web admin starts AngularJS only after the API address has loaded, so the first jobs request no longer goes to a wrong URL (with missing files now answered `404`, that request failed and left "execute now" without its handler)
- Web admin filter serves only files that exist under `webadmin/` in the jar: `..`, backslashes, encoded characters, missing files and unknown file types are answered `404` (previously an empty `200`), and the URI prefix is removed only at the start
- Web admin responses carry `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Content-Security-Policy: frame-ancestors 'none'`, `index.html` also `Cache-Control: no-store`; new setting `quartz-plus.web-admin.security-headers` (default `true`) turns the security headers off
- Web admin filter knows the content types of web fonts and SVG, ready for bundled front-end libraries
