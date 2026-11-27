import {html} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {formatNumber} from '../format.js';
import {href} from '../router.js';
import {Badge, Card, KeyValues, PageHeader, Pending, Time} from '../ui.js';

function status(metaData) {
    if (metaData.shutdown) {
        return html`<${Badge} kind="fail">Shut down<//>`;
    }
    if (metaData.inStandbyMode) {
        return html`<${Badge} kind="paused">Standby<//>`;
    }
    return metaData.started ? html`<${Badge} kind="ok">Running<//>` : html`<${Badge} kind="none">Not started<//>`;
}

const code = (text) => text ? html`<code>${text}</code>` : null;

export function SchedulerPage() {
    const {data, error, reload} = useApi(() => api.get(''), [], {refresh: true});
    const header = html`<${PageHeader} icon="info" title="Scheduler information"
        subtitle=${data && data.schedulerMetaData.schedulerName}/>`;
    if (!data) {
        return html`${header}<${Pending} error=${error} onRetry=${reload}/>`;
    }
    const md = data.schedulerMetaData;
    return html`${header}
    <div class="grid grid-2">
        <${Card} title="Scheduler" icon="clock">
            <${KeyValues} rows=${[
                ['Name', md.schedulerName],
                ['Status', status(md)],
                ['Instance ID', code(md.schedulerInstanceId)],
                ['Clustered', md.jobStoreClustered ? 'Yes' : 'No'],
                ['Running since', html`<${Time} value=${md.runningSince}/>`],
                ['Jobs executed', formatNumber(md.numberOfJobsExecuted)],
                ['Last access', html`<${Time} value=${data.serverTimestamp}/>`]
            ]}/>
        <//>
        <${Card} title="Runtime" icon="server">
            <${KeyValues} rows=${[
                ['Quartz version', md.version],
                ['Quartz Plus version', data.quartzPlusVersion],
                ['Scheduler class', code(md.schedulerClass)],
                ['Job store class', code(md.jobStoreClass)],
                ['Thread pool', html`<code>${md.threadPoolClass}</code> <span class="muted">· ${md.threadPoolSize} threads</span>`],
                ['Table prefix', code(data.tablePrefix)],
                ['Execution log service', code(data.jobExecutionLogServiceClass)]
            ]}/>
        <//>
    </div>
    <${Card} title="Job classes" icon="package">
        <ul class="link-list">
            ${(data.jobsCollection || []).map((jobClass) => html`<li key=${jobClass}>
                <a href=${href('/jobs', {instanceClass: jobClass})}><code>${jobClass}</code></a></li>`)}
        </ul>
    <//>`;
}
