import { Link, useNavigate, useParams } from 'react-router-dom';
import { taskApi } from '../api/endpoints';
import type { TaskResponse, TaskStatus } from '../api/types';
import { Badge, priorityTone, statusTone } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDate, formatDateTime } from '../format';
import { useAsync } from '../hooks/useasync';
import { useState } from 'react';

const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

export function TaskDetailPage() {
  const { id } = useParams<{ id: string }>();
  const taskId = Number(id);
  const navigate = useNavigate();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);

  const { data, loading, error, reload } = useAsync<TaskResponse>(() => taskApi.get(taskId), [taskId]);

  if (!Number.isFinite(taskId)) {
    return <ErrorState error={new Error('That task id is not a number.')} />;
  }
  if (loading) {
    return <LoadingState label="Loading task…" />;
  }
  if (error) {
    return <ErrorState error={error} onRetry={reload} />;
  }
  if (!data) {
    return null;
  }

  const changeStatus = async (status: TaskStatus) => {
    try {
      await taskApi.updateStatus(taskId, status);
      toast.success(`Status set to ${status.replace('_', ' ').toLowerCase()}`);
      reload();
    } catch {
      toast.failure('Could not change the status');
    }
  };

  const remove = async () => {
    try {
      await taskApi.remove(taskId);
      toast.success('Task deleted');
      navigate('/tasks');
    } catch {
      toast.failure('Could not delete the task');
    }
  };

  return (
    <div>
      <p className="small">
        <Link to="/tasks">← All tasks</Link>
      </p>

      <div className="page-head">
        <div>
          <h1>{data.title}</h1>
          <div className="row" style={{ marginTop: 6 }}>
            <Badge tone={statusTone(data.status)}>{data.status.replace('_', ' ')}</Badge>
            <Badge tone={priorityTone(data.priority)}>{data.priority}</Badge>
            {data.overdue ? <Badge tone="danger">Overdue</Badge> : null}
          </div>
        </div>
        <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>
          Delete
        </button>
      </div>

      <div className="grid grid--sidebar">
        <div className="stack">
          <section className="card">
            <h2 className="card__title">Notes</h2>
            {data.description ? <p>{data.description}</p> : <p className="muted">No notes on this task.</p>}
          </section>

          <section className="card">
            <h2 className="card__title">Status</h2>
            <div className="row">
              {STATUSES.map((status) => (
                <button
                  key={status}
                  type="button"
                  className={status === data.status ? 'btn btn--primary btn--sm' : 'btn btn--sm'}
                  onClick={() => void changeStatus(status)}
                  disabled={status === data.status}
                >
                  {status.replace('_', ' ')}
                </button>
              ))}
            </div>
          </section>
        </div>

        <section className="card">
          <h2 className="card__title">Details</h2>
          <dl className="kv">
            <dt>Due</dt>
            <dd>{data.dueDate ? formatDate(data.dueDate) : '—'}</dd>
            <dt>Goal</dt>
            <dd>{data.goalId ? <Link to={`/goals/${data.goalId}`}>{data.goalTitle ?? 'Goal'}</Link> : '—'}</dd>
            <dt>Tags</dt>
            <dd>{data.tags.length > 0 ? data.tags.join(', ') : '—'}</dd>
            <dt>Created</dt>
            <dd>{formatDateTime(data.createdAt)}</dd>
            <dt>Updated</dt>
            <dd>{formatDateTime(data.updatedAt)}</dd>
            <dt>Completed</dt>
            <dd>{data.completedAt ? formatDateTime(data.completedAt) : '—'}</dd>
          </dl>
        </section>
      </div>

      <ConfirmDialog
        open={confirming}
        title="Delete this task?"
        message={`“${data.title}” will be removed permanently.`}
        onConfirm={remove}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}
