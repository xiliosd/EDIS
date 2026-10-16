import type { Control, UseFormRegister } from 'react-hook-form';
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
