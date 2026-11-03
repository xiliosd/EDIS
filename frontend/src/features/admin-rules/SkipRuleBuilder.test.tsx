import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RULE_QUESTIONS, rulesView } from '../../test/rules-fixtures';
import type { SkipRule } from './rules-api';
import { SkipRuleBuilder } from './SkipRuleBuilder';

function renderBuilder(rule?: SkipRule) {
  const onSave = vi.fn();
  const onCancel = vi.fn();
  render(
    <SkipRuleBuilder
      rule={rule}
      questions={RULE_QUESTIONS}
      existingIds={['c01a-no']}
      onSave={onSave}
      onCancel={onCancel}
    />,
  );
  return { onSave, onCancel, user: userEvent.setup() };
}

describe('SkipRuleBuilder', () => {
  it('arma C01A = 2 → C01B solo con el teclado', async () => {
    const { onSave, user } = renderBuilder();

    await user.type(screen.getByLabelText('Identificador'), 'c01a-dos');
    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C01A');
    await user.selectOptions(screen.getByLabelText('Operador de la condición 1'), 'eq');
    await user.selectOptions(screen.getByLabelText('Valor de la condición 1'), '2');
    await user.selectOptions(screen.getByLabelText('Preguntas que no se muestran'), 'C01B');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSave).toHaveBeenCalledWith({
      id: 'c01a-dos',
      si: { pregunta: 'C01A', op: 'eq', valor: '2' },
      entonces: { accion: 'deshabilitar', preguntas: ['C01B'] },
    });
  });

  it('arma todas de C09A,C09B = 2 → C10', async () => {
    const { onSave, user } = renderBuilder();

    await user.type(screen.getByLabelText('Identificador'), 'c09-todas');
    await user.type(screen.getByLabelText('Descripción'), 'Ninguna aplica');
    await user.selectOptions(screen.getByLabelText('Modo de la condición 1'), 'todas');
    await user.selectOptions(screen.getByLabelText('Preguntas de la condición 1'), [
      'C09A',
      'C09B',
    ]);
    await user.selectOptions(screen.getByLabelText('Valor de la condición 1'), '2');
    await user.selectOptions(screen.getByLabelText('Preguntas que no se muestran'), 'C10');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSave).toHaveBeenCalledWith({
      id: 'c09-todas',
      descripcion: 'Ninguna aplica',
      si: { todas: ['C09A', 'C09B'], op: 'eq', valor: '2' },
      entonces: { accion: 'deshabilitar', preguntas: ['C10'] },
    });
  });

  it('une dos condiciones con O y usa casillas para en/no en', async () => {
    const { onSave, user } = renderBuilder();

    await user.type(screen.getByLabelText('Identificador'), 'mixta');
    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C01A');
    await user.selectOptions(screen.getByLabelText('Operador de la condición 1'), 'in');
    await user.click(screen.getByRole('checkbox', { name: 'Sí' }));
    await user.click(screen.getByRole('button', { name: 'Agregar condición' }));
    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 2'), 'C11');
    await user.selectOptions(screen.getByLabelText('Operador de la condición 2'), 'empty');
    await user.selectOptions(screen.getByLabelText('Unir condiciones con'), 'o');
    await user.selectOptions(screen.getByLabelText('Preguntas que no se muestran'), 'C10');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSave).toHaveBeenCalledWith({
      id: 'mixta',
      si: {
        o: [
          { pregunta: 'C01A', op: 'in', valores: ['1'] },
          { pregunta: 'C11', op: 'empty' },
        ],
      },
      entonces: { accion: 'deshabilitar', preguntas: ['C10'] },
    });
  });

  it('ofrece valor de texto solo para preguntas de texto', async () => {
    const { user } = renderBuilder();

    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C01A');
    expect(screen.getByLabelText('Valor de la condición 1').tagName).toBe('SELECT');

    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C03');
    expect(screen.getByLabelText('Valor de la condición 1').tagName).toBe('SELECT');
    expect(screen.getByRole('option', { name: 'Sí' })).toHaveValue('1');
    expect(screen.getByRole('option', { name: 'No' })).toHaveValue('0');

    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C11');
    expect(screen.getByLabelText('Valor de la condición 1').tagName).toBe('INPUT');
  });

  it('no envía una regla sin preguntas destino y lleva el foco al error', async () => {
    const { onSave, user } = renderBuilder();

    await user.type(screen.getByLabelText('Identificador'), 'sin-destino');
    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C01A');
    await user.selectOptions(screen.getByLabelText('Valor de la condición 1'), '1');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByText('Elija al menos una pregunta que no se muestre.')).toBeVisible();
    expect(screen.getByLabelText('Preguntas que no se muestran')).toHaveFocus();
  });

  it('rechaza un identificador repetido o inválido', async () => {
    const { onSave, user } = renderBuilder();

    await user.type(screen.getByLabelText('Identificador'), 'c01a-no');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(screen.getByText('Ya existe una regla con ese identificador.')).toBeVisible();

    await user.clear(screen.getByLabelText('Identificador'));
    await user.type(screen.getByLabelText('Identificador'), 'Mal Id');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(screen.getByText(/Use minúsculas, números y guiones/)).toBeVisible();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('muestra una vista previa en lenguaje claro', async () => {
    const { user } = renderBuilder();

    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'C01A');
    await user.selectOptions(screen.getByLabelText('Valor de la condición 1'), '2');
    await user.selectOptions(screen.getByLabelText('Preguntas que no se muestran'), 'C01B');

    expect(screen.getByText('Si C01A = No → no se muestra C01B')).toBeVisible();
  });

  it('abre una regla existente con sus datos y sin editar el identificador', async () => {
    const rule = rulesView().skips![0];
    const { onSave, user } = renderBuilder(rule);

    expect(screen.queryByLabelText('Identificador')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Pregunta de la condición 1')).toHaveValue('C01A');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSave).toHaveBeenCalledWith(rule);
  });

  it('abre en solo lectura una regla con condiciones anidadas', async () => {
    const nested = rulesView().skips![2];
    const { onSave, onCancel, user } = renderBuilder(nested);

    expect(screen.getByText('Esta regla solo se edita importando el archivo.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Pregunta de la condición 1')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(onCancel).toHaveBeenCalled();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('quita una condición', async () => {
    const { user } = renderBuilder();

    await user.click(screen.getByRole('button', { name: 'Agregar condición' }));
    expect(screen.getByLabelText('Pregunta de la condición 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar condición 2' }));

    expect(screen.queryByLabelText('Pregunta de la condición 2')).not.toBeInTheDocument();
  });
});
