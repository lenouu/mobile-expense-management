/**
 * The same field rules the sign-up form uses, shared by both auth screens.
 *
 * This lives in its own module because a rule applied in one place and not the other is how a
 * username that passes sign-up gets rejected at login - or worse, the reverse.
 */

export type FieldErrors<Field extends string> = Partial<Record<Field, string>>;

/** An email strict enough to catch typos, loose enough not to reject valid addresses. */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Validates a person's name. */
export function validateName(value: string, label: string): string | undefined {
  if (!value.trim()) return `${label} is required`;
  return undefined;
}

/**
 * Validates a display username.
 *
 * `minLength` is 3 at sign-up, where the user is choosing one. Login only checks that something
 * was typed: applying the sign-up policy there would lock out any account created before the
 * policy existed, and the credential check is the server's job anyway.
 */
export function validateUsername(value: string, options: { minLength?: number } = {}): string | undefined {
  const username = value.trim();
  if (!username) return 'Username is required';
  const min = options.minLength;
  if (min && username.length < min) return `Username must be at least ${min} characters`;
  if (/\s/.test(username)) return 'Username cannot contain spaces';
  return undefined;
}

export function validateEmail(value: string): string | undefined {
  const email = value.trim();
  if (!email) return 'Email is required';
  if (!EMAIL_PATTERN.test(email)) return 'Enter a valid email address';
  return undefined;
}
