/**
 * Global types that are not owned by a single feature.
 *
 * Anything only one feature needs lives next to that feature instead (see the lib folder inside
 * each feature), so this file stays a description of the API's shared vocabulary.
 */

/**
 * The backend's flat page envelope, used by every paged endpoint:
 * `{ data, page, size, totalElements, totalPages }`.
 */
export type Page<T> = {
  data: T[];
  /** Zero-based index of this page. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/**
 * Money as the API represents it: an object, never a bare number.
 *
 * `amount` is an exact decimal with at most 2 places; `currency` is an ISO 4217 code and is
 * always present on a response. On a request the backend defaults it to XAF when omitted.
 */
export type Money = {
  amount: number;
  currency: string;
};

/** CFA franc: what the backend falls back to, and the currency this product reports in. */
export const DEFAULT_CURRENCY = 'XAF';

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
  id: string;
  userName: string;
  email: string;
  /** Null until the user fills their name in from the profile screen. */
  firstName: string | null;
  lastName: string | null;
  /** YYYY-MM-DD, or null when the user skipped it. */
  dateOfBirth: string | null;
  profilePictureReference: string | null;
  /**
   * Path of the avatar relative to the API base, e.g. `/avatars/8f3c….jpg`, or null.
   * The file name is random and changes on upload, so the URL itself is the cache key.
   */
  profilePictureUrl: string | null;
  status: AccountState;
  type: AccountType;
  createdAt: string;
  updatedAt: string;
};

/** The backend's own error body: `{ code, message, fields }`, as built by `ApiError`. */
export type ApiErrorBody = {
  /** Stable machine-readable token, e.g. `validation_failed`, `not_found`, `business_rule_violation`. */
  code: string;
  message: string;
  /** Only present on a 400 `validation_failed`: field path to message. */
  fields?: Record<string, string>;
};
