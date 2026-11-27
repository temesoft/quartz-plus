// Charts: Chart.js (loaded as a plain script, window.Chart) drawn in the colours of the current theme.
import {html, useEffect, useRef, useState} from './lib.js';

// the theme's colours, read from the CSS variables of style.css
export function themeColors() {
    const style = getComputedStyle(document.documentElement);
    const value = (name) => style.getPropertyValue(name).trim();
    return {
        text: value('--text'),
        muted: value('--muted'),
        border: value('--border'),
        surface: value('--surface'),
        accent: value('--accent'),
        ok: value('--ok'),
        fail: value('--fail'),
        warn: value('--warn'),
        info: value('--info'),
        font: getComputedStyle(document.body).fontFamily
    };
}

// options every chart shares: no animation on refresh, theme colours on axes, legend and tooltip
export function baseOptions(colors) {
    return {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        font: {family: colors.font},
        plugins: {
            legend: {
                position: 'top',
                align: 'end',
                labels: {color: colors.muted, boxWidth: 10, boxHeight: 10, font: {family: colors.font}}
            },
            tooltip: {
                backgroundColor: colors.surface,
                titleColor: colors.text,
                bodyColor: colors.text,
                borderColor: colors.border,
                borderWidth: 1,
                titleFont: {family: colors.font},
                bodyFont: {family: colors.font}
            }
        },
        scales: {
            x: {
                grid: {display: false},
                border: {color: colors.border},
                ticks: {color: colors.muted, maxRotation: 0, autoSkip: true, maxTicksLimit: 8, font: {family: colors.font}}
            },
            y: {
                beginAtZero: true,
                grid: {color: colors.border},
                border: {display: false},
                ticks: {color: colors.muted, font: {family: colors.font}}
            }
        }
    };
}

/**
 * A chart built by make(data, colors), which returns a Chart.js configuration. It is drawn again when data changes,
 * and built anew when the theme changes, so its colours follow the theme.
 */
export function ChartView({make, data, height = 240, label}) {
    const canvas = useRef(null);
    const chart = useRef(null);
    const [theme, setTheme] = useState(0);

    useEffect(() => {
        const onTheme = () => setTheme((t) => t + 1);
        document.addEventListener('quartz-plus:theme', onTheme);
        return () => {
            document.removeEventListener('quartz-plus:theme', onTheme);
            if (chart.current) {
                chart.current.destroy();
            }
        };
    }, []);

    useEffect(() => {
        if (!window.Chart || !data) {
            return;
        }
        if (chart.current) {
            chart.current.destroy();
        }
        chart.current = new window.Chart(canvas.current, make(data, themeColors()));
    }, [data, theme]);

    if (!window.Chart) {
        return html`<div class="state-msg">Charts are not available: the chart library did not load.</div>`;
    }
    return html`<div class="chart" style=${'height: ' + height + 'px'}>
        <canvas ref=${canvas} role="img" aria-label=${label}></canvas>
    </div>`;
}
