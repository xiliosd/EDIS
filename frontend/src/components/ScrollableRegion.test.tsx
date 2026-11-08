import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ScrollableRegion } from './ScrollableRegion';

describe('ScrollableRegion', () => {
  it('isANamedFocusableRegionSoKeyboardUsersCanScrollIt', () => {
    render(
      <ScrollableRegion label="Cobertura" className="mt-4" aria-busy>
        <p>Contenido ancho</p>
      </ScrollableRegion>,
    );
    const region = screen.getByRole('region', { name: 'Cobertura' });
    expect(region).toHaveAttribute('tabindex', '0');
    expect(region).toHaveClass('overflow-x-auto', 'mt-4');
    expect(region).toHaveAttribute('aria-busy', 'true');
    expect(region).toHaveTextContent('Contenido ancho');
  });
});
