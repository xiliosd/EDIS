import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SaveCaseDialog } from './SaveCaseDialog';

function renderDialog(props: Partial<Parameters<typeof SaveCaseDialog>[0]> = {}) {
  const onSave = vi.fn();
  const onCancel = vi.fn();
  render(<SaveCaseDialog existingIds={['viejo']} onSave={onSave} onCancel={onCancel} {...props} />);
  return { onSave, onCancel, user: userEvent.setup() };
}

describe('SaveCaseDialog', () => {
  it('entrega el id y el nombre sin espacios sobrantes', async () => {
    const { onSave, user } = renderDialog();

    await user.type(screen.getByLabelText('Identificador del caso'), 'sin-trabajo');
    await user.type(screen.getByLabelText('Nombre del caso'), '  No trabaja  ');
    await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

    expect(onSave).toHaveBeenCalledWith({ id: 'sin-trabajo', name: 'No trabaja' });
  });

  it('empieza con el id y el nombre de un intento anterior', async () => {
    const { onSave, user } = renderDialog({
      initialDraft: { id: 'previo', name: 'Intento previo' },
    });

    expect(screen.getByLabelText('Identificador del caso')).toHaveValue('previo');
    expect(screen.getByLabelText('Nombre del caso')).toHaveValue('Intento previo');
    await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

    expect(onSave).toHaveBeenCalledWith({ id: 'previo', name: 'Intento previo' });
  });

  it('rechaza un id inválido y un nombre vacío o de más de 100 caracteres', async () => {
    const { onSave, user } = renderDialog();

    await user.type(screen.getByLabelText('Identificador del caso'), 'Con Mayúsculas');
    await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

    expect(screen.getByText(/minúsculas, números y guiones/)).toBeVisible();
    expect(screen.getByText('Escriba el nombre del caso.')).toBeVisible();
    expect(screen.getByLabelText('Identificador del caso')).toHaveAttribute('aria-invalid', 'true');

    await user.clear(screen.getByLabelText('Identificador del caso'));
    await user.type(screen.getByLabelText('Identificador del caso'), 'ok');
    await user.click(screen.getByLabelText('Nombre del caso'));
    await user.paste('x'.repeat(101));
    await user.click(screen.getByRole('button', { name: 'Guardar caso' }));

    expect(screen.getByText('El nombre admite hasta 100 caracteres.')).toBeVisible();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('avisa que un id existente se reemplaza y muestra el error del servidor', async () => {
    const { user } = renderDialog({ error: 'No fue posible completar la solicitud.' });

    await user.type(screen.getByLabelText('Identificador del caso'), 'viejo');

    expect(screen.getByText('Ya existe un caso con este id: se reemplazará.')).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('No fue posible completar la solicitud.');
  });

  it('cancela con el botón Cancelar', async () => {
    const { onCancel, user } = renderDialog();

    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onCancel).toHaveBeenCalledOnce();
  });
});
