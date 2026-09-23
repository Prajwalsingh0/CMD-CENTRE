import { Link, useParams } from 'react-router-dom';
import { goalApi, taskApi } from '../api/endpoints';
import type { GoalResponse, PageResponse, TaskResponse } from '../api/types';
import { Badge, goalTone, priorityTone, statusTone } from '../components/Badge';
import { ErrorState, LoadingState } from '../components/LoadingState';
import { ProgressBar } from '../components/ProgressBar';
import { useToast } from '../components/ToastProvider';
import { formatDate, formatDateTime } from '../format';
import { useAsync } from '../hooks/useasync';

export function GoalDetailPage() {
  const { id } = useParams<{ id: string }>();
  const goalId = Number(id);
  const toast = useToast();

  const goal = useAsync<GoalResponse>(() => goalApi.get(goalId), [goalId]);
  const tasks = useAsync<PageResponse<TaskResponse>>(
    () => taskApi.list({ goalId, size: 100 }),
    [goalId],
  );

  if (!Number.isFinite(goalId)) {
    return <ErrorState error={new Error('That goal id is not a number.')} />;
  }
  if (goal.loading) {
    return <LoadingState label="Loading goal…" />;
  }
  if (goal.error) {
    return <ErrorState error={goal.error} onRetry={goal.reload} />;
  }
  if (!goal.data) {
    return null;
  }

  const data = goal.data;

  const refreshStatus = async () => {
    try {
      const updated = await goalApi.refreshStatus(goalId);
      toast.success(
        updated.status === 'COMPLETED' ? 'Goal marked as completed' : 'Goal is still in progress',
        updated.status === 'COMPLETED' ? undefined : 'Some attached tasks are not finished yet.',
      );
      goal.reload();
      tasks.reload();
    } catch {
      toast.failure('Could not refresh the goal status');
    }
  };

  return (
    <div>
      <p className="small">
        <Link to="/goals">← All goals</Link>
      </p>

      <div className="page-head">
        <div>
          <h1>{data.title}</h1>
          <div className="row" style={{ marginTop: 6 }}>
            <Badge tone={goalTone(data.status)}>{data.status}</Badge>
            <Badge tone={priorityTone(data.priority)}>{data.priority}</Badge>
            {data.overdue ? <Badge tone="danger">Past deadline</Badge> : null}
          </div>
        </div>
        <button type="button" className="btn" onClick={() => void refreshStatus()}>
          Refresh status
        </button>
      </div>

      <div className="grid grid--sidebar">
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Attached tasks</h2>
            <span className="card__hint">{data.totalTasks} total</span>
          </div>
          {tasks.loading ? <LoadingState label="Loading tasks…" rows={3} /> : null}
          {tasks.error ? <ErrorState error={tasks.error} onRetry={tasks.reload} /> : null}
          {tasks.data && tasks.data.items.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No tasks are attached to this goal yet. Create one from the{' '}
              <Link to="/tasks">tasks page</Link> and pick this goal.
            </p>
          ) : null}
          {tasks.data && tasks.data.items.length > 0 ? (
            <ul className="list">
              {tasks.data.items.map((task) => (
                <li key={task.id} className="list__item">
                  <div className="list__main">
                    <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                    <div className="list__meta">
                      {task.dueDate ? `Due ${formatDate(task.dueDate)}` : 'No due date'}
                      {task.overdue ? ' · overdue' : ''}
                    </div>
                  </div>
                  <Badge tone={statusTone(task.status)}>{task.status.replace('_', ' ')}</Badge>
                </li>
              ))}
            </ul>
          ) : null}
        </section>

        <div className="stack">
          <section className="card">
            <h2 className="card__title">Progress</h2>
            <ProgressBar value={data.progressPercent} late={data.overdue} label={`${data.completedTasks} of ${data.totalTasks} tasks complete`} />
            <p className="small muted" style={{ marginTop: 8, marginBottom: 0 }}>
              Cancelled tasks leave the denominator, so the goal can still reach 100%.
            </p>
          </section>

          <section className="card">
            <h2 className="card__title">Details</h2>
            <dl className="kv">
              <dt>Target date</dt>
              <dd>{data.deadline ? formatDate(data.deadline) : '—'}</dd>
              <dt>Created</dt>
              <dd>{formatDateTime(data.createdAt)}</dd>
              <dt>Updated</dt>
              <dd>{formatDateTime(data.updatedAt)}</dd>
            </dl>
            {data.description ? (
              <>
                <hr className="divider" />
                <p style={{ margin: 0 }}>{data.description}</p>
              </>
            ) : null}
            {data.notes ? (
              <>
                <hr className="divider" />
                <p className="small muted" style={{ margin: 0 }}>
                  {data.notes}
                </p>
              </>
            ) : null}
          </section>
        </div>
      </div>
    </div>
  );
}
