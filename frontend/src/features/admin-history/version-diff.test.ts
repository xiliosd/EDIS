import { describe, expect, it } from 'vitest';
import type { RuleSet } from '../admin-rules/rules-api';
import { stableJson, versionDiff } from './version-diff';

const skip = (id: string, value: string, target = 'C02') => ({
  id,
  si: { pregunta: 'C01', op: 'eq' as const, valor: value },
  entonces: { preguntas: [target] },
});

const empty: RuleSet = { saltos: [], validaciones: {}, casos: [], tablas: {} };

describe('stableJson', () => {
  it('ordena las claves de forma recursiva', () => {
    expect(stableJson({ b: 1, a: { d: 2, c: [{ z: 1, y: 2 }] } })).toBe(
      '{"a":{"c":[{"y":2,"z":1}],"d":2},"b":1}',
    );
  });
});

describe('versionDiff', () => {
  it('no reporta cambios entre dos versiones iguales aunque cambie el orden de claves', () => {
    const older: RuleSet = { ...empty, validaciones: { C01: { min: 1, max: 5 } } };
    const newer: RuleSet = { ...empty, validaciones: { C01: { max: 5, min: 1 } } };
    expect(versionDiff(older, newer)).toEqual({
      skips: [],
      validations: [],
      cases: [],
      tables: [],
    });
  });

  it('detecta saltos agregados, eliminados y cambiados descritos en lenguaje claro', () => {
    const older: RuleSet = { ...empty, saltos: [skip('s1', '1'), skip('s2', '1')] };
    const newer: RuleSet = { ...empty, saltos: [skip('s2', '2'), skip('s3', '1')] };

    const { skips } = versionDiff(older, newer);

    expect(skips).toEqual([
      { id: 's1', kind: 'eliminada', before: 'Si C01 = 1 → no se muestra C02' },
      {
        id: 's2',
        kind: 'cambiada',
        before: 'Si C01 = 1 → no se muestra C02',
        after: 'Si C01 = 2 → no se muestra C02',
      },
      { id: 's3', kind: 'agregada', after: 'Si C01 = 1 → no se muestra C02' },
    ]);
  });

  it('detecta validaciones por pregunta con JSON estable', () => {
    const older: RuleSet = {
      ...empty,
      validaciones: { C01: { min: 1, max: 5 }, C02: { min: 0, max: 9 } },
    };
    const newer: RuleSet = {
      ...empty,
      validaciones: { C01: { min: 1, max: 6 }, C03: { min: 0, max: 3 } },
    };

    const { validations } = versionDiff(older, newer);

    expect(validations.map((change) => [change.id, change.kind])).toEqual([
      ['C01', 'cambiada'],
      ['C02', 'eliminada'],
      ['C03', 'agregada'],
    ]);
    expect(validations[0]).toMatchObject({
      before: '{"max":5,"min":1}',
      after: '{"max":6,"min":1}',
    });
  });

  it('detecta casos y tablas agregados, eliminados y cambiados', () => {
    const older: RuleSet = {
      ...empty,
      casos: [
        { id: 'k1', nombre: 'Uno' },
        { id: 'k2', nombre: 'Dos' },
      ],
      tablas: { C2: 'T_UNO', C3: 'T_DOS' },
    };
    const newer: RuleSet = {
      ...empty,
      casos: [
        { id: 'k2', nombre: 'Dos bis' },
        { id: 'k3', nombre: 'Tres' },
      ],
      tablas: { C2: 'T_UNO', C3: 'T_TRES', C4: 'T_CUATRO' },
    };

    const diff = versionDiff(older, newer);

    expect(diff.cases.map((change) => [change.id, change.kind])).toEqual([
      ['k1', 'eliminada'],
      ['k2', 'cambiada'],
      ['k3', 'agregada'],
    ]);
    expect(diff.tables.map((change) => [change.id, change.kind])).toEqual([
      ['C3', 'cambiada'],
      ['C4', 'agregada'],
    ]);
  });
});
