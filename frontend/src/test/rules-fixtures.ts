import type { RulesEditorView, RuleQuestion } from '../features/admin-rules/rules-api';

// Datos ficticios de las reglas de un año en borrador: dos páginas, un salto, uno heredado y uno anidado.
export const RULES_URL = '/admin/cuestionario/EDIN/2027/reglas';
export const RULES_API = '*/api/v1/admin/questionnaire/EDIN/2027/rules';

const YES_NO = [
  { value: '1', label: 'Sí' },
  { value: '2', label: 'No' },
];

function question(overrides: Partial<RuleQuestion> & { column: string }): RuleQuestion {
  return {
    inputType: 'SINGLE_CHOICE',
    columnStatus: 'ACTIVE',
    pageCode: 'C1',
    numeral: overrides.column,
    text: `Pregunta ${overrides.column}`,
    options: YES_NO,
    ...overrides,
  };
}

export const RULE_QUESTIONS: RuleQuestion[] = [
  question({ column: 'C01A' }),
  question({ column: 'C01B' }),
  question({ column: 'C03', inputType: 'CHECKBOX', options: [] }),
  question({ column: 'C09A', pageCode: 'C2' }),
  question({ column: 'C09B', pageCode: 'C2' }),
  question({ column: 'C10', pageCode: 'C2' }),
  question({ column: 'C11', pageCode: 'C2', inputType: 'TEXT', options: [] }),
  question({ column: 'Z01', pageCode: 'C2', inputType: 'UNKNOWN', options: [] }),
];

export function rulesView(overrides: Partial<RulesEditorView> = {}): RulesEditorView {
  return {
    survey: 'EDIN',
    year: 2027,
    state: 'borrador',
    version: 4,
    questions: RULE_QUESTIONS,
    skips: [
      {
        id: 'c01a-no',
        descripcion: 'Si no trabaja, omite la segunda',
        si: { pregunta: 'C01A', op: 'eq', valor: '2' },
        entonces: { accion: 'deshabilitar', preguntas: ['C01B'] },
      },
      {
        id: 'legacy-C09G',
        si: { pregunta: 'C09A', op: 'eq', valor: '1' },
        entonces: { accion: 'deshabilitar', preguntas: ['C10'] },
      },
      {
        id: 'anidada',
        si: {
          y: [
            { pregunta: 'C09A', op: 'eq', valor: '1' },
            {
              o: [
                { pregunta: 'C09B', op: 'eq', valor: '1' },
                { pregunta: 'C11', op: 'empty' },
              ],
            },
          ],
        },
        entonces: { accion: 'deshabilitar', preguntas: ['C10'] },
      },
      { id: 'sin-destino', si: { pregunta: 'C01A', op: 'eq', valor: '1' } },
    ],
    legacySkipIds: ['legacy-C09G'],
    validations: { C11: { obligatoria: true, tipo: 'entero', min: 0 } },
    cases: [],
    caseResults: [],
    report: {
      errors: [
        {
          code: 'RANGO_INVALIDO',
          element: 'validacion:C11',
          questions: ['C11'],
          message: 'El mínimo es mayor que el máximo.',
        },
      ],
      warnings: [
        {
          code: 'REGLAS_SOLAPADAS',
          element: 'regla:c01a-no',
          questions: ['C01B'],
          message: 'Dos reglas ocultan C01B.',
        },
      ],
    },
    ...overrides,
  };
}
