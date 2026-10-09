import { useCallback, useRef, useState } from 'react';

import { registerAndSignIn, type SignUpDetails } from '@/features/auth/lib/auth-api';
import {
  EMPTY_SIGN_UP_FORM,
  toSignUpDetails,
  validateSignUpForm,
  type SignUpErrors,
  type SignUpField,
  type SignUpForm,
} from '@/features/auth/lib/sign-up-validation';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { describeError } from '@/utils/errors';
import type { Account, Session } from '@/types/api';

/** Every field name the backend can report in `fieldErrors`, plus the password misspelling. */
const FORM_FIELDS: SignUpField[] = [
  'firstName',
  'lastName',
  'userName',
  'email',
  'dateOfBirth',
  'password',
  'acceptedTerms',
];

function isFormField(name: string): name is SignUpField {
  return (FORM_FIELDS as string[]).includes(name);
}

/**
 * Validation and submission for the sign-up form.
 *
 * Keeping this out of the screen means `sign-up-screen.tsx` only describes layout, and the
 * submit path (client rules -> POST /auth/register -> POST /auth/login) can be read in one
 * place. On success it hands the new session to `onSignedIn`, which stores it via the
 * session provider and navigates.
 */
export function useSignUpForm(onSignedIn: (result: { account: Account; session: Session }) => void) {
  const [form, setForm] = useState<SignUpForm>(EMPTY_SIGN_UP_FORM);
  const [errors, setErrors] = useState<SignUpErrors>({});
  const [submitting, setSubmitting] = useState(false);

  // Submitting reads the form through a ref rather than closing over the state value, so a
  // keystroke that lands between the tap and the request cannot be sent stale - and the
  // callback identity stays stable, which keeps the submit button from re-rendering the screen.
  const formRef = useRef(form);
  formRef.current = form;

  const setField = useCallback(<K extends keyof SignUpForm>(field: K, value: SignUpForm[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
    // Clear the message as soon as the user starts fixing it; leaving a stale error under a
    // field the user has already corrected reads as if the correction did not register.
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

    const clientErrors = validateSignUpForm(values);
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      return;
    }

    setSubmitting(true);
    setErrors({});

    try {
      const result = await registerAndSignIn(toSignUpDetails(values));
      onSignedIn(result);
    } catch (error) {
      setErrors(mapSubmitError(error));
    } finally {
      setSubmitting(false);
    }
  }, [onSignedIn]);

  return { form, errors, submitting, setField, submit };
}

/**
 * Turns a failed request into messages the form can show.
 *
 * The backend answers a 400 with `fieldErrors` keyed by its own field names - the same camelCase
 * names this form uses, so they drop straight into the state the client-side rules write to.
 *
 * A 409 carries no field, only a sentence naming the offending value: "Username 'x' is already
 * taken" or "Email 'x' is already registered". That sentence is parsed for the field so the error
 * appears under the input the user has to change, rather than at the bottom of the form.
 */
function mapSubmitError(error: unknown): SignUpErrors {
  if (!(error instanceof ApiRequestError)) {
    return { form: describeError(error) };
  }

  const errors: SignUpErrors = {};
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (isFormField(field)) {
      errors[field] = message;
    } else {
      errors.form = message;
    }
  }

  if (Object.keys(errors).length === 0) {
    if (error.status === 409) {
      if (/username/i.test(error.message)) {
        errors.userName = error.message;
      } else if (/email/i.test(error.message)) {
        errors.email = error.message;
      } else {
        errors.form = error.message;
      }
    } else if (error.status === 400) {
      errors.form = error.message;
    } else {
      errors.form = describeError(error);
    }
  }

  return errors;
}

export type { SignUpForm, SignUpErrors, SignUpField, SignUpDetails };
