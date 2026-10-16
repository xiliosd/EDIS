import { describe, expect, it } from 'vitest';
import { messageFor } from './answer-error-messages';

describe('messageFor', () => {
  it('traduce cada código de error al mismo texto que el backend', () => {
    expect(messageFor('REQUERIDA')).toBe('Responda esta pregunta.');
    expect(messageFor('TIPO_INVALIDO')).toBe('El valor no tiene el formato esperado.');
    expect(messageFor('FUERA_DE_RANGO')).toBe('El valor está fuera del rango permitido.');
    expect(messageFor('LONGITUD')).toBe('La respuesta es demasiado larga.');
    expect(messageFor('OPCION_INVALIDA')).toBe('Seleccione una de las opciones.');
    expect(messageFor('PREGUNTA_DESCONOCIDA')).toBe('Pregunta desconocida.');
  });

  it('usa un texto genérico para códigos desconocidos', () => {
    expect(messageFor('OTRO')).toBe('Respuesta inválida.');
  });
});
