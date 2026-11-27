// Pause, resume and run-now for a trigger, shared by the trigger list and the trigger details page.
import {html, useEffect, useRef, useState} from './lib.js';
import {api} from './api.js';
import {Dialog, Icon, KeyValues, toast, toastError} from './ui.js';

const keyOf = (trigger) => trigger.group + '.' + trigger.name;
const keyParams = (trigger) => ({groupName: trigger.group, triggerName: trigger.name});

// the job data is sent as a JSON object, merged into the job's own data for this run only
function ExecuteDialog({trigger, busy, onRun, onClose}) {
    const [text, setText] = useState('{}');
    const [problem, setProblem] = useState(null);
    const textarea = useRef(null);
    // selected on opening, so typing replaces the data of the last run
    useEffect(() => {
        if (trigger && textarea.current) {
            textarea.current.select();
        }
    }, [trigger]);
    const run = () => {
        let value;
        try {
            value = JSON.parse(text);
        } catch (e) {
            setProblem('Not valid JSON: ' + e.message);
            return;
        }
        if (value === null || typeof value !== 'object' || Array.isArray(value)) {
            setProblem('Job data must be a JSON object, e.g. {"key": "value"}');
            return;
        }
        setProblem(null);
        onRun(trigger, text);
    };
    return html`<${Dialog} open=${!!trigger} title="Run job now" onClose=${onClose} footer=${html`
        <button type="button" class="btn" onClick=${onClose}>Cancel</button>
        <button type="button" class="btn btn-primary" disabled=${busy} onClick=${run}><${Icon} name="zap"/>Run now</button>`}>
        ${trigger && html`<${KeyValues} rows=${[['Group', trigger.group], ['Job', trigger.jobName], ['Trigger', trigger.name]]}/>`}
        <label class="field">
            <span>Job data (JSON)</span>
            <textarea class="mono" rows="6" spellcheck="false" value=${text} autofocus ref=${textarea}
                onInput=${(e) => setText(e.currentTarget.value)}></textarea>
        </label>
        ${problem && html`<p class="field-error">${problem}</p>`}
    <//>`;
}

/**
 * Actions on triggers; after each one succeeds, onDone() runs (e.g. to reload the page). busy(trigger) tells
 * whether an action on that trigger is under way; dialog must be rendered for "run now".
 */
export function useTriggerActions(onDone) {
    const [busyKey, setBusyKey] = useState(null);
    const [runFor, setRunFor] = useState(null);

    const act = async (trigger, call, message) => {
        setBusyKey(keyOf(trigger));
        try {
            await call();
            toast('success', '', message);
            onDone();
            return true;
        } catch (e) {
            toastError(e);
            return false;
        } finally {
            setBusyKey(null);
        }
    };

    const pause = (trigger) => act(trigger, () => api.post('/trigger/pause', keyParams(trigger)),
        'Trigger "' + keyOf(trigger) + '" is paused');
    const resume = (trigger) => act(trigger, () => api.post('/trigger/resume', keyParams(trigger)),
        'Trigger "' + keyOf(trigger) + '" is resumed');
    const run = async (trigger, jobData) => {
        const done = await act(trigger, () => api.post('/trigger/execute', {...keyParams(trigger), jobName: trigger.jobName}, jobData),
            'Job "' + trigger.group + '.' + trigger.jobName + '" was sent for immediate execution');
        if (done) {
            setRunFor(null);
        }
    };

    return {
        busy: (trigger) => busyKey === keyOf(trigger),
        pause,
        resume,
        openRun: setRunFor,
        dialog: html`<${ExecuteDialog} trigger=${runFor} busy=${runFor && busyKey === keyOf(runFor)} onRun=${run}
            onClose=${() => setRunFor(null)}/>`
    };
}
