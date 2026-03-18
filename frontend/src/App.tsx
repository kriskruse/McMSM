import {
    BrowserRouter as Router,
    Routes,
    Route,
} from "react-router-dom";

import Login from "./pages/Login.tsx";
import Register from "./pages/Register.tsx";
import Home from "./pages/Home.tsx";


function App() {

    return (
        <Router>
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
        </Router>
    );
}

export default App
