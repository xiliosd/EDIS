// Mismos textos que AnswerErrorMessages.java: el encuestado ve el mismo mensaje se valide donde se valide.
const MESSAGES: Record<string, string> = {
  REQUERIDA: 'Responda esta pregunta.',
  TIPO_INVALIDO: 'El valor no tiene el formato esperado.',
  FUERA_DE_RANGO: 'El valor está fuera del rango permitido.',
  LONGITUD: 'La respuesta es demasiado larga.',
  OPCION_INVALIDA: 'Seleccione una de las opciones.',
  PREGUNTA_DESCONOCIDA: 'Pregunta desconocida.',
};

export function messageFor(code: string): string {
  return MESSAGES[code] ?? 'Respuesta inválida.';
}
