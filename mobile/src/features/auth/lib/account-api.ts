import { resolveApiBaseUrl } from '@/constants/api';
import { authRequest } from '@/features/auth/lib/api-client';
import type { Account, Session } from '@/types/api';

// The signed-in account's own profile: `/api/users/me`.
//
// Deliberately separate from `admin-api.ts`, which is the `/api/admin/**` surface: these routes
// are self-service and every role may call them, an administrator included. The account id comes
// from the token, so there is no id in any of these URLs to tamper with.

/** Fields of `PATCH /users/me`. Anything omitted is left as it is. */
export type ProfileUpdate = {
  userName?: string;
  firstName?: string;
  lastName?: string;
};

/** Body of `PUT /users/me/password`. All three are required by the backend. */
export type PasswordChange = {
  oldPassword: string;
  newPassword: string;
  confirmNewPassword: string;
};

/** GET /users/me - the caller's own profile, never including the password hash. */
export function fetchMyProfile(session: Session) {
  return authRequest<Account>('/users/me', session);
}

/**
 * PATCH /users/me - partial update.
 *
 * @throws ApiRequestError 409 when the requested username is already taken
 * @throws ApiRequestError 400 with `fields.userName` when validation fails
 */
export function updateMyProfile(session: Session, update: ProfileUpdate) {
  return authRequest<Account>('/users/me', session, { method: 'PATCH', body: update });
}

/**
 * PUT /users/me/password - 204 on success.
 *
 * @throws ApiRequestError 401 when the current password is wrong
 * @throws ApiRequestError 422 when the two new passwords do not match
 */
export function changeMyPassword(session: Session, change: PasswordChange) {
  return authRequest<void>('/users/me/password', session, { method: 'PUT', body: change });
}

/**
 * Absolute URI for the `profilePictureUrl` the profile returns.
 *
 * The server sends a path relative to the API base because the same account is reached over a LAN
 * address from a phone and over localhost from the browser; only the client knows which one applies.
 *
 * The URL contains a random file name, so a replaced picture is a different URL and no cache layer
 * can serve the old image.
 */
export function avatarUri(path: string | null | undefined): string | null {
  return path ? `${resolveApiBaseUrl()}${path}` : null;
}

/**
 * POST /users/me/photo - JSON `{"image": "<base64>"}`.
 *
 * Deliberately not multipart. A multipart upload from React Native failed on a real device before
 * the request ever left the phone (`fetch` rejected, so the server logged nothing at all), while
 * the app's ordinary JSON POSTs work. Sending the picture as base64 in a JSON body costs a third
 * more bytes and uses the transport that is known to arrive.
 *
 * The server re-encodes whatever it receives, so nothing is processed here beyond the picker's own
 * `quality` and `base64` options.
 *
 * @throws ApiRequestError 400 when the image is empty, over 5 MB, or not a format the server can read
 */
export function uploadMyPhoto(session: Session, base64: string) {
  return authRequest<Account>('/users/me/photo', session, {
    method: 'POST',
    body: { image: base64 },
  });
}

/** DELETE /users/me/photo - 204. The account falls back to its initials. */
export function deleteMyPhoto(session: Session) {
  return authRequest<void>('/users/me/photo', session, { method: 'DELETE' });
}
