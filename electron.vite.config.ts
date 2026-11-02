import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'electron-vite';

import { fileURLToPath } from 'node:url';

const devPort = 5173;
const src = (path: string) => fileURLToPath(new URL(`src/${path}`, import.meta.url));

export default defineConfig({
  main: {
    build: {
      // Everything is bundled into out/main except `dependencies` (only @acidify/core), which ships in app.asar:
      // its fflate declares a top-level `require` that collides with the bundle's own.
      rollupOptions: {
        // The account process is a utilityProcess entry next to the main process.
        input: { index: src('main/index.ts'), account: src('account/index.ts') },
      },
    },
  },
  preload: {
    build: {
      rollupOptions: {
        input: { index: src('preload/index.ts') },
        // Sandboxed preloads must be CommonJS.
        output: { format: 'cjs', entryFileNames: '[name].cjs' },
      },
    },
  },
  renderer: {
    root: 'src/renderer',
    resolve: { alias: { '@': src('renderer') } },
    plugins: [react(), tailwindcss()],
    build: {
      minify: true,
      // Loaded from local disk, so one larger chunk costs nothing over the network.
      chunkSizeWarningLimit: 2048,
      rollupOptions: { input: { index: src('renderer/index.html') } },
    },
    // In development the main process proxies app://cecilia/* to this server, so the page origin is app://cecilia
    // and the HMR client cannot infer the socket address from it.
    server: {
      host: 'localhost',
      port: devPort,
      strictPort: true,
      hmr: { protocol: 'ws', host: 'localhost', port: devPort },
    },
  },
});
