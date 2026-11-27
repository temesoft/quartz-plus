// Loading page data, and reloading it when the refresh control says so.
import {createContext, useCallback, useContext, useEffect, useRef, useState} from './lib.js';
import {toastError} from './ui.js';

const REFRESH_STORAGE_KEY = 'quartz-plus.auto-refresh';
export const REFRESH_INTERVALS = [0, 5, 10, 30, 60];
const DEFAULT_REFRESH_INTERVAL = 10;

// tick: increased by automatic refresh; manual: increased by "refresh now"; reportLoaded: called after each load;
// info: the scheduler's metadata (GET {api-uri}), null until it has loaded
export const AppContext = createContext({tick: 0, manual: 0, reportLoaded: () => undefined, info: null});

// seconds between automatic refreshes, 0 for none; 1.0.4 stored "on" or "off"
export function readRefreshInterval() {
    let value = null;
    try {
        value = window.localStorage.getItem(REFRESH_STORAGE_KEY);
    } catch (e) {
        // storage blocked: the default
    }
    if (value === 'off') {
        return 0;
    }
    const seconds = Number(value);
    return value !== null && REFRESH_INTERVALS.includes(seconds) ? seconds : DEFAULT_REFRESH_INTERVAL;
}

export function storeRefreshInterval(seconds) {
    try {
        window.localStorage.setItem(REFRESH_STORAGE_KEY, String(seconds));
    } catch (e) {
        // storage blocked: the choice lasts until the page is reloaded
    }
}

/**
 * Loads data with load() when the component mounts and whenever deps change, and again on "refresh now"; with
 * refresh, also on every automatic refresh. The last data stays shown while a reload runs or after it fails;
 * failures are reported as toasts. Only the answer of the latest load is kept.
 */
export function useApi(load, deps, {refresh = false} = {}) {
    const {tick, manual, reportLoaded} = useContext(AppContext);
    const [state, setState] = useState({data: null, error: null});
    const latest = useRef(0);
    const seen = useRef({tick, manual});

    const reload = useCallback(() => {
        const call = ++latest.current;
        load().then((data) => {
            if (call === latest.current) {
                setState({data, error: null});
                reportLoaded();
            }
        }, (error) => {
            if (call === latest.current) {
                setState((previous) => ({data: previous.data, error}));
                toastError(error);
            }
        });
    }, deps);

    useEffect(() => {
        reload();
    }, [reload]);

    useEffect(() => {
        const previous = seen.current;
        seen.current = {tick, manual};
        if (manual !== previous.manual || (refresh && tick !== previous.tick)) {
            reload();
        }
    }, [tick, manual]);

    // answers that arrive after the page was left are dropped
    useEffect(() => () => {
        latest.current = -1;
    }, []);

    return {data: state.data, error: state.error, reload};
}
