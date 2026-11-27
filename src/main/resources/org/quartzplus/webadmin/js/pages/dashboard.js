import {html, useContext, useState} from '../lib.js';
import {api} from '../api.js';
import {ChartView} from '../charts.js';
import {AppContext, useApi} from '../data.js';
import {formatDateTime, formatDuration, formatNumber, fromNow} from '../format.js';
import {href, triggerHref} from '../router.js';
import {executionsChart, RangeSwitch, RANGES, readRange, statsQuery, storeRange, summarize} from '../stats.js';
import {Card, Empty, Icon, PageHeader, Pending, useNow} from '../ui.js';

const STATES = [
    ['NORMAL', 'normal', 'Normal'],
    ['BLOCKED', 'blocked', 'Blocked'],
    ['PAUSED', 'paused', 'Paused'],
    ['ERROR', 'error', 'Error'],
    ['COMPLETE', 'complete', 'Complete'],
    ['NONE', 'none', 'None']
];
const UPCOMING = 8;

function Kpi({icon, label, value, detail, tone = ''}) {
    return html`<div class="card kpi">
        <div class="kpi-label"><span class="kpi-icon"><${Icon} name=${icon}/></span>${label}</div>
        <div class="kpi-value">${value}</div>
        <div class=${'kpi-detail ' + tone}>${detail}</div>
    </div>`;
}

function countStates(triggers) {
    const counts = {};
    triggers.forEach(({state}) => {
        counts[state] = (counts[state] || 0) + 1;
    });
    return counts;
}

// one bar split by trigger state; each part and legend entry opens the trigger list showing that state only
function TriggerStates({triggers}) {
    const counts = countStates(triggers);
    const present = STATES.filter(([state]) => counts[state]);
    if (!triggers.length) {
        return html`<${Empty}>No triggers<//>`;
    }
    return html`<div class="state-bar" role="img" aria-label=${present.map(([s, , l]) => counts[s] + ' ' + l).join(', ')}>
            ${present.map(([state, kind, label]) => html`<a key=${state} class=${'badge-' + kind} href=${href('/triggers', {state})}
                style=${'flex: ' + counts[state]} title=${counts[state] + ' ' + label}></a>`)}
        </div>
        <ul class="legend">
            ${present.map(([state, kind, label]) => html`<li key=${state}><a href=${href('/triggers', {state})}>
                <span class=${'dot badge-' + kind}></span><span class="strong">${counts[state]}</span> ${label}</a></li>`)}
        </ul>`;
}

function RecentFailures({page}) {
    const logs = page.content || [];
    if (!logs.length) {
        return html`<${Empty}><${Icon} name="circle-check"/>No failed runs logged<//>`;
    }
    return html`<ul class="item-list">
        ${logs.map((log) => html`<li key=${log.id}>
            <span class="item-icon fail"><${Icon} name="circle-x"/></span>
            <div class="item-main">
                <a class="strong" href=${'#/log/' + encodeURIComponent(log.id)}>${log.groupName}.${log.jobName}</a>
                <span class="item-sub ellipsis" title=${log.errorMessage}>${log.errorMessage}</span>
            </div>
            <span class="item-side" title=${formatDateTime(log.createTime)}>${fromNow(log.createTime)}</span>
        </li>`)}
    </ul>`;
}

// the triggers that fire next; paused and completed triggers do not fire
function NextFires({triggers}) {
    const now = useNow(1000);
    const upcoming = triggers
        .filter(({trigger, state}) => trigger.nextFireTime && state !== 'PAUSED' && state !== 'COMPLETE')
        .sort((a, b) => new Date(a.trigger.nextFireTime) - new Date(b.trigger.nextFireTime))
        .slice(0, UPCOMING);
    if (!upcoming.length) {
        return html`<${Empty}>No trigger is due to fire<//>`;
    }
    return html`<ul class="item-list">
        ${upcoming.map(({trigger}) => html`<li key=${trigger.group + '.' + trigger.name}>
            <span class="item-icon"><${Icon} name="clock"/></span>
            <div class="item-main">
                <a class="strong" href=${triggerHref(trigger.group, trigger.name)}>${trigger.name}</a>
                <span class="item-sub">${trigger.group} · ${trigger.jobName}</span>
            </div>
            <span class="item-side" title=${formatDateTime(trigger.nextFireTime)}>${fromNow(trigger.nextFireTime, now)}</span>
        </li>`)}
    </ul>`;
}

function schedulerSummary(info) {
    if (!info) {
        return null;
    }
    const md = info.schedulerMetaData;
    const status = md.shutdown ? 'shut down' : md.inStandbyMode ? 'in standby' : 'running since ' + formatDateTime(md.runningSince);
    return md.schedulerName + ' · ' + status + (md.jobStoreClustered ? ' · clustered' : '');
}

export function DashboardPage() {
    const {info} = useContext(AppContext);
    const clustered = !!(info && info.schedulerMetaData.jobStoreClustered);
    const [range, setRange] = useState(readRange());
    const stats = useApi(() => api.get('/stats', statsQuery(range)), [range], {refresh: true});
    const triggers = useApi(() => api.get('/triggers'), [], {refresh: true});
    const failures = useApi(() => api.get('/log', {success: false, pageSize: 5}), [], {refresh: true});
    const nodes = useApi(() => clustered ? api.get('/nodes') : Promise.resolve(null), [clustered], {refresh: true});

    const changeRange = (value) => {
        storeRange(value);
        setRange(value);
    };
    const total = summarize(stats.data);
    const counts = triggers.data ? countStates(triggers.data) : {};
    const readyNodes = nodes.data ? nodes.data.filter((node) => node.ready).length : 0;
    const lastCheckIn = nodes.data && nodes.data.length
        ? nodes.data.map((node) => node.lastCheckInTime).sort().reverse()[0] : null;

    return html`<${PageHeader} icon="layout-dashboard" title="Dashboard" subtitle=${schedulerSummary(info)}>
        <${RangeSwitch} value=${range} onChange=${changeRange}/>
    <//>
    <div class="kpis">
        <${Kpi} icon="clock" label="Triggers" value=${triggers.data ? formatNumber(triggers.data.length) : '…'}
            detail=${STATES.filter(([s]) => counts[s]).map(([s, , l]) => counts[s] + ' ' + l.toLowerCase()).join(' · ') || ' '}/>
        <${Kpi} icon="activity" label="Executions" value=${stats.data ? formatNumber(total.runs) : '…'}
            detail=${'in ' + RANGES[range].title}/>
        <${Kpi} icon="circle-check" label="Success rate"
            value=${!stats.data ? '…' : total.successRate === null ? '—' : formatNumber(total.successRate, 1) + '%'}
            detail=${total.failure ? formatNumber(total.failure) + (total.failure === 1 ? ' failure' : ' failures') : 'No failures'}
            tone=${total.failure ? 'fail' : 'ok'}/>
        <${Kpi} icon="timer" label="Mean duration" value=${!stats.data ? '…' : total.average === null ? '—' : formatDuration(total.average)}
            detail=${total.runs ? 'longest ' + formatDuration(total.max) : 'no runs'}/>
        ${clustered && html`<${Kpi} icon="server" label="Cluster nodes"
            value=${nodes.data ? readyNodes + ' / ' + nodes.data.length : '…'}
            detail=${lastCheckIn ? 'last check-in ' + fromNow(lastCheckIn) : ' '}
            tone=${nodes.data && readyNodes < nodes.data.length ? 'fail' : ''}/>`}
    </div>
    <div class="grid dash-row">
        <${Card} title="Executions" icon="activity">
            ${stats.data
                ? html`<${ChartView} make=${executionsChart(range)} data=${stats.data} height=${260}
                    label=${'Successful and failed runs in ' + RANGES[range].title}/>`
                : html`<${Pending} error=${stats.error} onRetry=${stats.reload}/>`}
        <//>
        <${Card} title="Trigger states" icon="clock">
            ${triggers.data ? html`<${TriggerStates} triggers=${triggers.data}/>` : html`<${Pending} error=${triggers.error}/>`}
        <//>
    </div>
    <div class="grid grid-2">
        <${Card} title="Recent failures" icon="triangle-alert">
            ${failures.data ? html`<${RecentFailures} page=${failures.data}/>` : html`<${Pending} error=${failures.error}/>`}
            <div class="card-links"><a href=${href('/logs', {success: 'false'})}><${Icon} name="scroll-text"/>All failed runs</a></div>
        <//>
        <${Card} title="Next to fire" icon="calendar-clock">
            ${triggers.data ? html`<${NextFires} triggers=${triggers.data}/>` : html`<${Pending} error=${triggers.error}/>`}
            <div class="card-links"><a href="#/triggers"><${Icon} name="clock"/>All triggers</a></div>
        <//>
    </div>`;
}
