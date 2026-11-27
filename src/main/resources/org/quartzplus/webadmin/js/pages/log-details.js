import {html} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {formatBytes, formatDuration} from '../format.js';
import {href, triggerHref} from '../router.js';
import {Card, CodeBlock, KeyValues, PageHeader, Pending, ResultBadge, Time} from '../ui.js';
import {isImmediateTrigger} from './logs.js';

export function LogDetailsPage({params}) {
    const {data: log, error, reload} = useApi(() => api.get('/log', {id: params.id}), []);
    const header = html`<${PageHeader} icon="scroll-text" title=${log ? log.jobName : 'Execution'}
        subtitle=${log && 'Execution in group ' + log.groupName} badge=${log && html`<${ResultBadge} success=${log.success}/>`}/>`;
    if (!log) {
        return html`${header}<${Pending} error=${error} onRetry=${reload}
            notFound=${html`This execution log does not exist: old entries are removed by the cleanup job. <a href="#/logs">All executions</a>`}/>`;
    }
    const immediate = isImmediateTrigger(log.triggerName);
    const jsonData = log.jsonData || '';
    const output = log.stackTrace || '';
    return html`${header}
    <div class="grid grid-2">
        <${Card} title="Execution" icon="activity">
            <${KeyValues} rows=${[
                ['Result', html`<${ResultBadge} success=${log.success}/>`],
                ['Finished', html`<${Time} value=${log.createTime}/>`],
                ['Duration', formatDuration(log.duration)],
                ['Priority', log.priority],
                ['Message', html`<span class="wrap">${log.errorMessage}</span>`]
            ]}/>
        <//>
        <${Card} title="Job" icon="package">
            <${KeyValues} rows=${[
                ['Group', log.groupName],
                ['Job', immediate ? log.jobName : html`<a href=${href('/jobs', {groupName: log.groupName, jobName: log.jobName})}>${log.jobName}</a>`],
                ['Trigger', immediate
                    ? html`${log.triggerName} <span class="muted">· run now trigger</span>`
                    : html`<a href=${triggerHref(log.groupName, log.triggerName)}>${log.triggerName}</a>`],
                ['Instance class', html`<code>${log.instanceClass}</code>`],
                ['Execution instance', html`<code>${log.fireInstanceId}</code>`],
                ['Log ID', html`<code>${log.id}</code>`]
            ]}/>
        <//>
    </div>
    <${Card} title=${'Execution data · ' + formatBytes(jsonData.length)} icon="file-text">
        <${CodeBlock} text=${prettyJson(jsonData)} emptyText="No job data."/>
    <//>
    <${Card} title=${'Output / stack trace · ' + formatBytes(output.length)} icon="file-text">
        <${CodeBlock} text=${output} emptyText="No output was captured."/>
    <//>`;
}

// job data is stored as one line of JSON: indented for reading, or as it is when it is not JSON
function prettyJson(text) {
    try {
        return JSON.stringify(JSON.parse(text), null, 2);
    } catch (e) {
        return text;
    }
}
