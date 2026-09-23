import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type ToastVariant = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  variant: ToastVariant;
  title: string;
  body?: string;
}

interface ToastContextValue {
  pushToast: (toast: Omit<Toast, 'id'>) => void;
  success: (title: string, body?: string) => void;
  failure: (title: string, body?: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const pushToast = useCallback(
    (toast: Omit<Toast, 'id'>) => {
      const id = nextId++;
      setToasts((current) => [...current, { ...toast, id }]);
      window.setTimeout(() => dismiss(id), toast.variant === 'error' ? 7000 : 4000);
    },
    [dismiss],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      pushToast,
      success: (title, body) => pushToast({ variant: 'success', title, body }),
      failure: (title, body) => pushToast({ variant: 'error', title, body }),
    }),
    [pushToast],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" aria-live="polite" aria-atomic="false">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast--${toast.variant}`} role="status">
            <div className="row row--between">
              <span className="toast__title">{toast.title}</span>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss notification"
              >
                ✕
              </button>
            </div>
            {toast.body ? <div className="toast__body">{toast.body}</div> : null}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider');
  }
  return context;
}
