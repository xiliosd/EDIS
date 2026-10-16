import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { evaluate, type RuleSet } from './rule-evaluator';

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
