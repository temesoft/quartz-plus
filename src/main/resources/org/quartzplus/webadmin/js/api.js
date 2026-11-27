// Client for the scheduler REST API. The API's address is configurable on the server, so it is read from the web
// admin filter before the first request; requests that change something carry the host's CSRF token, which the
// server renders into index.html as meta tags when Spring Security is present.

const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
let apiUri = null;

export class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
        this.title = status ? 'Request failed (' + status + ')' : 'Network error';
    }
}

export async function loadApiUri() {
    const response = await fetch('org/quartzplus/api', {credentials: 'same-origin'});
    if (!response.ok) {
        throw new ApiError(response.status, 'Unable to load the scheduler API address');
    }
    apiUri = (await response.text()).trim();
}

export function queryString(params) {
    const search = new URLSearchParams();
    Object.entries(params || {}).forEach(([name, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            search.append(name, String(value));
        }
    });
    const text = search.toString();
    return text ? '?' + text : '';
}

async function request(method, path, params, body) {
    const headers = {'Accept': 'application/json'};
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (method !== 'GET' && csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }
    let response;
    try {
        response = await fetch(apiUri + path + queryString(params), {method, headers, body, credentials: 'same-origin'});
    } catch (e) {
        throw new ApiError(0, 'The server could not be reached');
    }
    const text = await response.text();
    if (!response.ok) {
        throw new ApiError(response.status, errorMessage(text, response));
    }
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch (e) {
        return text;
    }
}

// the message of a JSON error body (Spring's error body names only the status when messages are not included),
// else the body itself, cut short: it may be a whole HTML error page
function errorMessage(text, response) {
    try {
        const json = JSON.parse(text);
        const message = json && (json.message || json.detail || json.error);
        if (message) {
            return String(message);
        }
    } catch (e) {
        // not JSON
    }
    const plain = (text || '').trim();
    if (!plain) {
        return response.statusText || 'No details were sent';
    }
    return plain.length > 300 ? plain.substring(0, 300) + '…' : plain;
}

export const api = {
    get: (path, params) => request('GET', path, params),
    post: (path, params, body) => request('POST', path, params, body),
    del: (path, params) => request('DELETE', path, params)
};
