'use strict';

// Loaded in <head> after the stylesheets, so the stored (or the system's) colour theme is applied before the page is
// painted: a dark page never flashes light. Both theme stylesheets are linked; the one not in use is disabled.

var __themeStorageKey = 'quartz-plus.theme';
var __currentTheme = null;

// preferences live in the browser's localStorage; a browser that blocks it simply gets the defaults
function __storedPreference(key) {
    try {
        return window.localStorage.getItem(key);
    } catch (e) {
        return null;
    }
}

function __storePreference(key, value) {
    try {
        window.localStorage.setItem(key, value);
    } catch (e) {
        // storage blocked: the choice lasts until the page is reloaded
    }
}

function __applyTheme(theme) {
    document.getElementById('themeLightStylesheet').disabled = theme === 'dark';
    document.getElementById('themeDarkStylesheet').disabled = theme !== 'dark';
    document.documentElement.setAttribute('data-theme', theme);
    __currentTheme = theme;
}

(function () {
    var theme = __storedPreference(__themeStorageKey);
    if (theme !== 'dark' && theme !== 'light') {
        theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    __applyTheme(theme);
})();
