import {html, useState} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {formatBytes, formatDuration, formatNumber} from '../format.js';
import {href, pick, triggerHref} from '../router.js';
import {Card, Empty, FilterNote, Icon, PageHeader, Pagination, Pending, ResultBadge, Table, Time} from '../ui.js';
import {ExecutionTimeline} from './timeline.js';

const PAGE_SIZES = [10, 25, 100, 500];
const RESULTS = [['all', 'All'], ['success', 'Success'], ['failure', 'Failure']];
const PERIODS = [
    ['', 'Any time', 0],
    ['1h', 'Last hour', 3600 * 1000],
    ['24h', 'Last 24 hours', 24 * 3600 * 1000],
    ['7d', 'Last 7 days', 7 * 24 * 3600 * 1000]
];
// triggers Quartz creates for "run now" are named MT_...; they are gone once the job ran
export const isImmediateTrigger = (name) => (name || '').startsWith('MT_');

function TriggerCell({log}) {
    if (isImmediateTrigger(log.triggerName)) {
        return html`<span class="muted" title=${log.triggerName}>run now</span>`;
    }
    return html`<a href=${triggerHref(log.groupName, log.triggerName)}>${log.triggerName}</a>`;
}

function matches(log, text) {
    const needle = text.trim().toLowerCase();
    return !needle || [log.groupName, log.triggerName, log.jobName, log.instanceClass, log.errorMessage]
        .some((value) => value && value.toLowerCase().includes(needle));
}

function Segments({options, value, onChange, label}) {
    return html`<div class="seg seg-text" role="group" aria-label=${label}>
        ${options.map(([key, text]) => html`<button type="button" key=${key} class=${value === key ? 'on' : ''}
            aria-pressed=${value === key} onClick=${() => onChange(key)}>${text}</button>`)}
    </div>`;
}

function LogList({filters, result, period}) {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);
    const [text, setText] = useState('');
    const {data, error, reload} = useApi(() => {
        const span = PERIODS.find(([key]) => key === period)[2];
        return api.get('/log', {
            ...filters,
            pageSize,
            currentPage: page,
            success: result === 'all' ? undefined : result === 'success',
            from: span ? new Date(Date.now() - span).toISOString() : undefined
        });
    }, [page, pageSize], {refresh: true});

    if (!data) {
        return html`<${Pending} error=${error} onRetry=${reload}/>`;
    }
    const rows = (data.content || []).filter((log) => matches(log, text));
    const first = data.number * data.size;
    return html`<div class="toolbar">
        <label class="search">
            <${Icon} name="search"/>
            <input type="search" placeholder="Filter this page" aria-label="Filter the rows of this page" value=${text}
                onInput=${(e) => setText(e.currentTarget.value)}/>
        </label>
        <span class="spacer"></span>
        <label class="inline-field">
            <span class="muted">Rows per page</span>
            <select value=${pageSize} onChange=${(e) => {
                setPageSize(Number(e.currentTarget.value));
                setPage(0);
            }}>${PAGE_SIZES.map((size) => html`<option value=${size} key=${size}>${size}</option>`)}</select>
        </label>
    </div>
    <${Card} className="card-flush">
        ${rows.length === 0 ? html`<${Empty}>${text ? 'No rows on this page match the filter' : 'No executions match'}<//>` : html`<${Table}>
            <thead><tr><th>Result</th><th>Finished</th><th>Group</th><th>Trigger</th><th>Job</th><th class="num">Duration</th><th>Message</th><th class="num">Size</th></tr></thead>
            <tbody>${rows.map((log) => html`<tr key=${log.id}>
                <td><a href=${'#/log/' + encodeURIComponent(log.id)} aria-label="Execution details"><${ResultBadge} success=${log.success}/></a></td>
                <td><a href=${'#/log/' + encodeURIComponent(log.id)}><${Time} value=${log.createTime}/></a></td>
                <td>${log.groupName}</td>
                <td><${TriggerCell} log=${log}/></td>
                <td><a href=${href('/jobs', {groupName: log.groupName, jobName: log.jobName})}>${log.jobName}</a></td>
                <td class="num nowrap">${formatDuration(log.duration)}</td>
                <td class="message">${!log.success && html`<span title=${log.errorMessage}>${log.errorMessage}</span>`}</td>
                <td class="num nowrap muted">${formatBytes((log.jsonData || '').length + (log.stackTrace || '').length)}</td>
            </tr>`)}</tbody>
        <//>`}
    <//>
    <div class="table-foot">
        <span class="muted">${data.totalElements === 0 ? '' : formatNumber(first + 1) + '–'
            + formatNumber(first + (data.numberOfElements ?? data.content.length)) + ' of ' + formatNumber(data.totalElements)}</span>
        <${Pagination} page=${data.number} totalPages=${data.totalPages} onPage=${setPage}/>
    </div>`;
}

export function LogsPage({query}) {
    const filters = pick(query, ['groupName', 'jobName', 'triggerName', 'instanceClass']);
    const [view, setView] = useState(query.view === 'timeline' ? 'timeline' : 'list');
    const [result, setResult] = useState(query.success === 'true' ? 'success' : query.success === 'false' ? 'failure' : 'all');
    const [period, setPeriod] = useState('');

    return html`<${PageHeader} icon="scroll-text" title="Execution logs">
        <${Segments} label="View" value=${view} onChange=${setView} options=${[['list', 'List'], ['timeline', 'Timeline']]}/>
    <//>
    <${FilterNote} filters=${filters} clearHref="#/logs"/>
    <div class="toolbar">
        <${Segments} label="Result" value=${result} onChange=${setResult} options=${RESULTS}/>
        ${view === 'list' && html`<select aria-label="Finished" value=${period} onChange=${(e) => setPeriod(e.currentTarget.value)}>
            ${PERIODS.map(([key, label]) => html`<option value=${key} key=${key}>${label}</option>`)}
        </select>`}
    </div>
    ${view === 'list'
        ? html`<${LogList} key=${result + '|' + period} filters=${filters} result=${result} period=${period}/>`
        : html`<${ExecutionTimeline} filters=${filters} result=${result}/>`}`;
}
