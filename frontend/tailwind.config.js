/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      // Themeable palette. Values resolve to CSS variables so switching `data-theme`
      // on <html> recolors the whole app. Only the shades actually used in the codebase
      // are overridden; the rest keep their default Tailwind values.
      //
      // The neutral (`slate`) and `white` tokens are inverted for the light theme so
      // text and subtle overlays (`bg-white/5`, `border-white/10`, `text-slate-400`)
      // stay readable on light surfaces without touching individual components.
      colors: {
        // surfaces
        canvas: 'rgb(var(--c-canvas) / <alpha-value>)',
        'canvas-2': 'rgb(var(--c-canvas-2) / <alpha-value>)',
        panel: 'rgb(var(--c-panel) / <alpha-value>)',
        line: 'rgb(var(--c-line) / <alpha-value>)',
        // control knob (switch handle) that must stay light on the accent track in every theme
        knob: 'rgb(var(--c-knob) / <alpha-value>)',
        // neutral text + subtle overlays (dark themes: light on dark; light theme: dark on light)
        white: 'rgb(var(--c-white) / <alpha-value>)',
        slate: {
          100: 'rgb(var(--c-slate-100) / <alpha-value>)',
          200: 'rgb(var(--c-slate-200) / <alpha-value>)',
          300: 'rgb(var(--c-slate-300) / <alpha-value>)',
          400: 'rgb(var(--c-slate-400) / <alpha-value>)',
          500: 'rgb(var(--c-slate-500) / <alpha-value>)',
          600: 'rgb(var(--c-slate-600) / <alpha-value>)',
        },
        // primary accent (cyan) and secondary accents (violet / lime)
        cyan: {
          200: 'rgb(var(--c-accent-200) / <alpha-value>)',
          300: 'rgb(var(--c-accent-300) / <alpha-value>)',
          400: 'rgb(var(--c-accent-400) / <alpha-value>)',
          500: 'rgb(var(--c-accent-500) / <alpha-value>)',
        },
        violet: {
          300: 'rgb(var(--c-accent-2-300) / <alpha-value>)',
          400: 'rgb(var(--c-accent-2-400) / <alpha-value>)',
          500: 'rgb(var(--c-accent-2-500) / <alpha-value>)',
          600: 'rgb(var(--c-accent-2-600) / <alpha-value>)',
        },
        lime: {
          300: 'rgb(var(--c-accent-3-300) / <alpha-value>)',
          400: 'rgb(var(--c-accent-3-400) / <alpha-value>)',
        },
        // status colors (light shades darkened for contrast on light surfaces)
        red: {
          100: 'rgb(var(--c-red-100) / <alpha-value>)',
          200: 'rgb(var(--c-red-200) / <alpha-value>)',
          300: 'rgb(var(--c-red-300) / <alpha-value>)',
          400: 'rgb(var(--c-red-400) / <alpha-value>)',
          500: 'rgb(var(--c-red-500) / <alpha-value>)',
          600: 'rgb(var(--c-red-600) / <alpha-value>)',
          700: 'rgb(var(--c-red-700) / <alpha-value>)',
        },
        amber: {
          200: 'rgb(var(--c-amber-200) / <alpha-value>)',
          300: 'rgb(var(--c-amber-300) / <alpha-value>)',
          400: 'rgb(var(--c-amber-400) / <alpha-value>)',
          500: 'rgb(var(--c-amber-500) / <alpha-value>)',
          600: 'rgb(var(--c-amber-600) / <alpha-value>)',
          700: 'rgb(var(--c-amber-700) / <alpha-value>)',
        },
        emerald: {
          200: 'rgb(var(--c-emerald-200) / <alpha-value>)',
          300: 'rgb(var(--c-emerald-300) / <alpha-value>)',
          400: 'rgb(var(--c-emerald-400) / <alpha-value>)',
          500: 'rgb(var(--c-emerald-500) / <alpha-value>)',
          600: 'rgb(var(--c-emerald-600) / <alpha-value>)',
          700: 'rgb(var(--c-emerald-700) / <alpha-value>)',
        },
        rose: {
          400: 'rgb(var(--c-rose-400) / <alpha-value>)',
          500: 'rgb(var(--c-rose-500) / <alpha-value>)',
          600: 'rgb(var(--c-rose-600) / <alpha-value>)',
        },
      },
    },
  },
  plugins: [],
}
