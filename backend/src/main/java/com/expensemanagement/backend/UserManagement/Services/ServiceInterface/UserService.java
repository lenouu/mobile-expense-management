package com.expensemanagement.backend.UserManagement.Services.ServiceInterface;

import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * User management operations.
 *
 * <p>One method per request DTO, plus the read operations those responses need. The
 * implementation lives in {@code Services.ServiceImplements.UserServiceImpl}.
 *
 * <p><strong>Contract rules that apply to every method:</strong>
 * <ul>
 *   <li>Arguments are assumed already validated ({@code @Valid} at the controller). These
 *       methods do not re-check {@code @NotBlank} etc.</li>
 *   <li>Entities never cross this boundary - only {@code UserResponses} projections leave,
 *       so {@code passwordHash} cannot leak.</li>
 *   <li>Methods documented "admin only" must be protected at the controller or with method
 *       security. The service does not enforce authorisation by itself.</li>
 * </ul>
 *
 * <p>The exceptions each method raises are described in its javadoc; there is no dedicated
 * exceptions package yet, so the implementation should define those types (or reuse
 * {@code jakarta.persistence.EntityNotFoundException}) before this interface is filled in.
 */
public interface UserService
{
    // ------------------------------------------------------------------
    // Registration and authentication
    // ------------------------------------------------------------------

    /**
     * Registers a new account.
     *
     * <p>Hashes {@code request.getPassword()} - the plaintext is never persisted. The new
     * account is always {@code UserAccountState.PENDING} and {@code UserType.USER}; neither
     * is taken from the request, because letting a client set them would allow
     * self-activation and privilege escalation.
     *
     * @throws IllegalStateException if the username or email is already taken (check with
     *         the repository's {@code existsBy...} before inserting so the caller gets a
     *         friendly 409 rather than a unique-constraint violation)
     */
    UserResponses.Details createAccount(UserRequests.CreateAccount request);

    /**
     * Verifies credentials from either the username or the email.
     *
     * <p>Look the account up with {@code findByUserNameOrEmail(identifier, identifier)} -
     * the derived query takes two parameters, so pass the same value twice.
     *
     * <p>Returns the {@code Details} of the authenticated user. Note that a real login
     * should hand back a token or session, not just the profile; treat this signature as a
     * placeholder until Spring Security is wired up.
     *
     * @throws IllegalStateException if no account matches, or the password does not verify
     *         (use one message for both cases so the response cannot be used to discover
     *         which usernames exist)
     * @throws IllegalStateException if the account is not {@code ACTIVE} - a
     *         {@code PENDING}, {@code SUSPENDED} or {@code DELETED} account must not
     *         authenticate
     */
    UserResponses.Details authenticate(UserRequests.Authenticate request);

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * @throws java.util.NoSuchElementException if no user has that id
     */
    UserResponses.Details findById(Long id);

    /**
     * @throws java.util.NoSuchElementException if no user has that username
     */
    UserResponses.Details findByUserName(String userName);

    /** Paged listing of every account, including soft-deleted ones. Admin only. */
    Page<UserResponses.Summary> findAll(Pageable pageable);

    /** Paged listing filtered by lifecycle state. Pass {@code ACTIVE} for the usual case. */
    Page<UserResponses.Summary> findAllByStatus(UserAccountState status, Pageable pageable);

    /**
     * Paged name search across first and last name.
     *
     * <p>The repository method takes two parameters, so pass {@code name} for both.
     */
    Page<UserResponses.Summary> searchByName(String name, Pageable pageable);

    // ------------------------------------------------------------------
    // Self-service profile and credentials
    // ------------------------------------------------------------------

    /**
     * Partial profile update. Only the non-null fields of the request are applied.
     *
     * <p>Because {@code UpdateAccount} can change {@code userName}, re-check uniqueness with
     * {@code existsByUserNameAndStatusNot(request.getUserName(), DELETED)} before saving.
     *
     * @throws java.util.NoSuchElementException if no user has that id
     */
    UserResponses.Details updateAccount(Long id, UserRequests.UpdateAccount request);

    /**
     * Changes a password after verifying the current one.
     *
     * <p>The {@code oldPassword} check is what stops a stolen session token from taking over
     * the account. Also verify {@code newPassword} equals {@code confirmNewPassword} here,
     * since that is a cross-field rule the field annotations cannot express, and hash the
     * new password before storing it.
     *
     * @throws IllegalStateException if {@code oldPassword} does not match the stored hash
     * @throws IllegalStateException if the two new passwords differ
     * @throws java.util.NoSuchElementException if no user has that id
     */
    void changePassword(Long id, UserRequests.ChangePassword request);

    // ------------------------------------------------------------------
    // Lifecycle and privilege (admin only)
    // ------------------------------------------------------------------

    /**
     * Admin only. Moves an account between PENDING / ACTIVE / SUSPENDED / DELETED.
     *
     * <p>The request carries a {@code reason} with no matching column on the entity, so the
     * implementation must decide where it goes - log it, or write it to an audit table.
     * Silently discarding it defeats the point of collecting it.
     *
     * @throws java.util.NoSuchElementException if no user has that id
     */
    UserResponses.Details updateStatus(Long id, UserRequests.UpdateAccountStatus request);

    /**
     * Admin only. Promotes or demotes an account between USER and ADMIN.
     *
     * <p>There is no request DTO for this yet; the enum value is passed directly. Add an
     * {@code UpdateAccountType} request if you want validation or a reason alongside it.
     *
     * <p>Guard against removing the last remaining admin - otherwise a single mistake locks
     * everyone out of administration permanently.
     *
     * @throws java.util.NoSuchElementException if no user has that id
     */
    UserResponses.Details changeType(Long id, UserType type);

    /**
     * Soft-deletes an account: status becomes {@code DELETED} and the row is kept, so the
     * audit trail and the unique {@code userName}/{@code email} values survive.
     *
     * <p>Verifies {@code request.getPassword()} first, as a re-authentication guard. The
     * request's {@code reason} has no column, same caveat as
     * {@link #updateStatus(Long, UserRequests.UpdateAccountStatus)}.
     *
     * @throws IllegalStateException if the password does not verify
     * @throws java.util.NoSuchElementException if no user has that id
     */
    void deleteAccount(Long id, UserRequests.DeleteAccount request);
}
