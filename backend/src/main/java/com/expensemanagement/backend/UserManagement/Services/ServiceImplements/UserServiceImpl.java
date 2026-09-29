package com.expensemanagement.backend.UserManagement.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.InvalidCredentialsException;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import com.expensemanagement.backend.UserManagement.Mappers.UserMappers;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * User management.
 *
 * <p>{@code readOnly = true} at class level is a safety net: it makes Hibernate skip dirty
 * checking on read paths, and write methods opt out individually with {@code @Transactional}.
 * A write method that forgets to opt out will not silently flush a change.
 *
 * <p>RBAC is enforced here with {@code @PreAuthorize}, in addition to the URL rules in
 * {@code SecurityConfig}. Both layers exist on purpose: URL rules are easy to audit in one
 * place, method rules cannot be bypassed by adding a new route that forgets the matcher.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class UserServiceImpl implements UserService
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------
    // Registration and authentication
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UserResponses.Details createAccount(UserRequests.CreateAccount request)
    {
        // Check before insert so the caller gets a clean 409 instead of a unique-constraint
        // violation surfacing as a 500.
        if (userRepository.existsByUserNameIgnoreCase(request.getUserName()))
        {
            throw new DuplicateResourceException("Username '" + request.getUserName() + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail()))
        {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' is already registered");
        }

        User user = UserMappers.toEntity(request);

        // The mapper leaves passwordHash null on purpose - hashing is a security decision, and
        // this is the only place with an encoder. Forgetting this line fails loudly on insert.
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // status and type are left unset: @PrePersist defaults them to PENDING and USER, so a
        // client cannot self-activate or self-promote.
        User saved = userRepository.save(user);

        log.info("Registered account id={} username={}", saved.getId(), saved.getUserName());
        return UserMappers.toDetails(saved);
    }

    @Override
    public UserResponses.Details authenticate(UserRequests.Authenticate request)
    {
        String identifier = request.getUsernameOrEmail();

        // The derived query takes two parameters (one for userName, one for email), so the
        // single identifier from the login form is passed twice.
        User user = userRepository.findByUserNameOrEmail(identifier, identifier)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
        {
            // Identical message to the branch above, on purpose: saying "no such user" versus
            // "wrong password" lets an attacker enumerate valid accounts.
            throw new InvalidCredentialsException("Invalid username/email or password");
        }

        // Checked after the password so this detail is only revealed to someone who already
        // proved they own the account.
        if (user.getStatus() != UserAccountState.ACTIVE)
        {
            throw new OperationNotAllowedException("Account is " + user.getStatus() + " and cannot sign in");
        }

        return UserMappers.toDetails(user);
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Override
    public UserResponses.Details findById(Long id)
    {
        return UserMappers.toDetails(getUserOrThrow(id));
    }

    @Override
    public UserResponses.Details findByUserName(String userName)
    {
        return userRepository.findByUserName(userName)
                .map(UserMappers::toDetails)
                .orElseThrow(() -> new ResourceNotFoundException("No user with username '" + userName + "'"));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponses.Summary> findAll(Pageable pageable)
    {
        return UserMappers.toSummaryPage(userRepository.findAll(pageable));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponses.Summary> findAllByStatus(UserAccountState status, Pageable pageable)
    {
        return UserMappers.toSummaryPage(userRepository.findAllByStatus(status, pageable));
    }

    @Override
    public Page<UserResponses.Summary> searchByName(String name, Pageable pageable)
    {
        // Two-parameter derived query again: one search box feeds both name fields.
        return UserMappers.toSummaryPage(
                userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                        name, name, pageable));
    }

    // ------------------------------------------------------------------
    // Self-service profile and credentials
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UserResponses.Details updateAccount(Long id, UserRequests.UpdateAccount request)
    {
        User user = getUserOrThrow(id);

        // Only re-check uniqueness when the username actually changes, and skip soft-deleted
        // accounts so someone who deleted an account does not block the name forever.
        if (StringUtils.hasText(request.getUserName())
                && !request.getUserName().equalsIgnoreCase(user.getUserName())
                && userRepository.existsByUserNameAndStatusNot(request.getUserName(), UserAccountState.DELETED))
        {
            throw new DuplicateResourceException("Username '" + request.getUserName() + "' is already taken");
        }

        // applyUpdate only touches non-null fields, so this is a PATCH, not a replace.
        UserMappers.applyUpdate(user, request);
        return UserMappers.toDetails(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(Long id, UserRequests.ChangePassword request)
    {
        User user = getUserOrThrow(id);

        // Requiring the current password is what stops a stolen token alone from locking the
        // real owner out of their own account.
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash()))
        {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        // A cross-field rule the field annotations cannot express.
        if (!request.getNewPassword().equals(request.getConfirmNewPassword()))
        {
            throw new OperationNotAllowedException("New password and confirmation do not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user id={}", id);
    }

    // ------------------------------------------------------------------
    // Lifecycle and privilege (admin only)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponses.Details updateStatus(Long id, UserRequests.UpdateAccountStatus request)
    {
        User user = getUserOrThrow(id);
        UserMappers.applyStatus(user, request);

        User saved = userRepository.save(user);

        // UpdateAccountStatus.reason has no column on User, so it is logged rather than
        // silently dropped. Move it to an audit table if it needs to be queryable.
        log.info("Admin changed status of user id={} to {} (reason: {})",
                id, saved.getStatus(), request.getReason());

        return UserMappers.toDetails(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponses.Details changeType(Long id, UserType type)
    {
        User user = getUserOrThrow(id);

        // Without this guard, an admin demoting themselves - or the only other admin - locks
        // everyone out of administration permanently, with no way back in through the API.
        if (user.getType() == UserType.ADMIN
                && type != UserType.ADMIN
                && userRepository.countByType(UserType.ADMIN) <= 1)
        {
            throw new OperationNotAllowedException("Cannot demote the last remaining admin");
        }

        user.setType(type);
        User saved = userRepository.save(user);

        log.info("Admin changed type of user id={} to {}", id, type);
        return UserMappers.toDetails(saved);
    }

    @Override
    @Transactional
    public void deleteAccount(Long id, UserRequests.DeleteAccount request)
    {
        User user = getUserOrThrow(id);

        // Re-authentication guard: a destructive action should not be triggerable by anyone
        // who merely holds an unlocked device with a live session.
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
        {
            throw new InvalidCredentialsException("Password is incorrect");
        }

        // Soft delete: the row survives so the audit trail and createdAt are preserved.
        UserMappers.applyDeletion(user);
        userRepository.save(user);

        log.info("User id={} soft-deleted (reason: {})", id, request.getReason());
    }

    // ------------------------------------------------------------------

    private User getUserOrThrow(Long id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No user with id " + id));
    }
}
