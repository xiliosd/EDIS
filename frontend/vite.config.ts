/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const CRITICAL_COVERAGE = { lines: 80, functions: 80, branches: 80, statements: 80 };

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Mismo origen en desarrollo: la cookie SameSite=Strict y el CSRF funcionan sin CORS.
    // changeOrigin en false conserva el Host original; si no, el backend vería Origin distinto
    // de Host y rechazaría la petición por CORS.
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    exclude: ['e2e/**', 'node_modules/**'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/**/*.d.ts', 'src/test/**', 'src/**/*.test.{ts,tsx}'],
      thresholds: {
        lines: 60,
        functions: 60,
        branches: 60,
        statements: 60,
        // Datos personales (novedades, claves y listados nominales): 80 % como en el backend.
        'src/features/admin-novelties/**': CRITICAL_COVERAGE,
        'src/features/admin-keys/**': CRITICAL_COVERAGE,
        'src/features/admin-reports/**': CRITICAL_COVERAGE,
      },
    },
  },
});
