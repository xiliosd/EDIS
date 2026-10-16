import { Controller } from 'react-hook-form';
import { fieldId, type QuestionProps } from './QuestionField';

/** La casilla heredada guarda "1" si se marca y "0" si no (D15). */
export function CheckboxQuestion({ column, label, error, control }: QuestionProps) {
  const id = fieldId(column);
  const errorId = `${id}-error`;
  return (
    <Controller
      control={control}
      name={column}
      render={({ field }) => (
        <div>
          <label htmlFor={id} className="flex min-h-11 items-center gap-2 font-medium">
            <input
              id={id}
              type="checkbox"
              className="size-5"
              checked={field.value === '1'}
              onChange={(event) => field.onChange(event.target.checked ? '1' : '0')}
              onBlur={field.onBlur}
              ref={field.ref}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? errorId : undefined}
            />
            {label}
          </label>
          {error && (
            <p id={errorId} className="mt-1 text-sm text-danger">
              {error}
            </p>
          )}
        </div>
      )}
    />
  );
}
