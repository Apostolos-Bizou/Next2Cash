# Next2Cash — Frontend

Vue.js 3 + Vite single-page app for the Next2me Group Cash Control platform.

## Stack

- [Vue 3](https://vuejs.org/) (Composition API, `<script setup>`)
- [Vite](https://vite.dev/) — dev server and build tool
- [Vue Router 4](https://router.vuejs.org/) — client-side routing
- [Pinia 2](https://pinia.vuejs.org/) — state management

## Project structure

```
frontend/
├── index.html
├── vite.config.js
├── package.json
└── src/
    ├── main.js            # app entry — installs Pinia + Router
    ├── App.vue            # root layout with sidebar + topbar
    ├── assets/
    │   └── main.css       # global styles + theme variables
    ├── components/
    │   └── PagePlaceholder.vue
    ├── router/
    │   └── index.js       # routes for all 7 pages
    ├── stores/
    │   ├── ui.js          # sidebar collapse state
    │   └── user.js        # auth / profile placeholder
    └── views/
        ├── DashboardView.vue
        ├── TransactionsView.vue
        ├── PaymentsView.vue
        ├── ObligationsView.vue
        ├── DocumentsView.vue
        ├── ReportsView.vue
        └── AdminView.vue
```

## Theme

Dark navy primary (`#162B40`) used for the sidebar and brand accents. The
full palette lives as CSS custom properties in `src/assets/main.css`.

## Getting started

```bash
cd frontend
npm install
npm run dev
```

Then open http://localhost:5173.

## Scripts

| Command            | Description                        |
| ------------------ | ---------------------------------- |
| `npm run dev`      | Start Vite dev server              |
| `npm run build`    | Build for production into `dist/`  |
| `npm run preview`  | Preview the production build       |
