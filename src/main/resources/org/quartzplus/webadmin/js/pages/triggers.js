import {html, useState} from '../lib.js';
import {api} from '../api.js';
import {useApi} from '../data.js';
import {pick, triggerHref} from '../router.js';
import {useTriggerActions} from '../trigger-actions.js';
import {Card, Empty, FilterNote, Icon, PageHeader, Pending, StateBadge, Table, Time} from '../ui.js';

const STATE_LABELS = {NORMAL: 'Normal', BLOCKED: 'Blocked', PAUSED: 'Paused', ERROR: 'Error', COMPLETE: 'Complete', NONE: 'None'};
const time = (value) => value ? new Date(value).getTime() : Infinity;

// sort keys of the columns; a column without a time sorts after all others
const COLUMNS = [
    ['group', 'Group', (t) => t.trigger.group.toLowerCase()],
    ['name', 'Trigger', (t) => t.trigger.name.toLowerCase()],
    ['job', 'Job', (t) => (t.trigger.jobName || '').toLowerCase()],
    ['state', 'State', (t) => t.state],
    ['previous', 'Previous fire', (t) => -time(t.trigger.previousFireTime)],
    ['next', 'Next fire', (t) => time(t.trigger.nextFireTime)]
];

function compare(a, b) {
    return a < b ? -1 : a > b ? 1 : 0;
}

function matches({trigger}, text) {
    const needle = text.trim().toLowerCase();
    return !needle || [trigger.group, trigger.name, trigger.jobName, trigger.description]
        .some((value) => value && value.toLowerCase().includes(needle));
}

function SortHeader({column, sort, onSort}) {
    const [key, label] = column;
    const active = sort.key === key;
    return html`<th aria-sort=${active ? (sort.asc ? 'ascending' : 'descending') : undefined}>
        <button type="button" class="sort" onClick=${() => onSort(key)}>${label}
            <${Icon} name=${active ? (sort.asc ? 'chevron-up' : 'chevron-down') : 'chevrons-up-down'} size=${14}/></button>
    </th>`;
}

export function TriggersPage({query}) {
    const filters = pick(query, ['groupName', 'jobName']);
    const {data, error, reload} = useApi(() => api.get('/triggers', filters), [], {refresh: true});
    const [text, setText] = useState('');
    const [state, setState] = useState(STATE_LABELS[query.state] ? query.state : null);
    const [sort, setSort] = useState({key: null, asc: true});
    const actions = useTriggerActions(reload);

    const header = html`<${PageHeader} icon="clock" title="Triggers" subtitle=${data && data.length + ' triggers'}/>`;
    if (!data) {
        return html`${header}<${FilterNote} filters=${filters} clearHref="#/triggers"/><${Pending} error=${error} onRetry=${reload}/>`;
    }
    const counts = {};
    data.forEach((t) => {
        counts[t.state] = (counts[t.state] || 0) + 1;
    });
    const sortBy = COLUMNS.find(([key]) => key === sort.key);
    const rows = data
        .filter((t) => (!state || t.state === state) && matches(t, text))
        .sort((a, b) => sortBy ? compare(sortBy[2](a), sortBy[2](b)) * (sort.asc ? 1 : -1) : 0);
    const onSort = (key) => setSort({key, asc: sort.key === key ? !sort.asc : true});

    return html`${header}
    <${FilterNote} filters=${filters} clearHref="#/triggers"/>
    <div class="toolbar">
        <label class="search">
            <${Icon} name="search"/>
            <input type="search" placeholder="Search group, trigger or job" aria-label="Search triggers" value=${text}
                onInput=${(e) => setText(e.currentTarget.value)}/>
        </label>
        <div class="chips" role="group" aria-label="Show triggers in state">
            <button type="button" class=${'chip-btn' + (state ? '' : ' on')} aria-pressed=${!state} onClick=${() => setState(null)}>
                All <span class="count">${data.length}</span></button>
            ${Object.keys(STATE_LABELS).filter((s) => counts[s]).map((s) => html`<button type="button" key=${s}
                class=${'chip-btn' + (state === s ? ' on' : '')} aria-pressed=${state === s} onClick=${() => setState(state === s ? null : s)}>
                ${STATE_LABELS[s]} <span class="count">${counts[s]}</span></button>`)}
        </div>
    </div>
    <${Card} className="card-flush">
        ${rows.length === 0 ? html`<${Empty}>${data.length ? 'No trigger matches' : 'No triggers'}<//>` : html`<${Table}>
            <thead><tr>
                ${COLUMNS.map((column) => html`<${SortHeader} key=${column[0]} column=${column} sort=${sort} onSort=${onSort}/>`)}
                <th class="num">Actions</th>
            </tr></thead>
            <tbody>${rows.map((t) => {
                const {trigger} = t;
                const busy = actions.busy(trigger);
                return html`<tr key=${trigger.group + '.' + trigger.name}>
                    <td>${trigger.group}</td>
                    <td><a class="strong" href=${triggerHref(trigger.group, trigger.name)}>${trigger.name}</a></td>
                    <td>${trigger.jobName}</td>
                    <td><${StateBadge} state=${t.state}/></td>
                    <td><${Time} value=${trigger.previousFireTime}/></td>
                    <td><${Time} value=${trigger.nextFireTime}/></td>
                    <td class="row-actions">
                        ${t.state === 'PAUSED'
                            ? html`<button type="button" class="icon-btn" title="Resume" aria-label=${'Resume ' + trigger.name}
                                disabled=${busy} onClick=${() => actions.resume(trigger)}><${Icon} name="play"/></button>`
                            : html`<button type="button" class="icon-btn" title="Pause" aria-label=${'Pause ' + trigger.name}
                                disabled=${busy} onClick=${() => actions.pause(trigger)}><${Icon} name="pause"/></button>`}
                        <button type="button" class="icon-btn" title="Run now" aria-label=${'Run ' + trigger.jobName + ' now'}
                            disabled=${busy} onClick=${() => actions.openRun(trigger)}><${Icon} name="zap"/></button>
                    </td>
                </tr>`;
            })}</tbody>
        <//>`}
    <//>
    ${actions.dialog}`;
}
