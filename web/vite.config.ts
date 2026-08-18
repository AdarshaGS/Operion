/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
  server: {
    // Lets an ngrok (or any) tunnel domain reach the dev server - Vite blocks unknown
    // Host headers by default. Dev-only convenience, not meant to ship.
    allowedHosts: true,
    proxy: {
      // Same-origin from the tunnel's point of view, so no CORS config needed on the
      // backend and no VITE_API_BASE_URL override - just point it at the local backend.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
