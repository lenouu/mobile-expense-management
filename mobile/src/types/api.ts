/**
 * Global types that are not owned by a single feature.
 *
 * Anything only one feature needs lives next to that feature instead (see the lib folder inside
 * each feature), so this file stays a description of the API's shared vocabulary.
 */

/** Spring Data's `PagedModel` envelope, used by every paged backend endpoint. */
export type PagedModel<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};

/** `UserType` on the backend: what an account is allowed to do. */
export type AccountType = 'USER' | 'ADMIN';

/** `UserAccountState` on the backend. */
export type AccountState = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';

/** What a successful POST /auth/login leaves us with. */
export type Session = {
  accessToken: string;
  userName: string;
  type: AccountType;
};

/** The user profile shape the backend returns (`UserResponses.Details`), minus any password. */
export type Account = {
  id: number;
  userName: string;
  email: string;
  firstName: string;
  lastName: string;
  /** YYYY-MM-DD, or null when the user skipped it. */
  dateOfBirth: string | null;
  profilePictureReference: string | null;
  status: AccountState;
  type: AccountType;
  createdAt: string;
  updatedAt: string;
};

/** The backend's own error body, as built by `ApiError.of(...)`. */
export type ApiErrorBody = {
  status: number;
  error: string;
  message: string;
  /** Only present on a 400 `validation_failed`: field name to message. */
  fieldErrors?: Record<string, string>;
};
