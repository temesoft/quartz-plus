// The one place the UI imports Preact and htm from: every other module imports from here.
import {createContext, h, render} from '../vendor/preact/preact.js';
import {useCallback, useContext, useEffect, useMemo, useRef, useState} from '../vendor/preact/hooks.js';
import htm from '../vendor/htm/htm.js';

// html`...` templates are parsed by htm at run time (no eval), and Preact escapes every value it renders as text
export const html = htm.bind(h);

export {createContext, render, useCallback, useContext, useEffect, useMemo, useRef, useState};
