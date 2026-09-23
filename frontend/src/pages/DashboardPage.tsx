import { Link } from 'react-router-dom';
import { dashboardApi } from '../api/endpoints';
import type { DashboardResponse, TaskResponse } from '../api/types';
import { activityTone, Badge, goalTone, priorityTone, statusTone } from '../components/Badge';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { ProgressBar } from '../components/ProgressBar';
import { StatCard } from '../components/StatCard';
import { formatDate, formatDateTime, relativeTime } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

function TaskRow({ task }: { task: TaskResponse }) {
  return (
    <li className="list__item">
      <div className="list__main">
        <div className="list__title">
          {task.title} {task.overdue ? <Badge tone="danger">Overdue</Badge> : null}
        </div>
        <div className="list__meta">
          {task.dueDate ? `Due ${formatDate(task.dueDate)}` : 'No due date'}
          {task.goalTitle ? ` · ${task.goalTitle}` : ''}
        </div>
      </div>
      <Badge tone={priorityTone(task.priority)}>{task.priority}</Badge>
      <Badge tone={statusTone(task.status)}>{task.status.replace('_', ' ')}</Badge>
    </li>
  );
}

export function DashboardPage() {
  const { data, loading, error, reload } = useAsync<DashboardResponse>(() => dashboardApi.load(), []);
  useDataRefresh(reload);

  if (loading) {
    return <LoadingState label="Loading your command centre…" rows={4} />;
  }
  if (error) {
    return <ErrorState error={error} onRetry={reload} />;
  }
  if (!data) {
    return <EmptyState title="Nothing to show yet" message="Start by creating a goal or a task." />;
  }

  const { stats } = data;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Welcome back, {data.displayName}</h1>
          <p>Everything below is computed from your own rows — nothing here is a placeholder.</p>
        </div>
      </div>

      <div className="grid grid--4" style={{ marginBottom: 16 }}>
        <StatCard
          label="Completion rate"
          value={`${stats.completionRate}%`}
          foot={`${stats.completedTasks} of ${stats.totalTasks} tasks`}
          tone={stats.completionRate >= 50 ? 'success' : 'default'}
        />
        <StatCard label="Open tasks" value={stats.openTasks} foot={`${stats.completedLast7Days} completed this week`} />
        <StatCard label="Overdue" value={stats.overdueTasks} tone={stats.overdueTasks > 0 ? 'danger' : 'default'} foot="Past their due date" />
        <StatCard label="Active goals" value={stats.activeGoals} foot={`${stats.documentsIndexed} documents indexed`} />
      </div>

      <div className="grid grid--sidebar">
        <div className="stack">
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Today's focus</h2>
              <Link className="small" to="/tasks">
                All tasks
              </Link>
            </div>
            {data.todayTasks.length === 0 ? (
              <EmptyState
                title="Nothing due today"
                message="Tasks due today and anything already overdue appear here."
                action={
                  <Link className="btn btn--sm" to="/tasks">
                    Plan something
                  </Link>
                }
              />
            ) : (
              <ul className="list">
                {data.todayTasks.map((task) => (
                  <TaskRow key={task.id} task={task} />
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Overdue</h2>
              <span className="card__hint">{data.overdueTasks.length} item(s)</span>
            </div>
            {data.overdueTasks.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Nothing is overdue. Good.
              </p>
            ) : (
              <ul className="list">
                {data.overdueTasks.map((task) => (
                  <TaskRow key={task.id} task={task} />
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Next seven days</h2>
            </div>
            {data.upcomingTasks.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                No open task has a due date in the next week.
              </p>
            ) : (
              <ul className="list">
                {data.upcomingTasks.map((task) => (
                  <TaskRow key={task.id} task={task} />
                ))}
              </ul>
            )}
          </section>
        </div>

        <div className="stack">
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Active goals</h2>
              <Link className="small" to="/goals">
                All goals
              </Link>
            </div>
            {data.activeGoals.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                No active goals yet. The command bar can create one with a full plan.
              </p>
            ) : (
              <div className="stack">
                {data.activeGoals.map((goal) => (
                  <div key={goal.id}>
                    <div className="row row--between">
                      <Link to={`/goals/${goal.id}`}>{goal.title}</Link>
                      <Badge tone={goalTone(goal.status)}>{goal.status}</Badge>
                    </div>
                    <ProgressBar
                      value={goal.progressPercent}
                      late={goal.overdue}
                      label={`${goal.completedTasks}/${goal.totalTasks} tasks${goal.deadline ? ` · due ${formatDate(goal.deadline)}` : ''}`}
                    />
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Upcoming deadlines</h2>
            </div>
            {data.upcomingDeadlines.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Nothing scheduled ahead.
              </p>
            ) : (
              <ul className="list">
                {data.upcomingDeadlines.map((deadline) => (
                  <li key={`${deadline.kind}-${deadline.refId}`} className="list__item">
                    <div className="list__main">
                      <div className="list__title">{deadline.title}</div>
                      <div className="list__meta">
                        {deadline.kind} · {formatDate(deadline.date)}
                      </div>
                    </div>
                    <Badge tone={deadline.daysUntil <= 2 ? 'warning' : 'neutral'}>
                      {deadline.daysUntil === 0 ? 'today' : `${deadline.daysUntil}d`}
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Recent AI activity</h2>
              <Link className="small" to="/activity">
                Full history
              </Link>
            </div>
            {data.recentAiActivity.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Nothing yet. Try “Show my stats” in the command bar.
              </p>
            ) : (
              <ul className="list">
                {data.recentAiActivity.map((activity) => (
                  <li key={activity.id} className="list__item">
                    <div className="list__main">
                      <div className="list__title">{activity.command}</div>
                      <div className="list__meta">
                        {relativeTime(activity.createdAt)} · {activity.resultSummary}
                      </div>
                    </div>
                    <Badge tone={activityTone(activity.status)}>{activity.status}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Recent documents</h2>
              <Link className="small" to="/documents">
                All documents
              </Link>
            </div>
            {data.recentDocuments.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Upload a PDF, TXT or Markdown file to build your knowledge base.
              </p>
            ) : (
              <ul className="list">
                {data.recentDocuments.map((document) => (
                  <li key={document.id} className="list__item">
                    <div className="list__main">
                      <Link to={`/documents/${document.id}`}>{document.name}</Link>
                      <div className="list__meta">
                        {document.chunkCount} passage(s) · {document.status}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Job analyses</h2>
              <Link className="small" to="/jobs">
                Analyse a description
              </Link>
            </div>
            {data.recentJobAnalyses.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Paste a job description to see your skill gaps.
              </p>
            ) : (
              <ul className="list">
                {data.recentJobAnalyses.map((job) => (
                  <li key={job.id} className="list__item">
                    <div className="list__main">
                      <Link to={`/jobs/${job.id}`}>{job.jobTitle ?? 'Untitled role'}</Link>
                      <div className="list__meta">
                        {job.company ?? 'Unknown company'} · {formatDateTime(job.createdAt)}
                      </div>
                    </div>
                    <Badge tone={job.matchScore >= 70 ? 'success' : job.matchScore >= 40 ? 'warning' : 'danger'}>
                      {job.matchScore}%
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {data.unreadNotifications.length > 0 ? (
            <section className="card">
              <div className="card__head">
                <h2 className="card__title">Unread notifications</h2>
                <Link className="small" to="/notifications">
                  {data.unreadNotificationCount} unread
                </Link>
              </div>
              <ul className="list">
                {data.unreadNotifications.map((notification) => (
                  <li key={notification.id} className="list__item">
                    <div className="list__main">
                      <div className="list__title">{notification.title}</div>
                      <div className="list__meta">{notification.message}</div>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      </div>
    </div>
  );
}
