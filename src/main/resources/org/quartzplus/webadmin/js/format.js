// Formatting of dates, durations and numbers. Times are shown in the browser's time zone with a 24-hour clock.

const relativeTime = new Intl.RelativeTimeFormat('en', {numeric: 'auto'});
const RELATIVE_UNITS = [
    [60, 'second', 1],
    [3600, 'minute', 60],
    [86400, 'hour', 3600],
    [86400 * 30, 'day', 86400],
    [86400 * 365, 'month', 86400 * 30],
    [Infinity, 'year', 86400 * 365]
];

const pad = (value) => String(value).padStart(2, '0');

export function toDate(value) {
    if (value === undefined || value === null || value === '') {
        return null;
    }
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value) {
    const d = toDate(value);
    if (!d) {
        return '';
    }
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
        + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
}

export function fromNow(value, now = Date.now()) {
    const d = toDate(value);
    if (!d) {
        return '';
    }
    const seconds = (d.getTime() - now) / 1000;
    const unit = RELATIVE_UNITS.find(([limit]) => Math.abs(seconds) < limit);
    return relativeTime.format(Math.round(seconds / unit[2]), unit[1]);
}

export function formatNumber(value, fractionDigits = 0) {
    if (value === undefined || value === null || isNaN(value)) {
        return '';
    }
    return new Intl.NumberFormat('en', {maximumFractionDigits: fractionDigits}).format(value);
}

export function formatDuration(ms) {
    if (ms === undefined || ms === null || isNaN(ms)) {
        return '';
    }
    if (ms < 1000) {
        return formatNumber(ms) + ' ms';
    }
    if (ms < 60000) {
        return formatNumber(ms / 1000, 1) + ' s';
    }
    const minutes = Math.floor(ms / 60000);
    return minutes + ' min ' + Math.round((ms % 60000) / 1000) + ' s';
}

export function formatSeconds(seconds) {
    if (seconds < 60) {
        return seconds + ' s';
    }
    if (seconds < 3600) {
        return Math.floor(seconds / 60) + ' min';
    }
    return Math.floor(seconds / 3600) + ' h';
}

export function formatBytes(bytes) {
    if (bytes < 1024) {
        return formatNumber(bytes) + ' B';
    }
    if (bytes < 1024 * 1024) {
        return formatNumber(bytes / 1024, 1) + ' KB';
    }
    return formatNumber(bytes / 1024 / 1024, 1) + ' MB';
}

export function shorten(text, size) {
    if (text === undefined || text === null) {
        return '';
    }
    return text.length > size ? text.substring(0, size).trim() + '…' : text;
}
