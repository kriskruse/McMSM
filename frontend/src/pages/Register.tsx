import {useState} from "react";
import {useNavigate} from "react-router-dom";
import CircularSpinner from "../components/CircularSpinner.tsx";

function Register() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [passwordConfirm, setPasswordConfirm] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const submit = async (endpoint: string) => {
        setError('');
        if (!username || !password) {
            setError('Please enter a valid username and password');
            return;
        }
        if (password !== passwordConfirm) {
            setError('Passwords do not match');
            return;
        }
        setLoading(true);

        try {
            const res = await fetch(`/api/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password }),
            });
            const text = await res.text();
            if (res.ok) {
                navigate('/');
            }
            else setError(text || `${endpoint} failed`);
        } catch {
            setError('Failed to connect to backend');
        } finally {
            setLoading(false);
        }

    };

    const inputClass =
        'mt-1 block w-full rounded-md bg-white/5 px-3 py-2 text-white outline outline-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:outline-indigo-500 sm:text-sm';

    return (
        <div className="w-full max-w-sm space-y-8">
            <div className="text-center">
                <img src="https://tailwindcss.com/plus-assets/img/logos/mark.svg?color=indigo&shade=500"
                     alt="McMSM logo" className="mx-auto h-10" />
                <h2 className="mt-6 text-2xl font-bold text-white">Register an account</h2>
            </div>

            {error && <p className="text-center text-sm text-red-400">{error}</p>}

            <form onSubmit={(e) => { e.preventDefault(); submit('register'); }} className="space-y-5">
                <div>
                    <label htmlFor="username" className="block text-sm font-medium text-gray-100">Username</label>
                    <input id="username" type="text" required value={username}
                           onChange={(e) => setUsername(e.target.value)} className={inputClass} />
                </div>

                <div>
                    <label htmlFor="password" className="block text-sm font-medium text-gray-100">Password</label>
                    <input id="password" type="password" required value={password}
                           onChange={(e) => setPassword(e.target.value)} className={inputClass} />
                </div>

                <div>
                    <label htmlFor="passwordConfirm" className="block text-sm font-medium text-gray-100">Confirm Password</label>
                    <input id="passwordConfirm" type="password" required value={passwordConfirm}
                           onChange={(e) => setPasswordConfirm(e.target.value)} className={inputClass} />
                </div>

                <button type="submit" disabled={loading}
                        className="flex items-center justify-center w-full rounded-md bg-indigo-500 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-400 disabled:opacity-50"
                        aria-label="Register account">
                    {loading ? (
                        <CircularSpinner />
                    ) : (
                        'Register'
                    )}
                </button>
            </form>

        </div>
    );
}

export default Register;