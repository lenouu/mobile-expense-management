import { validateEmail, validateName, validateUsername } from '@/features/auth/lib/fields';
import type { FieldErrors } from '@/features/auth/lib/fields';
import { validatePassword } from '@/features/auth/lib/password';
import type { SignUpDetails } from '@/features/auth/lib/auth-api';

// Sign-up form validation. The backend validates the same values again; this copy exists so the
// user is told what is wrong before a round-trip, not instead of one. The individual field rules
// come from `fields.ts`, which the login screen shares, so the two cannot drift apart.

/** The raw strings the form holds. They are converted to `SignUpDetails` on submit. */
export type SignUpForm = {
  firstName: string;
  lastName: string;
  userName: string;
  email: string;
  /**
   * Free text in DD/MM/YYYY, which is how the date is written locally. Optional: the backend
   * accepts a null `dateOfBirth`, and forcing a birthday would only cost us sign-ups.
   */
  dateOfBirth: string;
  password: string;
  acceptedTerms: boolean;
};

export const EMPTY_SIGN_UP_FORM: SignUpForm = {
  firstName: '',
  lastName: '',
  userName: '',
  email: '',
  dateOfBirth: '',
  password: '',
  acceptedTerms: false,
};

export type SignUpField = keyof SignUpForm | 'form';

export type SignUpErrors = FieldErrors<SignUpField>;

export function validateSignUpForm(form: SignUpForm): SignUpErrors {
  const errors: SignUpErrors = {};

  // Assigned through a helper so a `undefined` value never becomes an own property: an errors
  // object with a key set to undefined would still look non-empty to `Object.keys().length`.
  const add = (field: SignUpField, message: string | undefined) => {
    if (message) errors[field] = message;
  };

  add('firstName', validateName(form.firstName, 'First name'));
  add('lastName', validateName(form.lastName, 'Last name'));

  // 3 characters is this screen's policy for choosing a username; login deliberately does not
  // re-apply it, so an older account is never locked out.
  add('userName', validateUsername(form.userName, { minLength: 3 }));

  add('email', validateEmail(form.email));

  add('password', validatePassword(form.password) ?? undefined);

  if (form.dateOfBirth.trim() && !parseDateOfBirth(form.dateOfBirth)) {
    errors.dateOfBirth = 'Use DD/MM/YYYY, and a date in the past';
  }

  if (!form.acceptedTerms) {
    errors.acceptedTerms = 'Please accept the Terms of Service to continue';
  }

  return errors;
}

/**
 * Parses the typed birthday into the `YYYY-MM-DD` string Jackson wants for a `LocalDate`.
 *
 * Accepts `DD/MM/YYYY` (what the placeholder asks for) and `YYYY-MM-DD` (what a paste from a
 * document usually looks like). Returns null for anything else, or for a date that is not in
 * the past - the backend's `@Past` would reject it anyway.
 */
export function parseDateOfBirth(input: string): string | null {
  const value = input.trim();
  if (!value) return null;

  const slash = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(value);
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);

  let year: number, month: number, day: number;
  if (slash) {
    day = Number(slash[1]);
    month = Number(slash[2]);
    year = Number(slash[3]);
  } else if (iso) {
    year = Number(iso[1]);
    month = Number(iso[2]);
    day = Number(iso[3]);
  } else {
    return null;
  }

  const date = new Date(year, month - 1, day);
  // Rejects 31/02 and friends: the Date constructor rolls those over into March.
  const real =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
  if (!real) return null;

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  if (date.getTime() >= today.getTime()) return null;

  const pad = (n: number) => String(n).padStart(2, '0');
  return `${year}-${pad(month)}-${pad(day)}`;
}

/** Trims every string and turns the form into the exact body POST /auth/register expects. */
export function toSignUpDetails(form: SignUpForm): SignUpDetails {
  const dateOfBirth = parseDateOfBirth(form.dateOfBirth);
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    userName: form.userName.trim(),
    email: form.email.trim(),
    password: form.password,
    // Omitted rather than sent as null, so the optional field stays optional in the payload.
    ...(dateOfBirth ? { dateOfBirth } : {}),
  };
}
