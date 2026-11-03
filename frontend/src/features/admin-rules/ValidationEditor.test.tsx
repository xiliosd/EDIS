import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RULE_QUESTIONS, rulesView } from '../../test/rules-fixtures';
import { ValidationEditor } from './ValidationEditor';

function renderEditor(props: { disabled?: boolean } = {}) {
  const onSave = vi.fn().mockResolvedValue(undefined);
  const onDelete = vi.fn().mockResolvedValue(undefined);
  const view = rulesView();
  render(
    <ValidationEditor
      questions={RULE_QUESTIONS.filter((item) => item.pageCode === 'C1' || item.column === 'C11')}
      validations={view.validations!}
      report={view.report}
      disabled={props.disabled}
      onSave={onSave}
      onDelete={onDelete}
    />,
  );
  return { onSave, onDelete, user: userEvent.setup() };
}

describe('ValidationEditor', () => {
  it('ofrece solo obligatoria para las preguntas de opciones', () => {
    renderEditor();

    const combo = screen.getByRole('group', { name: 'Validación de C01A' });
    expect(within(combo).getByLabelText('Obligatoria')).toBeInTheDocument();
    expect(within(combo).queryByLabelText('Tipo')).not.toBeInTheDocument();
    expect(within(combo).queryByLabelText('Mínimo')).not.toBeInTheDocument();
    expect(within(combo).queryByLabelText('Máximo')).not.toBeInTheDocument();
    expect(within(combo).queryByLabelText('Longitud máxima')).not.toBeInTheDocument();
    const checkbox = screen.getByRole('group', { name: 'Validación de C03' });
    expect(within(checkbox).queryByLabelText('Mínimo')).not.toBeInTheDocument();
  });

  it('ofrece tipo, rango y longitud para las preguntas de texto, con lo guardado', () => {
    renderEditor();

    const text = screen.getByRole('group', { name: 'Validación de C11' });
    expect(within(text).getByLabelText('Obligatoria')).toBeChecked();
    expect(within(text).getByLabelText('Tipo')).toHaveValue('entero');
    expect(within(text).getByLabelText('Mínimo')).toHaveValue('0');
    expect(within(text).getByLabelText('Máximo')).toHaveValue('');
    expect(within(text).getByLabelText('Longitud máxima')).toHaveValue('');
  });

  it('guarda omitiendo las claves vacías', async () => {
    const { onSave, user } = renderEditor();
    const text = screen.getByRole('group', { name: 'Validación de C11' });

    await user.type(within(text).getByLabelText('Máximo'), '120');
    await user.click(within(text).getByRole('button', { name: 'Guardar validación de C11' }));

    expect(onSave).toHaveBeenCalledWith('C11', {
      obligatoria: true,
      tipo: 'entero',
      min: 0,
      max: 120,
    });
  });

  it('guarda obligatoria sola en una pregunta de opciones', async () => {
    const { onSave, user } = renderEditor();
    const combo = screen.getByRole('group', { name: 'Validación de C01A' });

    await user.click(within(combo).getByLabelText('Obligatoria'));
    await user.click(within(combo).getByRole('button', { name: 'Guardar validación de C01A' }));

    expect(onSave).toHaveBeenCalledWith('C01A', { obligatoria: true });
  });

  it('rechaza un número inválido sin guardar', async () => {
    const { onSave, user } = renderEditor();
    const text = screen.getByRole('group', { name: 'Validación de C11' });

    await user.type(within(text).getByLabelText('Longitud máxima'), 'abc');
    await user.click(within(text).getByRole('button', { name: 'Guardar validación de C11' }));

    expect(onSave).not.toHaveBeenCalled();
    expect(within(text).getByText('Escriba un número.')).toBeVisible();
  });

  it('quita la validación y muestra el hallazgo junto a la pregunta', async () => {
    const { onDelete, user } = renderEditor();
    const text = screen.getByRole('group', { name: 'Validación de C11' });

    expect(within(text).getByText('Rango inválido')).toBeVisible();
    expect(within(text).getByText('El mínimo es mayor que el máximo.')).toBeVisible();
    await user.click(within(text).getByRole('button', { name: 'Quitar validación de C11' }));

    expect(onDelete).toHaveBeenCalledWith('C11');
    expect(
      within(screen.getByRole('group', { name: 'Validación de C01A' })).queryByRole('button', {
        name: 'Quitar validación de C01A',
      }),
    ).not.toBeInTheDocument();
  });

  it('desactiva todo cuando el año está publicado', () => {
    renderEditor({ disabled: true });

    expect(screen.getByLabelText('Mínimo')).toBeDisabled();
    for (const button of screen.getAllByRole('button')) {
      expect(button).toBeDisabled();
    }
  });
});
