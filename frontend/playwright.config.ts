import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  // Un solo worker y sin reintentos: cada página se guarda una sola vez y el ingreso se bloquea
  // tras 5 intentos fallidos; un reintento no puede repetir el flujo y ocultaría el primer fallo.
  workers: 1,
  retries: 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8081',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'movil', use: { ...devices['Pixel 7'] } },
  ],
});
