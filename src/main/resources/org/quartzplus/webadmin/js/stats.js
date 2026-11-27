// Execution statistics from GET {api-uri}/stats: time ranges, totals and the charts drawn from them.
import {html} from './lib.js';
import {baseOptions} from './charts.js';
import {formatDuration, formatNumber} from './format.js';

export const RANGES = {
    '1h': {label: '1 h', ms: 3600 * 1000, bucketSeconds: 120, title: 'the last hour'},
    '24h': {label: '24 h', ms: 24 * 3600 * 1000, bucketSeconds: 3600, title: 'the last 24 hours'},
    '7d': {label: '7 d', ms: 7 * 24 * 3600 * 1000, bucketSeconds: 6 * 3600, title: 'the last 7 days'}
};

const RANGE_STORAGE_KEY = 'quartz-plus.stats-range';

export function readRange() {
    try {
        const value = window.localStorage.getItem(RANGE_STORAGE_KEY);
        return RANGES[value] ? value : '24h';
    } catch (e) {
        return '24h';
    }
}

export function storeRange(range) {
    try {
        window.localStorage.setItem(RANGE_STORAGE_KEY, range);
    } catch (e) {
        // storage blocked: the choice lasts until the page is reloaded
    }
}

// the query for a range: buckets end on whole bucket lengths, the last one is the one under way
export function statsQuery(range, extra) {
    const r = RANGES[range];
    const bucketMs = r.bucketSeconds * 1000;
    const to = Math.ceil(Date.now() / bucketMs) * bucketMs;
    return {
        ...extra,
        from: new Date(to - r.ms).toISOString(),
        to: new Date(to).toISOString(),
        bucketSeconds: r.bucketSeconds
    };
}

const pad = (value) => String(value).padStart(2, '0');
const weekday = new Intl.DateTimeFormat('en', {weekday: 'short'});

export function bucketLabel(range, value) {
    const d = new Date(value);
    const time = pad(d.getHours()) + ':' + pad(d.getMinutes());
    return range === '7d' ? weekday.format(d) + ' ' + time : time;
}

// runs, failures and durations of all buckets together
export function summarize(buckets) {
    const total = {success: 0, failure: 0, duration: 0, max: 0};
    (buckets || []).forEach((b) => {
        total.success += b.successCount;
        total.failure += b.failureCount;
        total.duration += b.totalDuration;
        total.max = Math.max(total.max, b.maxDuration);
    });
    total.runs = total.success + total.failure;
    total.average = total.runs ? total.duration / total.runs : null;
    total.successRate = total.runs ? (total.success / total.runs) * 100 : null;
    return total;
}

export function RangeSwitch({value, onChange}) {
    return html`<div class="seg seg-text" role="group" aria-label="Time range">
        ${Object.entries(RANGES).map(([key, r]) => html`<button type="button" key=${key} class=${value === key ? 'on' : ''}
            aria-pressed=${value === key} title=${'Show ' + r.title} onClick=${() => onChange(key)}>${r.label}</button>`)}
    </div>`;
}

// stacked bars: successful and failed runs per bucket
export function executionsChart(range) {
    return (buckets, colors) => {
        const options = baseOptions(colors);
        options.scales.x.stacked = true;
        options.scales.y.stacked = true;
        options.scales.y.ticks.precision = 0;
        options.plugins.tooltip.mode = 'index';
        options.plugins.tooltip.intersect = false;
        options.plugins.tooltip.callbacks = {
            title: (items) => {
                const bucket = buckets[items[0].dataIndex];
                return bucketLabel(range, bucket.start) + ' – ' + bucketLabel(range, bucket.end);
            }
        };
        return {
            type: 'bar',
            data: {
                labels: buckets.map((b) => bucketLabel(range, b.start)),
                datasets: [
                    {label: 'Success', data: buckets.map((b) => b.successCount), backgroundColor: colors.accent, borderRadius: 3, stack: 'runs'},
                    {label: 'Failure', data: buckets.map((b) => b.failureCount), backgroundColor: colors.fail, borderRadius: 3, stack: 'runs'}
                ]
            },
            options
        };
    };
}

// lines: mean and longest duration per bucket; buckets without runs leave a gap
export function durationChart(range) {
    return (buckets, colors) => {
        const options = baseOptions(colors);
        const runs = (b) => b.successCount + b.failureCount;
        options.scales.y.ticks.callback = (value) => formatDuration(value);
        options.plugins.legend.labels.usePointStyle = true;
        options.plugins.legend.labels.pointStyle = 'circle';
        options.plugins.tooltip.mode = 'index';
        options.plugins.tooltip.intersect = false;
        options.plugins.tooltip.callbacks = {
            title: (items) => {
                const bucket = buckets[items[0].dataIndex];
                return bucketLabel(range, bucket.start) + ' – ' + bucketLabel(range, bucket.end)
                    + ' · ' + formatNumber(runs(bucket)) + ' runs';
            },
            label: (item) => item.dataset.label + ': ' + formatDuration(item.parsed.y)
        };
        return {
            type: 'line',
            data: {
                labels: buckets.map((b) => bucketLabel(range, b.start)),
                datasets: [
                    {
                        label: 'Mean', data: buckets.map((b) => runs(b) ? b.averageDuration : null),
                        borderColor: colors.accent, backgroundColor: colors.accent, pointRadius: 2, tension: 0.25
                    },
                    {
                        label: 'Max', data: buckets.map((b) => runs(b) ? b.maxDuration : null),
                        borderColor: colors.warn, backgroundColor: colors.warn, pointRadius: 2, borderDash: [4, 4], tension: 0.25
                    }
                ]
            },
            options
        };
    };
}
