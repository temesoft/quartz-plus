'use strict';

// Loaded in <head> as a plain script, so the colour theme is set before the page is painted: a dark page never
// flashes light. The mode is "device" (follow the operating system, the default), "light" or "dark"; the choice is
// kept in localStorage under the key 1.0.4 used for "light" and "dark". The UI changes the mode through
// window.quartzPlusTheme; each change, and each change of the device's setting while in "device" mode, fires a
// "quartz-plus:theme" event on the document.
(function () {
    var storageKey = 'quartz-plus.theme';
    var media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    var mode = null;

    try {
        mode = window.localStorage.getItem(storageKey);
    } catch (e) {
        // storage blocked: the default
    }
    if (mode !== 'light' && mode !== 'dark') {
        mode = 'device';
    }

    function theme() {
        if (mode === 'device') {
            return media && media.matches ? 'dark' : 'light';
        }
        return mode;
    }

    function apply() {
        document.documentElement.setAttribute('data-theme', theme());
        document.dispatchEvent(new CustomEvent('quartz-plus:theme', {detail: {mode: mode, theme: theme()}}));
    }

    if (media && media.addEventListener) {
        media.addEventListener('change', function () {
            if (mode === 'device') {
                apply();
            }
        });
    }

    window.quartzPlusTheme = {
        mode: function () {
            return mode;
        },
        theme: theme,
        set: function (value) {
            mode = value;
            try {
                window.localStorage.setItem(storageKey, value);
            } catch (e) {
                // storage blocked: the choice lasts until the page is reloaded
            }
            apply();
        }
    };

    apply();
})();
