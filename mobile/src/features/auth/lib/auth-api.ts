import { ApiRequestError, login, request } from '@/features/auth/lib/api-client';
import type { Account, Session } from '@/types/api';

// The auth feature's slice of the API: POST /auth/register and POST /auth/login.

/** Re-exported so a caller can name the returned profile without importing the global types. */
export type { Account } from '@/types/api';

/**
 * Body of POST /auth/register.
 *
 * Field names are the Java field names from `UserRequests.CreateAccount`, and `dateOfBirth` is
 * a plain `YYYY-MM-DD` string because that is what Jackson accepts for a `LocalDate`.
 *
 * There is deliberately no `status` or `type`: the backend sets both server-side, so sending
 * them would be ignored.
 */
export type SignUpDetails = {
  userName: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  dateOfBirth?: string;
};

/**
 * POST /auth/register.
 *
 * Rejects with an `ApiRequestError` carrying `fieldErrors` on a 400 (validation) and a plain
 * message on a 409 (username or email already taken).
 */
export function register(details: SignUpDetails): Promise<Account> {
  return request<Account>('/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(details),
  });
}

/**
 * Registers, then immediately signs the new account in.
 *
 * The two calls are kept in one function so the sign-up screen cannot forget the second half
 * and drop the user on a screen they cannot use. The email is offered first as the login
 * identifier because `POST /auth/login` accepts either.
 */
export async function registerAndSignIn(details: SignUpDetails): Promise<{ account: Account; session: Session }> {
  const account = await register(details);
  try {
    const session = await login(details.email, details.password);
    return { account, session };
  } catch (error) {
    // The account exists; only the follow-up sign-in failed. Surface the original reason
    // rather than a generic message so the user knows their account was created.
    if (error instanceof ApiRequestError) {
      throw new ApiRequestError(
        error.status,
        `Your account was created, but signing in automatically failed (${error.message}). Please sign in.`,
      );
    }
    throw error;
  }
}
