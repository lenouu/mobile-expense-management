package com.expensemanagement.backend.UserManagement.Enums;

/**
 * Authorisation level of an account.
 *
 * <p><strong>Distinct from {@link UserAccountState}.</strong> That enum is the account
 * <em>lifecycle</em> (PENDING / ACTIVE / SUSPENDED / DELETED); this one is the account
 * <em>privilege</em>. The two axes are independent - a user can be ACTIVE and still be an
 * ordinary USER, or ACTIVE and an ADMIN. Collapsing them into one enum is a common mistake
 * that makes "suspend the admin" or "promote a pending user" impossible to express.
 *
 * <p>Value names are chosen to line up with Spring Security authorities, which you already
 * have on the classpath: {@code USER} maps to {@code ROLE_USER} and {@code ADMIN} to
 * {@code ROLE_ADMIN}.
 */
public enum UserType
{
    /** Ordinary account. May read and modify only its own data. */
    USER,

    /** Administrative account. May act on other users (status, type, listing). */
    ADMIN
}
