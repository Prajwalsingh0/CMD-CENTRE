import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { goalApi, taskApi } from '../api/endpoints';
import type { GoalResponse, PageResponse, Priority, TaskResponse, TaskStatus } from '../api/types';
import { Badge, priorityTone } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { Modal } from '../components/Modal';
import { useToast } from '../components/ToastProvider';
import { formatDate } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
const SORTS = [
  { value: '', label: 'Default' },
  { value: 'dueDate,asc', label: 'Due date (soonest)' },
  { value: 'dueDate,desc', label: 'Due date (latest)' },
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'title,asc', label: 'Title (A–Z)' },
  { value: 'priority,desc', label: 'Priority (high first)' },
];

interface FormState {
  id: number | null;
  title: string;
  description: string;
  status: TaskStatus;
  priority: Priority;
  dueDate: string;
  goalId: string;
  tags: string;
}

const emptyForm: FormState = {
  id: null,
  title: '',
  description: '',
  status: 'TODO',
  priority: 'MEDIUM',
  dueDate: '',
  goalId: '',
  tags: '',
};

export function TasksPage() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [form, setForm] = useState<FormState>(emptyForm);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState<ApiError | null>(null);
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<TaskResponse | null>(null);

  const status = (params.get('status') ?? '') as TaskStatus | '';
  const priority = (params.get('priority') ?? '') as Priority | '';
  const search = params.get('search') ?? '';
  const sort = params.get('sort') ?? '';
  const overdueOnly = params.get('overdueOnly') === 'true';
  const page = Number(params.get('page') ?? '0');

  const [searchDraft, setSearchDraft] = useState(search);

  const load = useCallback(
    () => taskApi.list({ status, priority, search, sort, overdueOnly, page, size: 10 }),
    [status, priority, search, sort, overdueOnly, page],
  );
  const { data, loading, error, reload } = useAsync<PageResponse<TaskResponse>>(load, [load]);
  useDataRefresh(reload);

  const goals = useAsync<GoalResponse[]>(() => goalApi.list(), []);

  useEffect(() => {
    setSearchDraft(search);
  }, [search]);

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    if (key !== 'page') {
      next.delete('page');
    }
    setParams(next, { replace: true });
  };

  const openCreate = () => {
    setForm({ ...emptyForm, goalId: params.get('goalId') ?? '' });
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (task: TaskResponse) => {
    setForm({
      id: task.id,
      title: task.title,
      description: task.description ?? '',
      status: task.status,
      priority: task.priority,
      dueDate: task.dueDate ?? '',
      goalId: task.goalId ? String(task.goalId) : '',
      tags: task.tags.join(', '),
    });
    setFormError(null);
    setFormOpen(true);
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    const payload = {
      title: form.title.trim(),
      description: form.description.trim() === '' ? null : form.description.trim(),
      status: form.status,
      priority: form.priority,
      dueDate: form.dueDate === '' ? null : form.dueDate,
      goalId: form.goalId === '' ? null : Number(form.goalId),
      tags: form.tags
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0),
    };
    try {
      if (form.id === null) {
        await taskApi.create(payload);
        toast.success('Task created');
      } else {
        await taskApi.update(form.id, payload);
        toast.success('Task updated');
      }
      setFormOpen(false);
      reload();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught : new ApiError(0, 'UNKNOWN', 'Could not save the task.'));
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (task: TaskResponse, next: TaskStatus) => {
    try {
      await taskApi.updateStatus(task.id, next);
      toast.success(`“${task.title}” is now ${next.replace('_', ' ').toLowerCase()}`);
      reload();
    } catch (caught) {
      toast.failure('Could not change the status', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) {
      return;
    }
    try {
      await taskApi.remove(pendingDelete.id);
      toast.success('Task deleted');
      setPendingDelete(null);
      reload();
    } catch (caught) {
      toast.failure('Could not delete the task', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  const fieldIssues = formError?.fieldIssues ?? {};

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Tasks</h1>
          <p>Filter, sort and update everything you have committed to.</p>
        </div>
        <button type="button" className="btn btn--primary" onClick={openCreate}>
          New task
        </button>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="filters">
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="filter-status">Status</label>
            <select id="filter-status" value={status} onChange={(event) => updateParam('status', event.target.value)}>
              <option value="">Any</option>
              {STATUSES.map((value) => (
                <option key={value} value={value}>
                  {value.replace('_', ' ')}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="filter-priority">Priority</label>
            <select id="filter-priority" value={priority} onChange={(event) => updateParam('priority', event.target.value)}>
              <option value="">Any</option>
              {PRIORITIES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="filter-sort">Sort</label>
            <select id="filter-sort" value={sort} onChange={(event) => updateParam('sort', event.target.value)}>
              {SORTS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="filter-search">Search</label>
            <div className="row" style={{ flexWrap: 'nowrap' }}>
              <input
                id="filter-search"
                type="text"
                value={searchDraft}
                placeholder="Title, notes or tag"
                onChange={(event) => setSearchDraft(event.target.value)}
                onKeyDown={(event) => event.key === 'Enter' && updateParam('search', searchDraft)}
              />
              <button type="button" className="btn" onClick={() => updateParam('search', searchDraft)}>
                Go
              </button>
            </div>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="filter-overdue">Overdue only</label>
            <select
              id="filter-overdue"
              value={overdueOnly ? 'true' : 'false'}
              onChange={(event) => updateParam('overdueOnly', event.target.value === 'true' ? 'true' : '')}
            >
              <option value="false">No</option>
              <option value="true">Yes</option>
            </select>
          </div>
        </div>
        {(status || priority || search || overdueOnly || sort) && (
          <div style={{ marginTop: 12 }}>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
              Clear filters
            </button>
          </div>
        )}
      </div>

      {loading ? <LoadingState label="Loading tasks…" rows={5} /> : null}
      {error ? <ErrorState error={error} onRetry={reload} /> : null}

      {!loading && !error && data ? (
        data.items.length === 0 ? (
          <EmptyState
            title="No tasks match"
            message="Create a task, or relax the filters above."
            action={
              <button type="button" className="btn btn--primary btn--sm" onClick={openCreate}>
                New task
              </button>
            }
          />
        ) : (
          <>
            <div className="card card--flush">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Task</th>
                    <th scope="col">Status</th>
                    <th scope="col">Priority</th>
                    <th scope="col">Due</th>
                    <th scope="col">Goal</th>
                    <th scope="col" className="numeric">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((task) => (
                    <tr key={task.id}>
                      <td>
                        <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                        {task.overdue ? (
                          <>
                            {' '}
                            <Badge tone="danger">Overdue</Badge>
                          </>
                        ) : null}
                        {task.tags.length > 0 ? (
                          <div style={{ marginTop: 4 }}>
                            {task.tags.map((tag) => (
                              <span key={tag} className="tag" style={{ marginRight: 4 }}>
                                {tag}
                              </span>
                            ))}
                          </div>
                        ) : null}
                      </td>
                      <td>
                        <label className="sr-only" htmlFor={`status-${task.id}`} style={{ position: 'absolute', left: -9999 }}>
                          Status for {task.title}
                        </label>
                        <select
                          id={`status-${task.id}`}
                          value={task.status}
                          onChange={(event) => void changeStatus(task, event.target.value as TaskStatus)}
                        >
                          {STATUSES.map((value) => (
                            <option key={value} value={value}>
                              {value.replace('_', ' ')}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <Badge tone={priorityTone(task.priority)}>{task.priority}</Badge>
                      </td>
                      <td>{task.dueDate ? formatDate(task.dueDate) : '—'}</td>
                      <td>
                        {task.goalId ? <Link to={`/goals/${task.goalId}`}>{task.goalTitle ?? 'Goal'}</Link> : '—'}
                      </td>
                      <td className="numeric">
                        <div className="row row--end" style={{ flexWrap: 'nowrap' }}>
                          <button type="button" className="btn btn--sm" onClick={() => openEdit(task)}>
                            Edit
                          </button>
                          <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPendingDelete(task)}>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="row row--between" style={{ marginTop: 12 }}>
              <span className="small subtle">
                {data.totalItems} task(s) · page {data.page + 1} of {Math.max(1, data.totalPages)}
              </span>
              <div className="row">
                <button
                  type="button"
                  className="btn btn--sm"
                  disabled={data.page === 0}
                  onClick={() => updateParam('page', String(data.page - 1))}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="btn btn--sm"
                  disabled={!data.hasNext}
                  onClick={() => updateParam('page', String(data.page + 1))}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )
      ) : null}

      <Modal
        title={form.id === null ? 'New task' : 'Edit task'}
        open={formOpen}
        onClose={() => setFormOpen(false)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </button>
            <button type="submit" form="task-form" className="btn btn--primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save task'}
            </button>
          </>
        }
      >
        {formError ? (
          <div className="alert alert--danger" role="alert">
            {formError.message}
          </div>
        ) : null}
        <form id="task-form" onSubmit={(event) => void save(event)} noValidate>
          <div className="field">
            <label htmlFor="task-title">Title</label>
            <input
              id="task-title"
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
            <label htmlFor="task-description">Notes</label>
            <textarea
              id="task-description"
              value={form.description}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
            />
          </div>
          <div className="grid grid--2">
            <div className="field">
              <label htmlFor="task-status">Status</label>
              <select
                id="task-status"
                value={form.status}
                onChange={(event) => setForm({ ...form, status: event.target.value as TaskStatus })}
              >
                {STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {value.replace('_', ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="task-priority">Priority</label>
              <select
                id="task-priority"
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
            <div className="field">
              <label htmlFor="task-due">Due date</label>
              <input
                id="task-due"
                type="date"
                value={form.dueDate}
                onChange={(event) => setForm({ ...form, dueDate: event.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="task-goal">Goal</label>
              <select
                id="task-goal"
                value={form.goalId}
                onChange={(event) => setForm({ ...form, goalId: event.target.value })}
              >
                <option value="">No goal</option>
                {(goals.data ?? []).map((goal) => (
                  <option key={goal.id} value={goal.id}>
                    {goal.title}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="field">
            <label htmlFor="task-tags">Tags</label>
            <input
              id="task-tags"
              type="text"
              placeholder="comma, separated"
              value={form.tags}
              onChange={(event) => setForm({ ...form, tags: event.target.value })}
            />
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this task?"
        message={`“${pendingDelete?.title ?? ''}” will be removed permanently.`}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
