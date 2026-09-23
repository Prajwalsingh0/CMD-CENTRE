import { useEffect } from 'react';

/**
 * Lets a page re-fetch after something else changed the data — currently the global command bar,
 * which may have created a goal or a task from anywhere in the app.
 *
 * A window event keeps the command bar and the pages from having to know about each other, and it
 * costs nothing: no store, no context threading, no extra dependency.
 */
const EVENT = 'aicc:data-changed';

export function notifyDataChanged(): void {
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function useDataRefresh(callback: () => void): void {
  useEffect(() => {
    const handler = () => callback();
    window.addEventListener(EVENT, handler);
    return () => window.removeEventListener(EVENT, handler);
    // The callback is intentionally not a dependency: callers pass an inline function and
    // re-subscribing on every render would be pointless churn.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
