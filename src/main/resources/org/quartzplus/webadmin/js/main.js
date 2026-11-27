// Entry point: the API's address is needed by every page, so the UI starts only once it has loaded.
import {html, render} from './lib.js';
import {loadApiUri} from './api.js';
import {App} from './app.js';

const root = document.getElementById('app');

loadApiUri().then(() => {
    root.textContent = '';
    render(html`<${App}/>`, root);
}, (error) => {
    root.textContent = '';
    render(html`<div class="boot boot-error"><b>The administration UI could not start.</b>
        <span>${error.message}${error.status ? ' (HTTP ' + error.status + ')' : ''}</span></div>`, root);
});
