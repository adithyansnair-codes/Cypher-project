import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
    plugins: [react()],
    base: './',
    server: {
        host: true,
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8090',
                changeOrigin: true,
            },
            '/ws': {
                target: 'ws://127.0.0.1:8090',
                ws: true,
                changeOrigin: true,
            },
            '/ai': {
                target: 'http://127.0.0.1:5000',
                changeOrigin: true,
                rewrite: function (path) { return path.replace(/^\/ai/, ''); },
            },
        },
        hmr: {
            clientPort: 5173,
        },
    },
});
