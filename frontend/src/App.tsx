import {
    BrowserRouter as Router,
    Routes,
    Route,
} from "react-router";

import ErrorBoundary from "./components/ErrorBoundary.tsx";
import ToastContainer from "./components/ToastContainer";
import { ToastProvider } from "./hooks/useToast";
import Login from "./pages/Login.tsx";
import Register from "./pages/Register.tsx";
import Home from "./pages/Home.tsx";


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
                </Routes>
                </ErrorBoundary>
            </Router>
            <ToastContainer />
        </ToastProvider>
    );
}

export default App
