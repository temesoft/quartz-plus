import {html} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {href, pick} from '../router.js';
import {Card, Empty, FilterNote, Icon, PageHeader, Pending, Table} from '../ui.js';

export function JobsPage({query}) {
    const filters = pick(query, ['groupName', 'jobName', 'instanceClass']);
    const {data, error, reload} = useApi(() => api.get('/jobs', filters), []);
    const header = html`<${PageHeader} icon="package" title="Job classes" subtitle=${data && data.length + ' jobs'}/>`;
    if (!data) {
        return html`${header}<${FilterNote} filters=${filters} clearHref="#/jobs"/><${Pending} error=${error} onRetry=${reload}/>`;
    }
    const jobs = data.filter((job) => job);
    return html`${header}
    <${FilterNote} filters=${filters} clearHref="#/jobs"/>
    <${Card} className="card-flush">
        ${jobs.length === 0 ? html`<${Empty}>No jobs<//>` : html`<${Table}>
            <thead><tr><th>Group</th><th>Job</th><th>Class and description</th><th></th></tr></thead>
            <tbody>${jobs.map((job) => html`<tr key=${job.group + '.' + job.name}>
                <td>${job.group}</td>
                <td class="strong">${job.name}</td>
                <td><code>${job.jobClass}</code>${job.description && html`<div class="muted small">${job.description}</div>`}</td>
                <td class="row-links">
                    <a href=${href('/triggers', {groupName: job.group, jobName: job.name})}><${Icon} name="clock"/>Triggers</a>
                    <a href=${href('/logs', {groupName: job.group, jobName: job.name})}><${Icon} name="scroll-text"/>Executions</a>
                </td>
            </tr>`)}</tbody>
        <//>`}
    <//>`;
}
