import type { Href } from 'expo-router';

import { validateUsername } from '@/features/auth/lib/fields';
import type { FieldErrors } from '@/features/auth/lib/fields';
import type { Session } from '@/types/api';

// Login form state: the two fields POST /auth/login needs, and where each account type lands.

/** The raw strings the form holds. */
export type LoginForm = {
  identifier: string;
  password: string;
};

export const EMPTY_LOGIN_FORM: LoginForm = { identifier: '', password: '' };

export type LoginField = keyof LoginForm | 'form';

export type LoginErrors = FieldErrors<LoginField>;

export function validateLoginForm(form: LoginForm): LoginErrors {
  const errors: LoginErrors = {};

  // No minimum length here: this is an existing credential, and the backend decides.
  const identifierError = validateUsername(form.identifier);
  if (identifierError) {
    errors.identifier = identifierError.replace('Username', 'Username or email');
  }
  if (!form.password) {
    errors.password = 'Password is required';
  }

  return errors;
}

/** Convenience for the submit button: a login needs neither field empty. */
export function canSubmitLogin(form: LoginForm): boolean {
  return form.identifier.trim().length > 0 && form.password.length > 0;
}

/**
 * Where an account lands after signing in.
 *
 * This is the whole point of signing in through one screen for both roles: `type` comes from the
 * JWT's account, not from the screen the user opened. An ADMIN goes to the admin area, a USER to
 * their money - and a USER cannot reach `/admin` by typing it, because the tab is the same
 * screen either way and the admin API refuses a non-admin token with 403.
 */
export const HOME_ROUTE_BY_TYPE: Record<Session['type'], Href> = {
  ADMIN: '/admin',
  USER: '/finances',
};

/** The route for an account type. */
export function homeRouteFor(type: Session['type']): Href {
  return HOME_ROUTE_BY_TYPE[type];
}

/**
 * The sentence shown in the pop-up once a sign-in succeeds.
 *
 * Admin is a role, not a person, so it is named as one; everyone else is greeted by the account
 * name the backend returned with the token.
 */
export function welcomeMessageFor(session: Session): string {
  return session.type === 'ADMIN'
    ? 'Welcome admin'
    : `Welcome ${session.userName}`;
}
