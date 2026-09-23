import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './styles.css';

const container = document.getElementById('root');

if (!container) {
  // Failing loudly beats rendering nothing and leaving the user staring at a blank page.
  throw new Error('The #root container is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
