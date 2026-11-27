// Hash routing: "#/path?name=value". The page addresses of earlier versions (#/logs?groupName=..., #/trigger/g/t)
// keep working, so bookmarks and links into the UI stay valid.
import {useEffect, useState} from './lib.js';

export function parseHash(hash = window.location.hash) {
    let text = hash.replace(/^#/, '');
    if (!text.startsWith('/')) {
        text = '/' + text;
    }
    const q = text.indexOf('?');
    const path = q < 0 ? text : text.substring(0, q);
    const query = Object.fromEntries(new URLSearchParams(q < 0 ? '' : text.substring(q + 1)));
    return {path, query, key: text};
}

export function useRoute() {
    const [route, setRoute] = useState(parseHash());
    useEffect(() => {
        const onChange = () => setRoute(parseHash());
        window.addEventListener('hashchange', onChange);
        return () => window.removeEventListener('hashchange', onChange);
    }, []);
    return route;
}

// a link into the UI; path segments given as values are encoded
export function href(path, query) {
    const search = new URLSearchParams();
    Object.entries(query || {}).forEach(([name, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            search.append(name, String(value));
        }
    });
    const text = search.toString();
    return '#' + path + (text ? '?' + text : '');
}

export function triggerHref(groupName, triggerName) {
    return '#/trigger/' + encodeURIComponent(groupName) + '/' + encodeURIComponent(triggerName);
}

export function navigate(target) {
    window.location.hash = target.replace(/^#/, '');
}

// finds the route whose pattern ("/trigger/:groupName/:triggerName") matches the path
export function matchRoute(routes, path) {
    for (const route of routes) {
        const names = [];
        const pattern = new RegExp('^' + route.path.replace(/:([A-Za-z]+)/g, (all, name) => {
            names.push(name);
            return '([^/]+)';
        }) + '$');
        const match = path.match(pattern);
        if (match) {
            const params = {};
            names.forEach((name, i) => {
                params[name] = decodeURIComponent(match[i + 1]);
            });
            return {route, params};
        }
    }
    return null;
}

// only the named entries of a query, for passing route filters on to the API
export function pick(query, names) {
    const result = {};
    names.forEach((name) => {
        if (query[name]) {
            result[name] = query[name];
        }
    });
    return result;
}
