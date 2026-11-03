import { fireEvent, render, screen, within } from '@testing-library/react';
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

type User = ReturnType<typeof userEvent.setup>;

/** Recorre el documento con Tab hasta que el foco llega al control. */
async function tabTo(user: User, control: HTMLElement) {
  for (let step = 0; step < 80 && document.activeElement !== control; step += 1) {
    await user.tab();
  }
  expect(control).toHaveFocus();
}

/**
 * jsdom no cambia la opción de un select con las flechas. Con el select enfocado por Tab se
 * pulsa la flecha y se aplica el cambio que haría el navegador.
 */
async function chooseFocused(user: User, select: HTMLSelectElement, ...values: string[]) {
  expect(select).toHaveFocus();
  await user.keyboard('{ArrowDown}');
  for (const option of Array.from(select.options)) {
    option.selected = values.includes(option.value);
  }
  fireEvent.change(select);
}

async function fillSelect(user: User, label: string, ...values: string[]) {
  const select = screen.getByLabelText(label) as HTMLSelectElement;
  await tabTo(user, select);
  await chooseFocused(user, select, ...values);
}

async function saveWithEnter(user: User) {
  await tabTo(user, screen.getByRole('button', { name: 'Guardar' }));
  await user.keyboard('{Enter}');
}

describe('SkipRuleBuilder', () => {
  it('arma C01A = 2 → C01B solo con el teclado', async () => {
    const { onSave, user } = renderBuilder();

    await tabTo(user, screen.getByLabelText('Identificador'));
    await user.keyboard('c01a-dos');
    await fillSelect(user, 'Pregunta de la condición 1', 'C01A');
    await fillSelect(user, 'Operador de la condición 1', 'eq');
    await fillSelect(user, 'Valor de la condición 1', '2');
    await fillSelect(user, 'Preguntas que no se muestran', 'C01B');
    await saveWithEnter(user);

    expect(onSave).toHaveBeenCalledWith({
      id: 'c01a-dos',
      si: { pregunta: 'C01A', op: 'eq', valor: '2' },
      entonces: { accion: 'deshabilitar', preguntas: ['C01B'] },
    });
  });

  it('arma todas de C09A,C09B = 2 → C10 solo con el teclado', async () => {
    const { onSave, user } = renderBuilder();

    await tabTo(user, screen.getByLabelText('Identificador'));
    await user.keyboard('c09-todas');
    await tabTo(user, screen.getByLabelText('Descripción'));
    await user.keyboard('Ninguna aplica');
    await fillSelect(user, 'Modo de la condición 1', 'todas');
    await fillSelect(user, 'Preguntas de la condición 1', 'C09A', 'C09B');
    await fillSelect(user, 'Valor de la condición 1', '2');
    await fillSelect(user, 'Preguntas que no se muestran', 'C10');
    await saveWithEnter(user);

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

  it('en todas/alguna deshabilita las preguntas de otro tipo u otras opciones', async () => {
    const { user } = renderBuilder();

    await user.selectOptions(screen.getByLabelText('Modo de la condición 1'), 'todas');
    await user.selectOptions(screen.getByLabelText('Preguntas de la condición 1'), 'C09A');

    const picker = within(screen.getByLabelText('Preguntas de la condición 1'));
    expect(picker.getByRole('option', { name: /^C09B/ })).toBeEnabled();
    expect(picker.getByRole('option', { name: /^C11/ })).toBeDisabled();
    expect(picker.getByRole('option', { name: /^C03/ })).toBeDisabled();
    expect(
      screen.getByText(/mismo tipo y con las mismas opciones que la primera elegida/),
    ).toBeVisible();
  });

  it('un tipo desconocido sin opciones solo admite está vacía o tiene respuesta', async () => {
    const { user } = renderBuilder();

    await user.selectOptions(screen.getByLabelText('Pregunta de la condición 1'), 'Z01');

    expect(
      screen.getByText(
        'Esta pregunta no admite condiciones por valor; use está vacía o tiene respuesta.',
      ),
    ).toBeVisible();
    const operators = screen.getByLabelText('Operador de la condición 1') as HTMLSelectElement;
    expect(Array.from(operators.options).map((option) => option.value)).toEqual([
      'empty',
      'notEmpty',
    ]);
    expect(screen.queryByLabelText('Valor de la condición 1')).not.toBeInTheDocument();
  });

  it.each(['y', 'o'])('abre en solo lectura una regla con %s vacío', (join) => {
    renderBuilder({
      id: 'vacia',
      si: { [join]: [] },
      entonces: { accion: 'deshabilitar', preguntas: ['C10'] },
    });

    expect(screen.getByText('Esta regla solo se edita importando el archivo.')).toBeVisible();
  });
});
