import {html, useMemo, useState} from '../lib.js';
import {api} from '../api.js';
import {baseOptions, ChartView} from '../charts.js';
import {useApi} from '../data.js';
import {formatDateTime, formatDuration, formatNumber} from '../format.js';
import {navigate} from '../router.js';
import {Card, Empty, Pending} from '../ui.js';

const WINDOWS = [
    ['15m', '15 min', 15 * 60 * 1000],
    ['1h', '1 h', 3600 * 1000],
    ['6h', '6 h', 6 * 3600 * 1000],
    ['24h', '24 h', 24 * 3600 * 1000]
];
// the API's limit: a busier window shows only its latest runs
const LIMIT = 1000;

const pad = (value) => String(value).padStart(2, '0');
const clock = (ms) => {
    const d = new Date(ms);
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
};
const laneOf = (run) => run.groupName + '.' + run.jobName;

function timelineChart(span) {
    return ({runs, from, to}, colors) => {
        const lanes = [...new Set(runs.map(laneOf))].sort();
        const bar = (run) => {
            const end = new Date(run.createTime).getTime();
            return {x: [end - (run.duration || 0), end], y: laneOf(run), run};
        };
        const options = baseOptions(colors);
        options.indexAxis = 'y';
        options.scales.x = {
            ...options.scales.x,
            type: 'linear',
            min: from,
            max: to,
            grid: {color: colors.border},
            ticks: {...options.scales.x.ticks, callback: (value) => clock(value), stepSize: span[2] / 6}
        };
        options.scales.y = {
            type: 'category',
            labels: lanes,
            grid: {display: false},
            border: {color: colors.border},
            ticks: {color: colors.muted, font: {family: colors.font}}
        };
        options.plugins.tooltip.callbacks = {
            title: (items) => laneOf(items[0].raw.run),
            label: (item) => {
                const run = item.raw.run;
                return (run.success ? 'Success' : 'Failure') + ' · ' + formatDuration(run.duration)
                    + ' · finished ' + formatDateTime(run.createTime);
            }
        };
        options.onClick = (event, elements, chart) => {
            if (elements.length) {
                const {datasetIndex, index} = elements[0];
                navigate('#/log/' + encodeURIComponent(chart.data.datasets[datasetIndex].data[index].run.id));
            }
        };
        options.onHover = (event, elements) => {
            event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
        };
        const dataset = (label, success, color) => ({
            label,
            data: runs.filter((run) => !!run.success === success).map(bar),
            backgroundColor: color,
            borderRadius: 2,
            borderSkipped: false,
            minBarLength: 3,
            barPercentage: 0.7,
            categoryPercentage: 0.9,
            grouped: false
        });
        return {
            type: 'bar',
            data: {datasets: [dataset('Success', true, colors.accent), dataset('Failure', false, colors.fail)]},
            options
        };
    };
}

/**
 * The runs of the last minutes or hours as bars from start to finish, one row per job; a click on a bar opens the
 * run's log. groupName and jobName are filtered by the API, the other filters here.
 */
export function ExecutionTimeline({filters, result}) {
    const [windowKey, setWindowKey] = useState('1h');
    const span = WINDOWS.find(([key]) => key === windowKey);
    const {data, error, reload} = useApi(() => {
        const to = Date.now();
        const from = to - span[2];
        return api.get('/log/timeline', {
            groupName: filters.groupName,
            jobName: filters.jobName,
            from: new Date(from).toISOString(),
            to: new Date(to).toISOString(),
            limit: LIMIT
        }).then((runs) => ({runs, from, to}));
    }, [windowKey], {refresh: true});

    // kept between renders, so the chart is drawn again only when the runs or the result filter change
    const shown = useMemo(() => data && {
        ...data,
        runs: data.runs.filter((run) => (result === 'all' || run.success === (result === 'success'))
            && (!filters.triggerName || run.triggerName === filters.triggerName)
            && (!filters.instanceClass || run.instanceClass === filters.instanceClass))
    }, [data, result]);
    const lanes = shown ? new Set(shown.runs.map(laneOf)).size : 0;

    return html`<${Card} title="Timeline" icon="activity">
        <div class="card-tools">
            <span class="muted small">${data && (data.runs.length >= LIMIT
                ? 'The latest ' + formatNumber(LIMIT) + ' runs of this window'
                : formatNumber(shown.runs.length) + ' runs') + ' · bars run from start to finish'}</span>
            <div class="seg seg-text" role="group" aria-label="Time window">
                ${WINDOWS.map(([key, label]) => html`<button type="button" key=${key} class=${windowKey === key ? 'on' : ''}
                    aria-pressed=${windowKey === key} onClick=${() => setWindowKey(key)}>${label}</button>`)}
            </div>
        </div>
        ${!shown ? html`<${Pending} error=${error} onRetry=${reload}/>`
            : shown.runs.length === 0 ? html`<${Empty}>No runs in this window<//>`
            : html`<${ChartView} make=${timelineChart(span)} data=${shown} height=${Math.max(160, lanes * 34 + 70)}
                label=${'Runs of the last ' + span[1] + ', one row per job'}/>`}
    <//>`;
}
