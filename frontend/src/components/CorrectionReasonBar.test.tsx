import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { validReason } from '../hooks/use-correction-reason';
import { CorrectionReasonBar } from './CorrectionReasonBar';

function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return <CorrectionReasonBar value={value} onChange={setValue} />;
}

describe('validReason', () => {
  it('devuelve el texto recortado cuando mide entre 10 y 500 caracteres', () => {
    expect(validReason('   Corrige la ortografía   ')).toBe('Corrige la ortografía');
    expect(validReason('a'.repeat(500))).toBe('a'.repeat(500));
  });

  it('rechaza lo corto, lo largo y lo que solo tiene espacios', () => {
    expect(validReason('breve')).toBeUndefined();
    expect(validReason('          x          ')).toBeUndefined();
    expect(validReason('a'.repeat(501))).toBeUndefined();
    expect(validReason('')).toBeUndefined();
  });
});

describe('CorrectionReasonBar', () => {
  it('ofrece un campo obligatorio con etiqueta, límites y la advertencia de datos personales', () => {
    render(<Harness />);

    const field = screen.getByRole('textbox', { name: 'Motivo de la corrección' });
    expect(field).toBeRequired();
    expect(field).toHaveAttribute('minlength', '10');
    expect(field).toHaveAttribute('maxlength', '500');
    expect(field).toHaveAccessibleDescription(/No escriba datos personales en el motivo\./);
  });

  it('avisa que faltan caracteres hasta que el motivo es válido', async () => {
    render(<Harness />);
    const field = screen.getByRole('textbox', { name: 'Motivo de la corrección' });

    await userEvent.type(field, 'corto');
    expect(screen.getByText('Escriba al menos 10 caracteres.')).toBeInTheDocument();
    expect(field).toHaveAttribute('aria-invalid', 'true');

    await userEvent.type(field, ' pero ya largo');
    expect(screen.queryByText('Escriba al menos 10 caracteres.')).not.toBeInTheDocument();
    expect(field).not.toHaveAttribute('aria-invalid');
  });
});
