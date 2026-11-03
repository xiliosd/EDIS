import { describe, expect, it } from 'vitest';
import type { components } from '../../services/api-schema';
import { describeCondition, describeRule } from './rule-description';

type RuleQuestion = components['schemas']['RuleQuestion'];
type Condition = components['schemas']['Condition'];

const YES_NO = [
  { value: '1', label: 'Sí' },
  { value: '2', label: 'No' },
];

function question(column: string, options = YES_NO): RuleQuestion {
  return { column, options };
}

const QUESTIONS = new Map<string, RuleQuestion>([
  ['E03', question('E03')],
  ['E04', question('E04')],
  ['C09A', question('C09A')],
  ['C09B', question('C09B')],
  ['EDAD', question('EDAD', [])],
]);

describe('describeCondition', () => {
  it.each<[string, Condition, string]>([
    ['eq', { pregunta: 'E03', op: 'eq', valor: '1' }, 'E03 = Sí'],
    ['ne', { pregunta: 'E03', op: 'ne', valor: '1' }, 'E03 ≠ Sí'],
    ['in', { pregunta: 'E03', op: 'in', valores: ['1', '2'] }, 'E03 es una de {Sí, No}'],
    ['notIn', { pregunta: 'E03', op: 'notIn', valores: ['2'] }, 'E03 no es ninguna de {No}'],
    ['gt', { pregunta: 'EDAD', op: 'gt', valor: '17' }, 'EDAD > 17'],
    ['lt', { pregunta: 'EDAD', op: 'lt', valor: '5' }, 'EDAD < 5'],
    ['empty', { pregunta: 'E03', op: 'empty' }, 'E03 está vacía'],
    ['notEmpty', { pregunta: 'E03', op: 'notEmpty' }, 'E03 tiene respuesta'],
  ])('lee el operador %s', (_name, condition, expected) => {
    expect(describeCondition(condition, QUESTIONS)).toBe(expected);
  });

  it('lee todas y alguna', () => {
    expect(describeCondition({ todas: ['C09A', 'C09B'], op: 'eq', valor: '2' }, QUESTIONS)).toBe(
      'todas de C09A, C09B = No',
    );
    expect(describeCondition({ alguna: ['C09A', 'C09B'], op: 'eq', valor: '2' }, QUESTIONS)).toBe(
      'alguna de C09A, C09B = No',
    );
  });

  it('lee y/o anidados entre paréntesis', () => {
    const condition: Condition = {
      y: [
        { pregunta: 'E03', op: 'eq', valor: '1' },
        {
          o: [
            { pregunta: 'EDAD', op: 'gt', valor: '17' },
            { pregunta: 'E04', op: 'empty' },
          ],
        },
      ],
    };
    expect(describeCondition(condition, QUESTIONS)).toBe(
      '(E03 = Sí y (EDAD > 17 o E04 está vacía))',
    );
  });

  it('usa el código y el valor tal cual si la pregunta no existe', () => {
    expect(describeCondition({ pregunta: 'ZZ9', op: 'eq', valor: '1' }, QUESTIONS)).toBe('ZZ9 = 1');
  });
});

describe('describeRule', () => {
  it('lee la regla con la frase exacta', () => {
    expect(
      describeRule(
        {
          id: 'e03-si',
          si: { pregunta: 'E03', op: 'eq', valor: '1' },
          entonces: { accion: 'deshabilitar', preguntas: ['E04'] },
        },
        QUESTIONS,
      ),
    ).toBe('Si E03 = Sí → no se muestra E04');
  });

  it('une varios destinos con coma', () => {
    expect(
      describeRule(
        {
          id: 'r',
          si: { pregunta: 'E03', op: 'empty' },
          entonces: { accion: 'deshabilitar', preguntas: ['E04', 'C09A'] },
        },
        QUESTIONS,
      ),
    ).toBe('Si E03 está vacía → no se muestra E04, C09A');
  });
});
