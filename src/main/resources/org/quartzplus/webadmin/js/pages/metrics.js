import {html} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {formatDuration, formatNumber} from '../format.js';
import {href} from '../router.js';
import {Badge, Card, Empty, PageHeader, Pending, Table} from '../ui.js';

// meter names are "jobs.<group>.<job>.<kind>"; the API lists "<group>.<job>"
function splitJob(groupAndName) {
    const dot = groupAndName.indexOf('.');
    return dot < 0 ? ['', groupAndName] : [groupAndName.substring(0, dot), groupAndName.substring(dot + 1)];
}

export function MetricsPage() {
    const {data, error, reload} = useApi(() => api.get('/metrics'), [], {refresh: true});
    const header = html`<${PageHeader} icon="activity" title="Instance metrics"
        subtitle=${data && 'Counted on instance ' + data.instanceId + ' since it started'}/>`;
    if (!data) {
        return html`${header}<${Pending} error=${error} onRetry=${reload}/>`;
    }
    const meters = data.meters || {};
    const timers = data.timers || {};
    return html`${header}
    <${Card} className="card-flush">
        ${data.jobList.length === 0 ? html`<${Empty}>No job has run on this instance yet<//>` : html`<${Table}>
            <thead><tr><th>Group</th><th>Job</th><th class="num">Success</th><th class="num">Failure</th><th class="num">Duration (mean)</th><th class="num">Duration (max)</th></tr></thead>
            <tbody>${data.jobList.map((job) => {
                const [group, name] = splitJob(job);
                const timer = timers['jobs.' + job + '.duration'] || [];
                return html`<tr key=${job}>
                    <td>${group}</td>
                    <td><a href=${href('/jobs', {groupName: group, jobName: name})}>${name}</a></td>
                    <td class="num"><${Badge} kind="ok">${formatNumber(meters['jobs.' + job + '.success'] || 0)}<//></td>
                    <td class="num"><${Badge} kind=${meters['jobs.' + job + '.failure'] ? 'fail' : 'none'}>${formatNumber(meters['jobs.' + job + '.failure'] || 0)}<//></td>
                    <td class="num">${formatDuration(timer[0])}</td>
                    <td class="num">${formatDuration(timer[1])}</td>
                </tr>`;
            })}</tbody>
        <//>`}
    <//>`;
}
