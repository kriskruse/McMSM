import { BrowserRouter as Router, Routes, Route } from 'react-router';

import ErrorBoundary from './components/ErrorBoundary.tsx';
import ToastContainer from './components/ToastContainer';
import { ToastProvider } from './hooks/useToast';
import Login from './pages/Login.tsx';
import Register from './pages/Register.tsx';
import Home from './pages/Home.tsx';
import Settings from './pages/Settings.tsx';
import ConfigEditor from './pages/ConfigEditor.tsx';

function App() {
    return (
        <ToastProvider>
            <Router>
                <ErrorBoundary>
                    <Routes>
                        <Route
                            path="/"
                            element={
                                <div className="auth-shell">
                                    <Login />
                                </div>
                            }
                        />
                        <Route
                            path="/register"
                            element={
                                <div className="auth-shell">
                                    <Register />
                                </div>
                            }
                        />
                        <Route path="/home" element={<Home />} />
                        <Route path="/settings" element={<Settings />} />
                        <Route path="/packs/:packId/config" element={<ConfigEditor />} />
                    </Routes>
                </ErrorBoundary>
            </Router>
            <ToastContainer />
        </ToastProvider>
    );
}

export default App;
