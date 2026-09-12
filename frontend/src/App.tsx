import { Link, Route, Routes } from 'react-router';

export function App() {
  return (
    <main>
      <h1>Lang API</h1>
      <nav>
        <Link to="/dashboard">控制台</Link>
      </nav>
      <Routes>
        <Route path="/" element={<p>工程基线运行正常。</p>} />
        <Route path="/dashboard" element={<p>控制台占位页。</p>} />
      </Routes>
    </main>
  );
}
