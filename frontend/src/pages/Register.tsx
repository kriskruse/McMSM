import {useActionState, useState} from "react";
import {useNavigate} from "react-router";
import CircularSpinner from "../components/CircularSpinner.tsx";
import McmsmLogo from "../components/McmsmLogo";
import { btn } from "../util/buttonVariants";
import { INPUT_CLASS } from "../util/styles";

function Register() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [passwordConfirm, setPasswordConfirm] = useState('');
    const navigate = useNavigate();

    const [error, submitAction, isPending] = useActionState<string, FormData>(
        async (_previousError) => {
            if (!username || !password) {
                return 'Please enter a valid username and password';
            }
            if (password !== passwordConfirm) {
                return 'Passwords do not match';
            }
            try {
                const res = await fetch('/api/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password }),
                });
                const text = await res.text();
                if (res.ok) {
                    navigate('/');
                    return '';
                }
                try {
                    const json = JSON.parse(text);
                    return json.message || 'Registration failed';
                } catch {
                    return text || 'Registration failed';
                }
            } catch {
                return 'Failed to connect to backend';
            }
        },
        '',
    );

    return (
        <div className="w-full max-w-sm space-y-8">
            <div className="text-center">
                <McmsmLogo className="mx-auto h-10 w-10" />
                <h2 className="mt-6 text-2xl font-bold text-white">Register an account</h2>
            </div>

            {error && <p className="text-center text-sm text-red-400">{error}</p>}

            <form action={submitAction} className="space-y-5">
                <div>
                    <label htmlFor="username" className="block text-sm font-medium text-gray-100">Username</label>
                    <input id="username" type="text" required value={username}
                           onChange={(e) => setUsername(e.target.value)} className={INPUT_CLASS} />
                </div>

                <div>
                    <label htmlFor="password" className="block text-sm font-medium text-gray-100">Password</label>
                    <input id="password" type="password" required value={password}
                           onChange={(e) => setPassword(e.target.value)} className={INPUT_CLASS} />
                </div>

                <div>
                    <label htmlFor="passwordConfirm" className="block text-sm font-medium text-gray-100">Confirm Password</label>
                    <input id="passwordConfirm" type="password" required value={passwordConfirm}
                           onChange={(e) => setPasswordConfirm(e.target.value)} className={INPUT_CLASS} />
                </div>

                <button type="submit" disabled={isPending}
                        className={`${btn('primary')} w-full`}
                        aria-label="Register account">
                    {isPending ? (
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