import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ToastProvider';
import { TasksPage } from '../pages/TasksPage';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  window.localStorage.clear();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('TasksPage', () => {
  it('shows an actionable empty state when the user has no tasks', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/goals')) {
        return jsonResponse([]);
      }
      if (url.includes('/api/tasks')) {
        return jsonResponse({ items: [], page: 0, size: 10, totalItems: 0, totalPages: 0, hasNext: false });
      }
      return jsonResponse({}, 404);
    });

    render(
      <MemoryRouter>
        <ToastProvider>
          <TasksPage />
        </ToastProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('No tasks match')).toBeInTheDocument();
    // Two "New task" buttons exist on purpose (page head + empty-state action), so assert the set.
    expect(screen.getAllByRole('button', { name: 'New task' }).length).toBeGreaterThan(0);
  });

  it('renders the rows returned by the API and flags an overdue task', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/goals')) {
        return jsonResponse([]);
      }
      if (url.includes('/api/tasks')) {
        return jsonResponse({
          items: [
            {
              id: 1,
              title: 'Revise SQL joins',
              description: null,
              status: 'TODO',
              priority: 'HIGH',
              dueDate: '2026-01-01',
              goalId: null,
              goalTitle: null,
              tags: ['sql'],
              overdue: true,
              createdAt: '2025-12-01T10:00:00Z',
              updatedAt: '2025-12-01T10:00:00Z',
              completedAt: null,
            },
          ],
          page: 0,
          size: 10,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
        });
      }
      return jsonResponse({}, 404);
    });

    render(
      <MemoryRouter>
        <ToastProvider>
          <TasksPage />
        </ToastProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Revise SQL joins')).toBeInTheDocument();
    expect(screen.getByText('Overdue')).toBeInTheDocument();
    // "HIGH" appears both in the row badge and in the priority filter, so assert on the set.
    expect(screen.getAllByText('HIGH').length).toBeGreaterThan(0);
    expect(screen.getByText('1 task(s) · page 1 of 1')).toBeInTheDocument();
  });
});
