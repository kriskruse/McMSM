import { useActionState, useState } from 'react';
import {NavLink, useNavigate} from "react-router";
import CircularSpinner from "../components/CircularSpinner.tsx";
import McmsmLogo from "../components/McmsmLogo";
import type { LoginRequestDto, LoginResponseDto } from "../dto";
import { btn } from "../util/buttonVariants";
import { INPUT_CLASS } from "../util/styles";

function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();

    const [error, submitAction, isPending] = useActionState<string, FormData>(
        async (_previousError) => {
            if (!username || !password) {
                return 'Please enter a valid username and password';
            }
            try {
                const loginRequest: LoginRequestDto = { username, password };
                const res = await fetch('/api/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(loginRequest),
                });
                const responseDto: LoginResponseDto = await res.json();
                if (res.ok) {
                    navigate('/home');
                    return '';
                }
                return responseDto.message;
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
                <h2 className="mt-6 text-2xl font-bold text-white">Sign in to your account</h2>
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

                <button type="submit" disabled={isPending}
                        className={`${btn('primary')} w-full`}
                        aria-label="Sign in">
                    {isPending ? (
                        <CircularSpinner />
                    ) : (
                        'Sign in'
                    )}
                </button>
            </form>

            <p className="text-center text-sm text-gray-400">
                Not a member?{' '}
                <NavLink to="/register"
                         className="font-semibold text-indigo-400 hover:text-indigo-300">
                    Register
                </NavLink>
            </p>
        </div>
    );
}

export default Login;
