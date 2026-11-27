// The application shell: top bar, navigation, the page for the current address, and the refresh timer.
import {html, useCallback, useEffect, useMemo, useState} from './lib.js';
import {api} from './api.js';
import {AppContext, readRefreshInterval, REFRESH_INTERVALS, storeRefreshInterval} from './data.js';
import {formatSeconds} from './format.js';
import {matchRoute, useRoute} from './router.js';
import {Icon, Toasts, useNow} from './ui.js';
import {ConfigurationPage} from './pages/configuration.js';
import {DashboardPage} from './pages/dashboard.js';
import {JobsPage} from './pages/jobs.js';
import {LogDetailsPage} from './pages/log-details.js';
import {LogsPage} from './pages/logs.js';
import {MetricsPage} from './pages/metrics.js';
import {NodesPage} from './pages/nodes.js';
import {SchedulerPage} from './pages/scheduler.js';
import {TriggerDetailsPage} from './pages/trigger-details.js';
import {TriggersPage} from './pages/triggers.js';

const DEFAULT_PATH = '/dashboard';

const NAVIGATION = [
    {path: '/dashboard', icon: 'layout-dashboard', label: 'Dashboard'},
    {path: '/scheduler', icon: 'info', label: 'Scheduler info'},
    {path: '/configuration', icon: 'sliders-horizontal', label: 'Configuration'},
    {path: '/triggers', icon: 'clock', label: 'Triggers', also: '/trigger/'},
    {path: '/jobs', icon: 'package', label: 'Job classes'},
    {path: '/logs', icon: 'scroll-text', label: 'Execution logs', also: '/log/'},
    {path: '/nodes', icon: 'server', label: 'Cluster nodes', clusteredOnly: true},
    {path: '/metrics', icon: 'activity', label: 'Instance metrics'}
];

const ROUTES = [
    {path: '/dashboard', page: DashboardPage},
    {path: '/scheduler', page: SchedulerPage},
    {path: '/configuration', page: ConfigurationPage},
    {path: '/triggers', page: TriggersPage},
    {path: '/trigger/:groupName/:triggerName', page: TriggerDetailsPage},
    {path: '/jobs', page: JobsPage},
    {path: '/logs', page: LogsPage},
    {path: '/log/:id', page: LogDetailsPage},
    {path: '/nodes', page: NodesPage},
    {path: '/metrics', page: MetricsPage}
];

const THEMES = [
    ['device', 'monitor', 'Follow the device'],
    ['light', 'sun', 'Light'],
    ['dark', 'moon', 'Dark']
];

function RefreshControl({interval, onInterval, onRefresh, lastLoaded}) {
    const now = useNow(1000);
    const seconds = lastLoaded ? Math.max(0, Math.round((now - lastLoaded) / 1000)) : null;
    return html`<div class="refresh">
        <span class="refresh-status">${seconds === null ? '' : seconds < 2 ? 'Updated just now' : 'Updated ' + formatSeconds(seconds) + ' ago'}</span>
        <button type="button" class="icon-btn" title="Refresh now" aria-label="Refresh now" onClick=${onRefresh}>
            <${Icon} name="refresh-cw"/></button>
        <select class="select-sm" aria-label="Automatic refresh" value=${interval} onChange=${(e) => onInterval(Number(e.currentTarget.value))}>
            ${REFRESH_INTERVALS.map((s) => html`<option value=${s} key=${s}>${s ? 'Every ' + s + ' s' : 'Auto refresh off'}</option>`)}
        </select>
    </div>`;
}

function ThemeSwitch() {
    const [mode, setMode] = useState(window.quartzPlusTheme.mode());
    return html`<div class="seg" role="group" aria-label="Colour theme">
        ${THEMES.map(([value, icon, label]) => html`<button type="button" key=${value} class=${mode === value ? 'on' : ''}
            aria-pressed=${mode === value} title=${label} aria-label=${label}
            onClick=${() => {
                window.quartzPlusTheme.set(value);
                setMode(value);
            }}><${Icon} name=${icon}/></button>`)}
    </div>`;
}

function Sidebar({path, clustered, version}) {
    return html`<nav class="sidebar" aria-label="Main">
        <ul>${NAVIGATION.filter((item) => !item.clusteredOnly || clustered).map((item) => {
            const active = path === item.path || (item.also && path.startsWith(item.also));
            return html`<li key=${item.path}><a href=${'#' + item.path} class=${active ? 'active' : ''}
                aria-current=${active ? 'page' : undefined}><${Icon} name=${item.icon} size=${18}/><span>${item.label}</span></a></li>`;
        })}</ul>
        ${version && html`<div class="sidebar-foot">Quartz Plus ${version}</div>`}
    </nav>`;
}

export function App() {
    const route = useRoute();
    const [interval, setInterval_] = useState(readRefreshInterval());
    const [tick, setTick] = useState(0);
    const [manual, setManual] = useState(0);
    const [lastLoaded, setLastLoaded] = useState(null);
    const [visible, setVisible] = useState(!document.hidden);
    const [navOpen, setNavOpen] = useState(false);
    const [info, setInfo] = useState(null);

    // the cluster entry and the version in the navigation; a failure shows on the page that needs the data
    useEffect(() => {
        api.get('').then(setInfo, () => undefined);
    }, []);

    // no polling while the tab is hidden; coming back refreshes at once
    useEffect(() => {
        const onVisibility = () => {
            setVisible(!document.hidden);
            if (!document.hidden && interval > 0) {
                setTick((t) => t + 1);
            }
        };
        document.addEventListener('visibilitychange', onVisibility);
        return () => document.removeEventListener('visibilitychange', onVisibility);
    }, [interval]);

    useEffect(() => {
        if (interval > 0 && visible) {
            const timer = setInterval(() => setTick((t) => t + 1), interval * 1000);
            return () => clearInterval(timer);
        }
        return undefined;
    }, [interval, visible]);

    useEffect(() => setNavOpen(false), [route.key]);

    const matched = matchRoute(ROUTES, route.path);
    useEffect(() => {
        if (!matched) {
            window.location.replace('#' + DEFAULT_PATH);
        }
    }, [route.key]);

    const reportLoaded = useCallback(() => setLastLoaded(Date.now()), []);
    const context = useMemo(() => ({tick, manual, reportLoaded, info}), [tick, manual, info]);
    const changeInterval = (seconds) => {
        storeRefreshInterval(seconds);
        setInterval_(seconds);
        if (seconds > 0) {
            setTick((t) => t + 1);
        }
    };
    const Page = matched && matched.route.page;

    return html`<${AppContext.Provider} value=${context}>
        <div class=${'app' + (navOpen ? ' nav-open' : '')}>
            <header class="topbar">
                <button type="button" class="icon-btn nav-toggle" aria-label="Show navigation" aria-expanded=${navOpen}
                    onClick=${() => setNavOpen(!navOpen)}><${Icon} name="menu" size=${20}/></button>
                <a class="brand" href=${'#' + DEFAULT_PATH}><img src="logo.png" alt="" width="26" height="26"/>Quartz Plus</a>
                <span class="spacer"></span>
                <${RefreshControl} interval=${interval} onInterval=${changeInterval} lastLoaded=${lastLoaded}
                    onRefresh=${() => setManual((m) => m + 1)}/>
                <${ThemeSwitch}/>
            </header>
            <${Sidebar} path=${route.path} clustered=${info && info.schedulerMetaData.jobStoreClustered}
                version=${info && info.quartzPlusVersion}/>
            <div class="nav-backdrop" onClick=${() => setNavOpen(false)}></div>
            <main class="main">
                ${Page && html`<${Page} key=${route.key} params=${matched.params} query=${route.query}/>`}
            </main>
        </div>
        <${Toasts}/>
    <//>`;
}
