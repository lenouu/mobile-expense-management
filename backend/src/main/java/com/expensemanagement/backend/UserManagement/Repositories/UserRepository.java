package com.expensemanagement.backend.UserManagement.Repositories;

import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long>
{
    // ---------------------------------------------------------------------
    // Authentication / login lookups
    // ---------------------------------------------------------------------

    /** Login by username. Unique column, so the result is at most one row. */
    Optional<User> findByUserName(String userName);

    /** Login by email. Unique column, so the result is at most one row. */
    Optional<User> findByEmail(String email);

    /** Single round-trip for "username or email" login forms. */
    Optional<User> findByUserNameOrEmail(String userName, String email);

    /** Case-insensitive variants - emails and usernames are conceptually case-folded. */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserNameIgnoreCase(String userName);

    // ---------------------------------------------------------------------
    // Registration / uniqueness checks
    // ---------------------------------------------------------------------
    // Prefer these over findBy...().isPresent(): they issue a cheap EXISTS
    // query instead of hydrating a whole entity.

    boolean existsByUserName(String userName);

    boolean existsByEmail(String email);

    boolean existsByUserNameIgnoreCase(String userName);

    boolean existsByEmailIgnoreCase(String email);

    // ---------------------------------------------------------------------
    // Status-scoped reads (the entity has a UserAccountState lifecycle:
    // PENDING, ACTIVE, SUSPENDED, DELETED)
    // ---------------------------------------------------------------------

    List<User> findAllByStatus(UserAccountState status);

//    List<User> findAll(Pageable pageable);

    Page<User> findAllByStatus(UserAccountState status, Pageable pageable);

    /** Excludes soft-deleted rows - the usual "list real users" query. */
    Page<User> findAllByStatusNot(UserAccountState status, Pageable pageable);

    long countByStatus(UserAccountState status);

    /** Guard for acting on a live user, e.g. suspend/activate. */
    Optional<User> findByIdAndStatus(Long id, UserAccountState status);

    boolean existsByEmailAndStatusNot(String email, UserAccountState status);

    boolean existsByUserNameAndStatusNot(String userName, UserAccountState status);

    // ---------------------------------------------------------------------
    // Name search (admin / user directory screens)
    // ---------------------------------------------------------------------

    Page<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName, Pageable pageable);

    // ---------------------------------------------------------------------
    // Reporting / auditing
    // ---------------------------------------------------------------------

    List<User> findAllByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByCreatedAtAfter(LocalDateTime from);


}
