import {html, useState} from '../lib.js';
import {api} from '../api.js';
import {ChartView} from '../charts.js';
import {useApi} from '../data.js';
import {formatDateTime, formatNumber, fromNow} from '../format.js';
import {href, navigate} from '../router.js';
import {durationChart, RangeSwitch, RANGES, readRange, statsQuery, storeRange} from '../stats.js';
import {useTriggerActions} from '../trigger-actions.js';
import {Card, ConfirmDialog, Empty, Icon, KeyValues, PageHeader, Pending, StateBadge, Time, toast, toastError} from '../ui.js';

const NO_RESTRICTIONS_CALENDAR = 'org.quartzplus.calendar.NoRestrictionsCalendar';

// the cron expression in words (cronstrue, loaded as a plain script); null if it cannot describe it
function describeCron(expression) {
    if (!window.cronstrue) {
        return null;
    }
    try {
        return window.cronstrue.toString(expression, {use24HourTimeFormat: true, verbose: true});
    } catch (e) {
        return null;
    }
}

function schedule(trigger) {
    if (trigger.cronExpression) {
        const words = describeCron(trigger.cronExpression);
        return [
            ['Type', 'Cron'],
            ['Cron expression', html`<code>${trigger.cronExpression}</code>${words && html`<div class="small muted">${words}</div>`}`],
            ['Time zone', trigger.timeZone]
        ];
    }
    if (trigger.repeatInterval !== undefined && trigger.repeatInterval !== null) {
        const times = trigger.repeatCount < 0 ? 'repeat indefinitely' : formatNumber(trigger.repeatCount) + ' times';
        return [
            ['Type', 'Simple'],
            ['Repeat interval', formatNumber(trigger.repeatInterval) + ' ms'],
            ['Repeat count', times]
        ];
    }
    return [['Type', 'Other']];
}

function NextFireTimes({groupName, triggerName, paused}) {
    const {data, error} = useApi(() => api.get('/trigger/fire-times', {groupName, triggerName, count: 5}), [], {refresh: true});
    if (!data) {
        return html`<${Pending} error=${error}/>`;
    }
    if (!data.length) {
        return html`<p class="muted">This trigger will not fire again.</p>`;
    }
    return html`${paused && html`<p class="small muted">Paused: it fires at these times only once it is resumed.</p>`}
        <ol class="fire-times">${data.map((time) => html`<li key=${time}>
            <span class="nowrap">${formatDateTime(time)}</span><span class="muted small">${fromNow(time)}</span></li>`)}
        </ol>`;
}

function DurationTrend({trigger}) {
    const [range, setRange] = useState(readRange());
    const {data, error} = useApi(() => api.get('/stats', statsQuery(range, {groupName: trigger.jobGroup, jobName: trigger.jobName})),
        [range], {refresh: true});
    const changeRange = (value) => {
        storeRange(value);
        setRange(value);
    };
    const runs = data ? data.reduce((sum, b) => sum + b.successCount + b.failureCount, 0) : 0;
    return html`<${Card} title=${'Duration of ' + trigger.jobName} icon="timer">
        <div class="card-tools"><span class="muted small">${data && formatNumber(runs) + ' runs in ' + RANGES[range].title}</span>
            <${RangeSwitch} value=${range} onChange=${changeRange}/></div>
        ${!data ? html`<${Pending} error=${error}/>`
            : runs === 0 ? html`<${Empty}>${trigger.jobName} did not run in ${RANGES[range].title}<//>`
            : html`<${ChartView} make=${durationChart(range)} data=${data} height=${220}
                label=${'Mean and longest duration of ' + trigger.jobName + ' in ' + RANGES[range].title}/>`}
    <//>`;
}

export function TriggerDetailsPage({params}) {
    const {groupName, triggerName} = params;
    const {data, error, reload} = useApi(() => api.get('/trigger', {groupName, triggerName}), [], {refresh: true});
    const actions = useTriggerActions(reload);
    const [removing, setRemoving] = useState(null);

    const header = (state, buttons) => html`<${PageHeader} icon="clock" title=${triggerName}
        subtitle=${'Trigger in group ' + groupName} badge=${state && html`<${StateBadge} state=${state}/>`}>${buttons}<//>`;
    if (!data) {
        return html`${header()}<${Pending} error=${error} onRetry=${reload}
            notFound=${html`This trigger does not exist: it may have been removed. <a href="#/triggers">All triggers</a>`}/>`;
    }
    const {trigger, state} = data;
    const busy = actions.busy(trigger) || removing === 'busy';

    const remove = async () => {
        setRemoving('busy');
        try {
            await api.del('/trigger', {groupName: trigger.group, triggerName: trigger.name});
            toast('success', '', 'Trigger "' + trigger.group + '.' + trigger.name + '" is deleted');
            setRemoving(null);
            navigate('#/triggers');
        } catch (e) {
            toastError(e);
            setRemoving('confirm');
        }
    };

    const buttons = html`
        ${state === 'PAUSED'
            ? html`<button type="button" class="btn btn-primary" disabled=${busy} onClick=${() => actions.resume(trigger)}><${Icon} name="play"/>Resume</button>`
            : html`<button type="button" class="btn" disabled=${busy} onClick=${() => actions.pause(trigger)}><${Icon} name="pause"/>Pause</button>`}
        <button type="button" class="btn" disabled=${busy} onClick=${() => actions.openRun(trigger)}><${Icon} name="zap"/>Run now</button>
        <button type="button" class="btn btn-danger-ghost" disabled=${busy} onClick=${() => setRemoving('confirm')}><${Icon} name="trash-2"/>Remove</button>`;

    return html`${header(state, buttons)}
    <div class="grid grid-3">
        <${Card} title="Schedule" icon="calendar-clock">
            <${KeyValues} rows=${[
                ...schedule(trigger),
                ['Calendar', trigger.calendarName === NO_RESTRICTIONS_CALENDAR ? 'No restrictions' : trigger.calendarName],
                ['Priority', trigger.priority],
                ['Description', trigger.description]
            ]}/>
        <//>
        <${Card} title="Fire times" icon="history">
            <${KeyValues} rows=${[
                ['Previous fire', html`<${Time} value=${trigger.previousFireTime}/>`],
                ['Start time', html`<${Time} value=${trigger.startTime}/>`],
                ['End time', html`<${Time} value=${trigger.endTime}/>`]
            ]}/>
            <h3 class="sub-title">Next fire times</h3>
            <${NextFireTimes} groupName=${trigger.group} triggerName=${trigger.name} paused=${state === 'PAUSED'}/>
        <//>
        <${Card} title="Job" icon="package">
            <${KeyValues} rows=${[
                ['Job', html`<a href=${href('/jobs', {groupName: trigger.jobGroup, jobName: trigger.jobName})}>${trigger.jobName}</a>`],
                ['Job group', trigger.jobGroup]
            ]}/>
            <div class="card-links">
                <a href=${href('/logs', {groupName: trigger.jobGroup, triggerName: trigger.name})}><${Icon} name="scroll-text"/>Execution logs</a>
            </div>
        <//>
    </div>
    <${DurationTrend} trigger=${trigger}/>
    ${actions.dialog}
    <${ConfirmDialog} open=${removing !== null} title="Remove trigger" confirmLabel="Remove" busy=${removing === 'busy'}
        onConfirm=${remove} onClose=${() => setRemoving(null)}>
        <p>Remove trigger <b>${trigger.group}.${trigger.name}</b> from the schedule? Its job stops running on this
            trigger. This cannot be undone here: the trigger comes back only when the application registers it again.</p>
    <//>`;
}
