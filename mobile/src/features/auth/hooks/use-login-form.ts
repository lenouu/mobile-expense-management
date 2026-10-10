import { useCallback, useRef, useState } from 'react';

import { ApiRequestError, login } from '@/features/auth/lib/api-client';
import {
  EMPTY_LOGIN_FORM,
  validateLoginForm,
  type LoginErrors,
  type LoginForm,
} from '@/features/auth/lib/login';
import { describeError } from '@/utils/errors';
import type { Session } from '@/types/api';

/**
 * Credentials, validation and submission for the login screen.
 *
 * Kept out of the screen for the same reason as `useSignUpForm`: the screen describes layout,
 * and the one interesting decision - what a failed credential check means to the user - is
 * readable in a single function.
 */
export function useLoginForm(onAuthenticated: (session: Session) => void) {
  const [form, setForm] = useState<LoginForm>(EMPTY_LOGIN_FORM);
  const [errors, setErrors] = useState<LoginErrors>({});
  const [submitting, setSubmitting] = useState(false);

  // Read through a ref on submit so a keystroke racing the tap is never sent stale, and so the
  // callback identity stays stable across renders.
  const formRef = useRef(form);
  formRef.current = form;

  const setField = useCallback(<K extends keyof LoginForm>(field: K, value: LoginForm[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field] && !current.form) return current;
      const next = { ...current };
      delete next[field];
      delete next.form;
      return next;
    });
  }, []);

  const submit = useCallback(async () => {
    const values = formRef.current;

    const clientErrors = validateLoginForm(values);
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      return;
    }

    setSubmitting(true);
    setErrors({});

    try {
      // The backend accepts either identifier, so the form does not have to decide which it got.
      onAuthenticated(await login(values.identifier.trim(), values.password));
    } catch (error) {
      setErrors(describeLoginError(error));
    } finally {
      setSubmitting(false);
    }
  }, [onAuthenticated]);

  return { form, errors, submitting, setField, submit };
}

/**
 * Turns a failed login into something worth reading.
 *
 * 401 gets a fixed sentence rather than the backend's message: the backend deliberately returns
 * the same "invalid username/email or password" for an unknown account and a wrong password, so
 * repeating it is right, and wording it as a pair stops the user wondering which half was wrong.
 *
 * 409 means the credentials were correct but the account cannot sign in (SUSPENDED, DELETED), so
 * the backend's own sentence - "Account is SUSPENDED and cannot sign in" - is the useful one.
 */
function describeLoginError(error: unknown): LoginErrors {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return { form: 'Wrong username/email or password.' };
    }
    if (error.status === 409) {
      return { form: error.message };
    }
    return { form: describeError(error) };
  }
  return { form: describeError(error) };
}

export type { LoginForm, LoginErrors };
