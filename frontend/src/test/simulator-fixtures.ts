import type { SimulatorView } from '../features/admin-rules/rules-api';
import type { QuestionnaireEntry } from '../features/survey/survey-api';

// Datos ficticios del simulador de EDIN 2027: C1 con dos preguntas de opción y una casilla, C2 con un texto.
export const SIMULATOR_URL = '/admin/cuestionario/EDIN/2027/simulador';
export const SIMULATOR_API = '*/api/v1/admin/questionnaire/EDIN/2027/simulator';

const YES_NO = [
  { value: '1', label: 'Sí' },
  { value: '2', label: 'No' },
];

export function simulatorEntry(
  column: string,
  pageCode: string,
  overrides: Partial<NonNullable<QuestionnaireEntry['answer']>> = {},
): QuestionnaireEntry {
  return {
    id: [...column].reduce((hash, char) => hash * 31 + char.charCodeAt(0), 7),
    kind: 'ITEM',
    label: column,
    text: `Pregunta ${column}`,
    pageCode,
    headers: [],
    answer: {
      columnCode: column,
      columnStatus: 'ACTIVE',
      inputType: 'SINGLE_CHOICE',
      options: YES_NO,
      ...overrides,
    },
  };
}

export function simulatorView(): SimulatorView {
  return {
    version: 4,
    rules: {
      encuesta: 'EDIN',
      anio: 2027,
      version: 4,
      saltos: [
        {
          id: 'c01a-no',
          si: { pregunta: 'C01A', op: 'eq', valor: '2' },
          entonces: { accion: 'deshabilitar', preguntas: ['C01B'] },
        },
      ],
      validaciones: { C11: { obligatoria: true, tipo: 'entero', min: 0 } },
    },
    pages: [
      {
        pageCode: 'C1',
        chapterCode: 'C',
        chapterTitle: 'Capítulo C',
        module: 'Módulo C',
        state: 'AVAILABLE',
        entries: [
          simulatorEntry('C01A', 'C1'),
          simulatorEntry('C01B', 'C1'),
          simulatorEntry('C03', 'C1', { inputType: 'CHECKBOX', options: [] }),
        ],
      },
      {
        pageCode: 'C2',
        chapterCode: 'C',
        chapterTitle: 'Capítulo C',
        module: 'Módulo C',
        state: 'AVAILABLE',
        entries: [simulatorEntry('C11', 'C2', { inputType: 'TEXT', options: [] })],
      },
    ],
  };
}
