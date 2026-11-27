import {html} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {formatNumber, shorten} from '../format.js';
import {Badge, Card, Empty, PageHeader, Pending, Table, Time} from '../ui.js';

export function NodesPage() {
    const {data, error, reload} = useApi(() => api.get('/nodes'), [], {refresh: true});
    const header = html`<${PageHeader} icon="server" title="Cluster nodes" subtitle=${data && data.length + ' nodes'}/>`;
    if (!data) {
        return html`${header}<${Pending} error=${error} onRetry=${reload}/>`;
    }
    return html`${header}
    <${Card} className="card-flush">
        ${data.length === 0 ? html`<${Empty}>No nodes have checked in<//>` : html`<${Table}>
            <thead><tr><th>Scheduler</th><th>Node instance</th><th>Last check-in</th><th class="num">Check-in interval</th><th>Status</th></tr></thead>
            <tbody>${data.map((node) => html`<tr key=${node.instanceName}>
                <td>${node.schedulerName}</td>
                <td><code title=${node.instanceName}>${shorten(node.instanceName, 40)}</code></td>
                <td><${Time} value=${node.lastCheckInTime}/></td>
                <td class="num">${formatNumber(node.checkInInterval)} ms</td>
                <td>${node.ready ? html`<${Badge} kind="ok">Ready<//>` : html`<${Badge} kind="fail">Not ready<//>`}</td>
            </tr>`)}</tbody>
        <//>`}
    <//>`;
}
