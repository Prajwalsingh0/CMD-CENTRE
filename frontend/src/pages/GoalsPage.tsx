import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { goalApi } from '../api/endpoints';
import type { GoalResponse, GoalStatus, Priority } from '../api/types';
import { Badge, goalTone, priorityTone } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { Modal } from '../components/Modal';
import { ProgressBar } from '../components/ProgressBar';
import { useToast } from '../components/ToastProvider';
import { formatDate } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
const STATUSES: GoalStatus[] = ['ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED'];

interface GoalForm {
  title: string;
  description: string;
  deadline: string;
  priority: Priority;
}

export function GoalsPage() {
  const toast = useToast();
  const [statusFilter, setStatusFilter] = useState<GoalStatus | ''>('');
  const [formOpen, setFormOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<ApiError | null>(null);
  const [form, setForm] = useState<GoalForm>({ title: '', description: '', deadline: '', priority: 'MEDIUM' });
  const [pendingDelete, setPendingDelete] = useState<GoalResponse | null>(null);

  const { data, loading, error, reload } = useAsync<GoalResponse[]>(() => goalApi.list(statusFilter), [statusFilter]);
  useDataRefresh(reload);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    try {
      await goalApi.create({
        title: form.title.trim(),
        description: form.description.trim() === '' ? null : form.description.trim(),
        deadline: form.deadline === '' ? null : form.deadline,
        priority: form.priority,
      });
      toast.success('Goal created');
      setFormOpen(false);
      setForm({ title: '', description: '', deadline: '', priority: 'MEDIUM' });
      reload();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught : new ApiError(0, 'UNKNOWN', 'Could not create the goal.'));
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) {
      return;
    }
    try {
      await goalApi.remove(pendingDelete.id);
      toast.success('Goal deleted', 'Its tasks were kept and detached.');
      setPendingDelete(null);
      reload();
    } catch (caught) {
      toast.failure('Could not delete the goal', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  const fieldIssues = formError?.fieldIssues ?? {};

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Goals</h1>
          <p>Progress is derived from the tasks attached to each goal — no manual percentages.</p>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setFormOpen(true)}>
          New goal
        </button>
      </div>

      <div className="row" style={{ marginBottom: 16 }}>
        <button type="button" className={statusFilter === '' ? 'btn btn--primary btn--sm' : 'btn btn--sm'} onClick={() => setStatusFilter('')}>
          All
        </button>
        {STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            className={statusFilter === status ? 'btn btn--primary btn--sm' : 'btn btn--sm'}
            onClick={() => setStatusFilter(status)}
          >
            {status}
          </button>
        ))}
      </div>

      {loading ? <LoadingState label="Loading goals…" /> : null}
      {error ? <ErrorState error={error} onRetry={reload} /> : null}

      {!loading && !error && data ? (
        data.length === 0 ? (
          <EmptyState
            title="No goals yet"
            message="Create one here, or ask the command bar for “a 14-day plan” and it will build the goal and its tasks."
          />
        ) : (
          <div className="grid grid--3">
            {data.map((goal) => (
              <section key={goal.id} className="card">
                <div className="row row--between">
                  <Link to={`/goals/${goal.id}`}>
                    <strong>{goal.title}</strong>
                  </Link>
                  <Badge tone={goalTone(goal.status)}>{goal.status}</Badge>
                </div>
                {goal.description ? <p className="small muted">{goal.description}</p> : null}
                <ProgressBar
                  value={goal.progressPercent}
                  late={goal.overdue}
                  label={`${goal.completedTasks} of ${goal.totalTasks} tasks`}
                />
                <div className="row" style={{ marginTop: 10 }}>
                  <Badge tone={priorityTone(goal.priority)}>{goal.priority}</Badge>
                  <span className="small subtle">{goal.deadline ? `Due ${formatDate(goal.deadline)}` : 'No deadline'}</span>
                  {goal.overdue ? <Badge tone="danger">Past deadline</Badge> : null}
                </div>
                {goal.openTaskTitles.length > 0 ? (
                  <ul className="list" style={{ marginTop: 8 }}>
                    {goal.openTaskTitles.map((title) => (
                      <li key={title} className="small muted">
                        • {title}
                      </li>
                    ))}
                  </ul>
                ) : null}
                <div className="row row--end" style={{ marginTop: 10 }}>
                  <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPendingDelete(goal)}>
                    Delete
                  </button>
                </div>
              </section>
            ))}
          </div>
        )
      ) : null}

      <Modal
        title="New goal"
        open={formOpen}
        onClose={() => setFormOpen(false)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="goal-form" className="btn btn--primary" disabled={saving}>
              {saving ? 'Saving…' : 'Create goal'}
            </button>
          </>
        }
      >
        {formError ? (
          <div className="alert alert--danger" role="alert">
            {formError.message}
          </div>
        ) : null}
        <form id="goal-form" onSubmit={(event) => void save(event)} noValidate>
          <div className="field">
            <label htmlFor="goal-title">Title</label>
            <input
              id="goal-title"
              type="text"
              required
              maxLength={180}
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              aria-invalid={Boolean(fieldIssues.title)}
            />
            {fieldIssues.title ? <span className="field__error">{fieldIssues.title}</span> : null}
          </div>
          <div className="field">
            <label htmlFor="goal-description">Description</label>
            <textarea
              id="goal-description"
              value={form.description}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
            />
          </div>
          <div className="grid grid--2">
            <div className="field">
              <label htmlFor="goal-deadline">Target date</label>
              <input
                id="goal-deadline"
                type="date"
                value={form.deadline}
                onChange={(event) => setForm({ ...form, deadline: event.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="goal-priority">Priority</label>
              <select
                id="goal-priority"
                value={form.priority}
                onChange={(event) => setForm({ ...form, priority: event.target.value as Priority })}
              >
                {PRIORITIES.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this goal?"
        message={`“${pendingDelete?.title ?? ''}” will be deleted. Its tasks are kept and detached, so nothing else is lost.`}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
