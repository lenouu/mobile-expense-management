/**
 * The sign-up password rules.
 *
 * The backend only enforces a minimum length (`@Size(min = 8)` on `UserRequests.CreateAccount`).
 * Everything else here is a product decision shown live in the checklist, so the rules live in
 * one place: change this file and both the checklist and the submit-time validation follow.
 */

export const MIN_PASSWORD_LENGTH = 8;

/** Characters that count as "special" for the checklist. */
const SPECIAL_CHARACTER = /[^A-Za-z0-9]/;

export type PasswordRule = {
  key: 'length' | 'number' | 'capital' | 'special';
  label: string;
  test: (password: string) => boolean;
};

export const PASSWORD_RULES: PasswordRule[] = [
  { key: 'length', label: `${MIN_PASSWORD_LENGTH}+ characters`, test: (p) => p.length >= MIN_PASSWORD_LENGTH },
  { key: 'number', label: '1+ number', test: (p) => /\d/.test(p) },
  { key: 'capital', label: 'Capital letter', test: (p) => /[A-Z]/.test(p) },
  { key: 'special', label: 'Special character', test: (p) => SPECIAL_CHARACTER.test(p) },
];

/** The rules with their current pass/fail state, ready to hand to `PasswordChecklist`. */
export function passwordRequirements(password: string) {
  return PASSWORD_RULES.map((rule) => ({
    key: rule.key,
    label: rule.label,
    met: rule.test(password),
  }));
}

/**
 * The message to show when the password is not acceptable, or `null` when it is.
 *
 * This mirrors every rule in the checklist including the minimum length the backend enforces,
 * so a request is never sent that the server is certain to reject.
 */
export function validatePassword(password: string): string | null {
  if (password.length === 0) return 'Password is required';
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `Password must be at least ${MIN_PASSWORD_LENGTH} characters`;
  }
  const unmet = PASSWORD_RULES.filter((rule) => !rule.test(password));
  if (unmet.length > 0) {
    return `Password still needs: ${unmet.map((rule) => rule.label.toLowerCase()).join(', ')}`;
  }
  return null;
}
