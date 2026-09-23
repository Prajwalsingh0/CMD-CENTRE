import { useState } from 'react';
import { ApiError } from '../api/client';
import { notificationApi } from '../api/endpoints';
import type { NotificationResponse } from '../api/types';
import { Badge } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime, humanise, relativeTime } from '../format';
import { notifyDataChanged } from '../hooks/usedatarefresh';
import { useAsync } from '../hooks/useasync';

function toneFor(eventType: string): 'danger' | 'warning' | 'success' | 'info' | 'neutral' {
  switch (eventType) {
    case 'OVERDUE_TASK':
      return 'danger';
    case 'DEADLINE_SOON':
    case 'GOAL_DEADLINE':
      return 'warning';
    case 'GOAL_COMPLETED':
      return 'success';
    case 'AI_ACTION':
      return 'info';
    default:
      return 'neutral';
  }
}

export function NotificationsPage() {
  const toast = useToast();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<NotificationResponse | null>(null);

  const { data, loading, error, reload } = useAsync<NotificationResponse[]>(
    () => notificationApi.list(unreadOnly),
    [unreadOnly],
  );

  const refresh = async () => {
    try {
      const list = await notificationApi.refresh();
      toast.success('Checked for new notifications', `${list.filter((item) => !item.read).length} unread.`);
      reload();
      notifyDataChanged();
    } catch (caught) {
      toast.failure('Could not refresh notifications', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  const markRead = async (notification: NotificationResponse) => {
    try {
      await notificationApi.markRead(notification.id);
      reload();
      notifyDataChanged();
    } catch {
      toast.failure('Could not mark it as read');
    }
  };

  const markAllRead = async () => {
    try {
      const result = await notificationApi.markAllRead();
      toast.success(`Marked ${result.updated} notification(s) as read`);
      reload();
      notifyDataChanged();
    } catch {
      toast.failure('Could not mark everything as read');
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) {
      return;
    }
    try {
      await notificationApi.remove(pendingDelete.id);
      setPendingDelete(null);
      reload();
      notifyDataChanged();
    } catch {
      toast.failure('Could not delete the notification');
    }
  };

  const unreadCount = (data ?? []).filter((item) => !item.read).length;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Notifications</h1>
          <p>
            Derived from your own rows — overdue tasks, approaching deadlines and completed goals.
            Repeated refreshes never duplicate anything.
          </p>
        </div>
        <div className="row">
          <button type="button" className="btn" onClick={() => void refresh()}>
            Check for updates
          </button>
          <button type="button" className="btn" onClick={() => void markAllRead()} disabled={unreadCount === 0 && !unreadOnly}>
            Mark all read
          </button>
        </div>
      </div>

      <div className="row" style={{ marginBottom: 16 }}>
        <button type="button" className={unreadOnly ? 'btn btn--sm' : 'btn btn--primary btn--sm'} onClick={() => setUnreadOnly(false)}>
          All
        </button>
        <button type="button" className={unreadOnly ? 'btn btn--primary btn--sm' : 'btn btn--sm'} onClick={() => setUnreadOnly(true)}>
          Unread only
        </button>
      </div>

      {loading ? <LoadingState label="Loading notifications…" /> : null}
      {error ? <ErrorState error={error} onRetry={reload} /> : null}

      {!loading && !error && data ? (
        data.length === 0 ? (
          <EmptyState
            title={unreadOnly ? 'Nothing unread' : 'No notifications'}
            message="Use “Check for updates” to derive notifications from your current tasks and goals."
          />
        ) : (
          <ul className="list">
            {data.map((notification) => (
              <li key={notification.id} className="card" style={{ marginBottom: 8 }}>
                <div className="row row--between">
                  <div className="row">
                    <Badge tone={toneFor(notification.eventType)}>{humanise(notification.eventType)}</Badge>
                    {!notification.read ? <Badge tone="accent">Unread</Badge> : null}
                    <strong>{notification.title}</strong>
                  </div>
                  <span className="small subtle" title={formatDateTime(notification.createdAt)}>
                    {relativeTime(notification.createdAt)}
                  </span>
                </div>
                {notification.message ? <p className="small muted" style={{ margin: '6px 0 0' }}>{notification.message}</p> : null}
                <div className="row row--end" style={{ marginTop: 8 }}>
                  {!notification.read ? (
                    <button type="button" className="btn btn--sm" onClick={() => void markRead(notification)}>
                      Mark read
                    </button>
                  ) : null}
                  <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPendingDelete(notification)}>
                    Delete
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )
      ) : null}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this notification?"
        message="Only the notification is removed — the task or goal it refers to is untouched."
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
