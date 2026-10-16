import { fieldId, type QuestionProps } from './QuestionField';

// Con más de 7 opciones, los radios alargan la página más que lo que ayudan.
const MAX_RADIO_OPTIONS = 7;

export function ChoiceQuestion({ entry, column, label, error, register }: QuestionProps) {
  const options = entry.answer?.options ?? [];
  const id = fieldId(column);
  const errorId = `${id}-error`;
  const errorMessage = error && (
    <p id={errorId} className="mt-1 text-sm text-danger">
      {error}
    </p>
  );

  if (options.length > MAX_RADIO_OPTIONS) {
    return (
      <div>
        <label htmlFor={id} className="block font-medium">
          {label}
        </label>
        <select
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          className="mt-1 block min-h-11 w-full rounded-lg border border-ink-muted/40 bg-surface px-3 text-base text-ink aria-invalid:border-danger"
          {...register(column)}
        >
          <option value="">Seleccione una opción</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {errorMessage}
      </div>
    );
  }

  return (
    <fieldset
      id={id}
      tabIndex={-1}
      role="radiogroup"
      aria-invalid={error ? true : undefined}
      aria-describedby={error ? errorId : undefined}
    >
      <legend className="font-medium">{label}</legend>
      <div className="mt-2 flex flex-wrap gap-x-6 gap-y-2">
        {options.map((option) => (
          <label key={option.value} className="flex min-h-11 items-center gap-2">
            <input type="radio" value={option.value} className="size-5" {...register(column)} />
            {option.label}
          </label>
        ))}
      </div>
      {errorMessage}
    </fieldset>
  );
}
