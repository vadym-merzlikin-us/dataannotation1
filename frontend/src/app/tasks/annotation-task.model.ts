export const LABELS = ['positive', 'neutral', 'negative'] as const;

export type Label = (typeof LABELS)[number];

export interface AnnotationTask {
  id: string;
  text: string;
  label?: Label;
  createdAt: string;
  labelledAt?: string;
}
