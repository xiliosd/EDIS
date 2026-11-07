import type { Control, UseFormRegister } from 'react-hook-form';
import { useTermHelp } from '../help/TermHelp';
import type { RuleSet } from '../rules/rule-evaluator';
import type { QuestionnaireEntry } from '../survey-api';
import { CheckboxQuestion } from './CheckboxQuestion';
import { ChoiceQuestion } from './ChoiceQuestion';
import { TextQuestion } from './TextQuestion';

export type PageValues = Record<string, string>;

export interface QuestionProps {
  entry: QuestionnaireEntry;
  column: string;
  label: string;
  error?: string;
  register: UseFormRegister<PageValues>;
  control: Control<PageValues>;
  rules?: RuleSet;
}

export function fieldId(column: string): string {
  return `campo-${column}`;
}

/** Ayudas del texto de la pregunta; el término se busca después del numeral. */
export function useQuestionHelp({ entry, column, label }: QuestionProps, text = label) {
  return useTermHelp({
    idPrefix: fieldId(column),
    text,
    help: entry.help,
    searchFrom: (entry.label ?? '').length,
  });
}

export function QuestionField(props: QuestionProps) {
  switch (props.entry.answer?.inputType) {
    case 'SINGLE_CHOICE':
      return <ChoiceQuestion {...props} />;
    case 'CHECKBOX':
      return <CheckboxQuestion {...props} />;
    default:
      return <TextQuestion {...props} />;
  }
}
