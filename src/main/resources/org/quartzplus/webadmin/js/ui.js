// Shared building blocks of the pages: icons, badges, cards, dialogs, toasts and paging.
import {html, useEffect, useRef, useState} from './lib.js';
import {formatDateTime, fromNow} from './format.js';

const ICON_SPRITE = 'vendor/lucide/icons.svg';

// the current time, renewed every everyMs, for countdowns and "updated ... ago"
export function useNow(everyMs) {
    const [now, setNow] = useState(Date.now());
    useEffect(() => {
        const timer = setInterval(() => setNow(Date.now()), everyMs);
        return () => clearInterval(timer);
    }, [everyMs]);
    return now;
}

export function Icon({name, size = 16}) {
    return html`<svg class="icon" width=${size} height=${size} aria-hidden="true" focusable="false">
        <use href=${ICON_SPRITE + '#' + name}></use></svg>`;
}

const TRIGGER_STATES = {
    NORMAL: ['normal', 'Normal'],
    PAUSED: ['paused', 'Paused'],
    BLOCKED: ['blocked', 'Blocked'],
    ERROR: ['error', 'Error'],
    COMPLETE: ['complete', 'Complete'],
    NONE: ['none', 'None']
};

export function StateBadge({state}) {
    const [kind, label] = TRIGGER_STATES[state] || ['none', state || 'Unknown'];
    return html`<span class=${'badge badge-' + kind}>${label}</span>`;
}

export function Badge({kind, children}) {
    return html`<span class=${'badge badge-' + kind}>${children}</span>`;
}

export function ResultBadge({success}) {
    return success ? html`<${Badge} kind="ok">Success<//>` : html`<${Badge} kind="fail">Failure<//>`;
}

// a point in time, with how long ago or ahead it is underneath
export function Time({value}) {
    const text = formatDateTime(value);
    if (!text) {
        return html`<span class="muted">—</span>`;
    }
    return html`<span class="time"><span class="nowrap">${text}</span><span class="time-rel">${fromNow(value)}</span></span>`;
}

export function PageHeader({icon, title, subtitle, badge, children}) {
    return html`<header class="page-header">
        <div class="page-title">
            <h1>${icon && html`<${Icon} name=${icon} size=${22}/>`}<span>${title}</span>${badge}</h1>
            ${subtitle && html`<p class="muted">${subtitle}</p>`}
        </div>
        ${children && html`<div class="page-actions">${children}</div>`}
    </header>`;
}

export function Card({title, icon, className = '', children}) {
    return html`<section class=${'card ' + className}>
        ${title && html`<h2 class="card-title">${icon && html`<${Icon} name=${icon}/>`}${title}</h2>`}
        ${children}
    </section>`;
}

// label / value pairs; rows whose value is undefined are left out
export function KeyValues({rows}) {
    return html`<dl class="kv">${rows.filter((row) => row[1] !== undefined).map(([label, value]) => html`
        <div class="kv-row"><dt>${label}</dt><dd>${value === null || value === '' ? html`<span class="muted">—</span>` : value}</dd></div>`)}
    </dl>`;
}

// shown while a page has no data yet: a spinner, or why the data could not be loaded; notFound is shown when the
// API answers that what the page shows does not exist (404, or 400 for an execution log ID it does not know)
export function Pending({error, onRetry, notFound}) {
    if (error && (error.status === 404 || error.status === 400) && notFound) {
        return html`<div class="state-msg"><${Icon} name="circle-alert" size=${20}/><span>${notFound}</span></div>`;
    }
    if (error) {
        return html`<div class="state-msg state-error"><${Icon} name="circle-alert" size=${20}/>
            <span>Could not load this page: ${error.message}</span>
            ${onRetry && html`<button type="button" class="btn btn-sm" onClick=${onRetry}>Try again</button>`}</div>`;
    }
    return html`<div class="state-msg"><span class="spinner" aria-hidden="true"></span>Loading…</div>`;
}

export function Empty({children}) {
    return html`<div class="state-msg">${children}</div>`;
}

const FILTER_LABELS = {groupName: 'Group', jobName: 'Job', triggerName: 'Trigger', instanceClass: 'Class'};

// names the filters a page was opened with, e.g. from a link on another page, and offers to drop them
export function FilterNote({filters, clearHref}) {
    const entries = Object.entries(filters);
    if (!entries.length) {
        return null;
    }
    return html`<div class="filter-note">
        <${Icon} name="search"/><span>Showing only</span>
        ${entries.map(([name, value]) => html`<span class="chip" key=${name}><span class="muted">${FILTER_LABELS[name] || name}</span> ${value}</span>`)}
        <a href=${clearHref}>Show all</a>
    </div>`;
}

// copies text; the clipboard API needs a secure context (HTTPS or localhost), elsewhere a selection is copied
export async function copyText(text) {
    try {
        await navigator.clipboard.writeText(text);
    } catch (e) {
        const area = document.createElement('textarea');
        area.value = text;
        area.setAttribute('readonly', '');
        area.className = 'offscreen';
        document.body.appendChild(area);
        area.select();
        const copied = document.execCommand('copy');
        area.remove();
        if (!copied) {
            toast('error', 'Not copied', 'The browser does not allow copying here: select the text and copy it');
            return;
        }
    }
    toast('success', '', 'Copied to the clipboard');
}

// preformatted text with buttons to copy it and to wrap long lines or scroll them
export function CodeBlock({text, emptyText}) {
    const [wrap, setWrap] = useState(true);
    if (!text) {
        return html`<p class="muted">${emptyText}</p>`;
    }
    return html`<div class="code-block">
        <div class="code-tools">
            <button type="button" class=${'btn btn-sm' + (wrap ? ' active' : '')} aria-pressed=${wrap} onClick=${() => setWrap(!wrap)}>
                <${Icon} name="wrap-text"/>Wrap lines</button>
            <button type="button" class="btn btn-sm" onClick=${() => copyText(text)}><${Icon} name="copy"/>Copy</button>
        </div>
        <pre class=${'code' + (wrap ? '' : ' code-nowrap')}>${text}</pre>
    </div>`;
}

export function Table({children, className = ''}) {
    return html`<div class="table-wrap"><table class=${'table ' + className}>${children}</table></div>`;
}

/**
 * A modal dialog on the native <dialog> element: Escape and a click on the backdrop close it.
 */
export function Dialog({open, title, onClose, children, footer}) {
    const ref = useRef(null);
    useEffect(() => {
        const dialog = ref.current;
        if (open && !dialog.open) {
            dialog.showModal();
        } else if (!open && dialog.open) {
            dialog.close();
        }
    }, [open]);
    const onClick = (e) => {
        if (e.target === ref.current) {
            onClose();
        }
    };
    return html`<dialog ref=${ref} class="dialog" onClose=${onClose} onClick=${onClick} aria-label=${title}>
        ${open && html`<div class="dialog-box">
            <header class="dialog-head"><h2>${title}</h2>
                <button type="button" class="icon-btn" aria-label="Close" onClick=${onClose}><${Icon} name="x"/></button>
            </header>
            <div class="dialog-body">${children}</div>
            <footer class="dialog-foot">${footer}</footer>
        </div>`}
    </dialog>`;
}

export function ConfirmDialog({open, title, confirmLabel, busy, onConfirm, onClose, children}) {
    return html`<${Dialog} open=${open} title=${title} onClose=${onClose} footer=${html`
        <button type="button" class="btn" onClick=${onClose} autofocus>Cancel</button>
        <button type="button" class="btn btn-danger" disabled=${busy} onClick=${onConfirm}>
            <${Icon} name="trash-2"/>${confirmLabel}</button>`}>${children}<//>`;
}

/**
 * Page numbers around the current page, with the first and last always shown; null marks a gap.
 */
export function pageNumbers(current, total) {
    const wanted = new Set([0, total - 1, current - 2, current - 1, current, current + 1, current + 2]);
    const pages = [...wanted].filter((n) => n >= 0 && n < total).sort((a, b) => a - b);
    const result = [];
    pages.forEach((n, i) => {
        if (i > 0 && n - pages[i - 1] > 1) {
            result.push(null);
        }
        result.push(n);
    });
    return result;
}

// page is zero-based, as the API counts it
export function Pagination({page, totalPages, onPage}) {
    if (totalPages <= 1) {
        return null;
    }
    return html`<nav class="pagination" aria-label="Pages">
        <button type="button" class="btn btn-sm" disabled=${page === 0} onClick=${() => onPage(page - 1)} aria-label="Previous page">
            <${Icon} name="chevron-left"/></button>
        ${pageNumbers(page, totalPages).map((n, i) => n === null
            ? html`<span class="pagination-gap" key=${'gap' + i}>…</span>`
            : html`<button type="button" key=${n} class=${'btn btn-sm' + (n === page ? ' active' : '')}
                    aria-current=${n === page ? 'page' : undefined} onClick=${() => onPage(n)}>${n + 1}</button>`)}
        <button type="button" class="btn btn-sm" disabled=${page >= totalPages - 1} onClick=${() => onPage(page + 1)} aria-label="Next page">
            <${Icon} name="chevron-right"/></button>
    </nav>`;
}

// ---------------------------------------------------------------- toasts

let toasts = [];
let nextToastId = 1;
const toastListeners = new Set();

function emitToasts() {
    toastListeners.forEach((listener) => listener(toasts));
}

function dismissToast(id) {
    toasts = toasts.filter((t) => t.id !== id);
    emitToasts();
}

// the same message is not stacked while it is still shown (a failing auto refresh would repeat it)
export function toast(kind, title, text) {
    if (toasts.some((t) => t.kind === kind && t.title === title && t.text === text)) {
        return;
    }
    const id = nextToastId++;
    toasts = [...toasts, {id, kind, title, text}];
    emitToasts();
    setTimeout(() => dismissToast(id), kind === 'error' ? 8000 : 4000);
}

export function toastError(error) {
    toast('error', error.title || 'Error', error.message || String(error));
}

export function Toasts() {
    const [list, setList] = useState(toasts);
    useEffect(() => {
        toastListeners.add(setList);
        return () => toastListeners.delete(setList);
    }, []);
    return html`<div class="toasts" role="status" aria-live="polite">
        ${list.map((t) => html`<div class=${'toast toast-' + t.kind} key=${t.id}>
            <${Icon} name=${t.kind === 'error' ? 'circle-alert' : 'circle-check'} size=${18}/>
            <div class="toast-text">${t.title && html`<b>${t.title}</b>`}<span>${t.text}</span></div>
            <button type="button" class="icon-btn" aria-label="Dismiss" onClick=${() => dismissToast(t.id)}><${Icon} name="x"/></button>
        </div>`)}
    </div>`;
}
