import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './design/global.css';

const rootElement = document.getElementById('root');
if (rootElement === null) {
  throw new Error('找不到应用挂载节点 #root');
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
