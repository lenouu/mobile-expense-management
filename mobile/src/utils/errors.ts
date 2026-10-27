import { ApiRequestError } from '@/features/auth/lib/api-client';

/**
 * Turns any thrown value into a sentence worth showing a user.
 *
 * `context` lets a caller add the one thing the generic message cannot know - for example that
 * a 401 here means the session expired rather than a wrong password.
 */
export function describeError(
  error: unknown,
  context: { unauthorized?: string; forbidden?: string } = {},
): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 0) return error.message;
    if (error.status === 401 && context.unauthorized) return context.unauthorized;
    if (error.status === 403 && context.forbidden) return context.forbidden;
    return error.message;
  }
  if (error instanceof Error && error.message) return error.message;
  return 'Something went wrong. Please try again.';
}
