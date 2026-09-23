import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CommandBar } from '../components/CommandBar';

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

describe('CommandBar', () => {
  it('renders a rejected command as a warning and states that nothing changed', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/ai/examples')) {
        return jsonResponse([]);
      }
      if (url.includes('/api/ai/command')) {
        return jsonResponse({
          activityId: 42,
          command: 'make me a sandwich',
          intent: 'UNKNOWN',
          status: 'REJECTED',
          summary: 'No supported action matched this command.',
          steps: ['No action was taken'],
          data: {},
          parsedBy: 'heuristic',
          rationale: 'Nothing in the vocabulary fits.',
          provider: 'local',
        });
      }
      return jsonResponse({ error: 'NOT_FOUND', message: 'unexpected call' }, 404);
    });

    render(<CommandBar />);

    await userEvent.type(screen.getByLabelText('AI command'), 'make me a sandwich');
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));

    expect(await screen.findByText('REJECTED')).toBeInTheDocument();
    expect(screen.getByText(/Nothing was changed/)).toBeInTheDocument();
    expect(screen.getByText('No supported action matched this command.')).toBeInTheDocument();
    expect(screen.getByText('UNKNOWN')).toBeInTheDocument();
  });

  it('renders a successful command with its step checklist', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/ai/examples')) {
        return jsonResponse(['Create a task to study Spring Boot tomorrow']);
      }
      if (url.includes('/api/ai/command')) {
        return jsonResponse({
          activityId: 7,
          command: 'create a task',
          intent: 'CREATE_TASK',
          status: 'SUCCESS',
          summary: 'Created task "study Spring Boot"',
          steps: ['Created task "study Spring Boot"', 'Due 2026-03-05'],
          data: {},
          parsedBy: 'heuristic',
          rationale: 'The command asks to create a task',
          provider: 'local',
        });
      }
      return jsonResponse({}, 404);
    });

    render(<CommandBar />);

    await userEvent.type(screen.getByLabelText('AI command'), 'create a task');
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));

    expect(await screen.findByText('SUCCESS')).toBeInTheDocument();
    expect(screen.getByText('Due 2026-03-05')).toBeInTheDocument();
    expect(screen.queryByText(/Nothing was changed/)).not.toBeInTheDocument();
  });

  it('surfaces a transport failure without crashing the bar', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/ai/examples')) {
        return jsonResponse([]);
      }
      throw new TypeError('fetch failed');
    });

    render(<CommandBar />);

    await userEvent.type(screen.getByLabelText('AI command'), 'show my stats');
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));

    expect(await screen.findByText(/Could not reach the server/)).toBeInTheDocument();
  });
});
