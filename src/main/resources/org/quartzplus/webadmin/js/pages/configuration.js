import {html} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {Card, Empty, PageHeader, Pending, Table} from '../ui.js';

export function ConfigurationPage() {
    const {data, error, reload} = useApi(() => api.get('/config'), []);
    const entries = data ? Object.entries(data) : [];
    const header = html`<${PageHeader} icon="sliders-horizontal" title="Configuration properties"
        subtitle=${data && entries.length + ' properties · passwords, users and secrets are masked'}/>`;
    if (!data) {
        return html`${header}<${Pending} error=${error} onRetry=${reload}/>`;
    }
    return html`${header}
    <${Card} className="card-flush">
        ${entries.length === 0 ? html`<${Empty}>No properties<//>` : html`<${Table}>
            <thead><tr><th>Name</th><th>Value</th></tr></thead>
            <tbody>${entries.map(([name, value]) => html`<tr key=${name}>
                <td class="mono">${name}</td><td class="mono wrap">${String(value)}</td></tr>`)}</tbody>
        <//>`}
    <//>`;
}
