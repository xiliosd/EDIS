import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { evaluate, explain, type RuleSet } from './rule-evaluator';

interface RulesFixture {
  descripcion: string;
  reglas: RuleSet;
  columnas: string[];
  respuestas: Record<string, string>;
  deshabilitadas: string[];
  errores: Record<string, string>;
}

// Los mismos casos que usa RuleEvaluatorFixturesTest en Java: ambos motores deben coincidir.
// import.meta.url no es de esquema file en jsdom; por eso se usa import.meta.dirname.
const FIXTURES_DIR = resolve(import.meta.dirname, '../../../../../shared/rules-fixtures') + '/';
const FIXTURE_FILES = readdirSync(FIXTURES_DIR).filter((file) => file.endsWith('.json'));

describe('evaluate', () => {
  it('encuentra los casos compartidos', () => {
    expect(FIXTURE_FILES.length).toBeGreaterThanOrEqual(5);
  });

  it.each(FIXTURE_FILES)('coincide con el caso compartido %s', (file) => {
    const fixture = JSON.parse(readFileSync(`${FIXTURES_DIR}${file}`, 'utf-8')) as RulesFixture;

    const result = evaluate(fixture.reglas, fixture.respuestas, fixture.columnas);

    expect(result.disabled).toEqual([...fixture.deshabilitadas].sort());
    expect(result.errors).toEqual(fixture.errores);
  });

  it('tolera reglas sin saltos ni validaciones', () => {
    expect(evaluate({ encuesta: 'EDIN', anio: 2026, version: 1 }, { A01: '1' }, ['A01'])).toEqual({
      disabled: [],
      errors: {},
    });
  });
});

describe('explain', () => {
  function load(file: string): RulesFixture {
    return JSON.parse(readFileSync(`${FIXTURES_DIR}${file}`, 'utf-8')) as RulesFixture;
  }

  it('en la cascada (02) solo explica con la regla que sigue vigente', () => {
    const fixture = load('02-cascada.json');

    expect(explain(fixture.reglas, fixture.respuestas)).toEqual({ B01: ['a-no'] });
  });

  it('en el caso del simulador (07) explica cada columna oculta', () => {
    const fixture = load('07-caso-simulador.json');

    const reasons = explain(fixture.reglas, fixture.respuestas);

    expect(reasons).toEqual({ C01B: ['c01a-no'], C02: ['c01b-vacia'] });
    expect(Object.keys(reasons).sort()).toEqual(
      evaluate(fixture.reglas, fixture.respuestas, fixture.columnas).disabled,
    );
  });

  it('junta todas las reglas que deshabilitan la misma columna', () => {
    const rules: RuleSet = {
      saltos: [
        { id: 'r1', si: { pregunta: 'A', op: 'eq', valor: '1' }, entonces: { preguntas: ['X'] } },
        { id: 'r2', si: { pregunta: 'B', op: 'eq', valor: '1' }, entonces: { preguntas: ['X'] } },
      ],
    };

    expect(explain(rules, { A: '1', B: '1' })).toEqual({ X: ['r1', 'r2'] });
  });

  it('devuelve vacío sin reglas que coincidan', () => {
    expect(explain({ encuesta: 'EDIN', anio: 2026, version: 1 }, {})).toEqual({});
  });
});
