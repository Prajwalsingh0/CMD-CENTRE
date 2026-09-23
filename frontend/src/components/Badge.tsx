import type { ReactNode } from 'react';

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'accent';

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`badge badge--${tone}`}>{children}</span>;
}

/** Task status → tone. One place, so a status can never look different on two screens. */
export function statusTone(status: string): Tone {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'IN_PROGRESS':
      return 'info';
    case 'CANCELLED':
      return 'neutral';
    case 'TODO':
      return 'warning';
    default:
      return 'neutral';
  }
}

export function priorityTone(priority: string): Tone {
  switch (priority) {
    case 'URGENT':
      return 'danger';
    case 'HIGH':
      return 'warning';
    case 'MEDIUM':
      return 'info';
    default:
      return 'neutral';
  }
}

export function goalTone(status: string): Tone {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'ACTIVE':
      return 'accent';
    case 'PAUSED':
      return 'warning';
    default:
      return 'neutral';
  }
}

export function documentTone(status: string): Tone {
  switch (status) {
    case 'READY':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'PROCESSING':
      return 'info';
    default:
      return 'warning';
  }
}

export function activityTone(status: string): Tone {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'REJECTED':
      return 'warning';
    case 'FAILED':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** The three-state skill vocabulary from the job-intelligence module. */
export function skillTone(state: string): Tone {
  switch (state) {
    case 'KNOWN':
      return 'success';
    case 'UNVERIFIED':
      return 'warning';
    default:
      return 'danger';
  }
}

export function skillMark(state: string): string {
  switch (state) {
    case 'KNOWN':
      return '✓';
    case 'UNVERIFIED':
      return '△';
    default:
      return '✗';
  }
}
