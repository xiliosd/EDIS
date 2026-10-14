import { setupServer } from 'msw/node';

/** Cada prueba registra sus propios handlers con server.use(...). */
export const server = setupServer();
