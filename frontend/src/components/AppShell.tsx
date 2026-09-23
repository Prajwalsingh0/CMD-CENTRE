import { useCallback, useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { notificationApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthProvider';
import { useDataRefresh } from '../hooks/usedatarefresh';
import { CommandBar } from './CommandBar';

interface NavItem {
  to: string;
  label: string;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/tasks', label: 'Tasks' },
  { to: '/goals', label: 'Goals' },
  { to: '/documents', label: 'Documents' },
  { to: '/knowledge', label: 'Knowledge' },
  { to: '/jobs', label: 'Jobs' },
  { to: '/research', label: 'Research' },
  { to: '/activity', label: 'Activity' },
  { to: '/analytics', label: 'Analytics' },
  { to: '/notifications', label: 'Notifications' },
  { to: '/profile', label: 'Profile' },
];

/** Sidebar + top bar + command bar. Every authenticated route renders inside here. */
export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  const loadUnread = useCallback(async () => {
    try {
      const { count } = await notificationApi.unreadCount();
      setUnread(count);
    } catch {
      // A failed badge refresh must never interrupt what the user is doing.
    }
  }, []);

  useEffect(() => {
    void loadUnread();
    const timer = window.setInterval(() => void loadUnread(), 60_000);
    return () => window.clearInterval(timer);
  }, [loadUnread]);

  useDataRefresh(() => void loadUnread());

  const signOut = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app">
      <aside className={`sidebar${drawerOpen ? ' sidebar--open' : ''}`} aria-label="Primary">
        <div className="sidebar__brand">
          <strong>AI Command Center</strong>
          <span>Personal AI workspace</span>
        </div>
        <nav className="sidebar__nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `navlink${isActive ? ' navlink--active' : ''}`}
              onClick={() => setDrawerOpen(false)}
            >
              <span>{item.label}</span>
              {item.to === '/notifications' && unread > 0 ? (
                <span className="badge badge--accent" aria-label={`${unread} unread notifications`}>
                  {unread}
                </span>
              ) : null}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar__foot">
          <div className="small">{user?.displayName}</div>
          <div className="small subtle">{user?.email}</div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button
            type="button"
            className="btn btn--ghost btn--sm topbar__menu"
            onClick={() => setDrawerOpen((open) => !open)}
            aria-expanded={drawerOpen}
            aria-label="Toggle navigation"
          >
            ☰
          </button>
          <CommandBar />
        </header>

        <main className="content">
          <Outlet />
        </main>

        <footer className="content small subtle" style={{ paddingTop: 0 }}>
          <div className="row row--between">
            <span>Every figure on every screen is computed from your own rows.</span>
            <button type="button" className="btn btn--ghost btn--sm" onClick={signOut}>
              Sign out
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
