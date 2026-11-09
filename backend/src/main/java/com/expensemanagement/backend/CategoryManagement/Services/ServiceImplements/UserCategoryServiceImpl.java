package com.expensemanagement.backend.CategoryManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Mappers.CategoryMappers;
import com.expensemanagement.backend.CategoryManagement.Repositories.DefaultCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.UserCategoryService;
import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Each user's own categories: copied from the defaults at sign-up (S2-TECH-1), or created by
 * the user (US17).
 *
 * <p>Reads the default categories straight from the repository rather than through
 * {@code DefaultCategoryService}: that service's admin methods carry {@code @PreAuthorize}, and
 * registration runs anonymously.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class UserCategoryServiceImpl implements UserCategoryService
{
    private final UserCategoryRepository userCategoryRepository;
    private final DefaultCategoryRepository defaultCategoryRepository;
    private final UserRepository userRepository;

    public UserCategoryServiceImpl(UserCategoryRepository userCategoryRepository,
                                   DefaultCategoryRepository defaultCategoryRepository,
                                   UserRepository userRepository)
    {
        this.userCategoryRepository = userCategoryRepository;
        this.defaultCategoryRepository = defaultCategoryRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public int provisionDefaults(User user)
    {
        // Only active defaults: a deactivated one is exactly what the admin no longer wants
        // new users to start with. Default names are unique ignoring case, so the copies
        // cannot clash with each other on the (user_id, name) constraint.
        List<UserCategory> copies = defaultCategoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(source -> CategoryMappers.toUserCategory(source, user))
                .toList();

        userCategoryRepository.saveAll(copies);

        log.info("Provisioned {} default categories for user id={}", copies.size(), user.getId());
        return copies.size();
    }

    @Override
    public List<CategoryResponses.Owned> findMine(UUID userId)
    {
        return userCategoryRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(CategoryMappers::toOwned)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponses.Owned create(UUID userId, CategoryRequests.SaveCategory request)
    {
        String name = request.getName().trim();

        // Checked before insert so the caller gets a clean 409, and ignoring case so "pets"
        // cannot sit next to "Pets". Only this user's categories count: names are per user.
        if (userCategoryRepository.existsByUserIdAndNameIgnoreCase(userId, name))
        {
            throw new DuplicateResourceException("You already have a category named '" + name + "'");
        }

        UserCategory category = new UserCategory();
        // A reference, not a query: the id comes from a verified token, so the user exists.
        category.setUser(userRepository.getReferenceById(userId));
        UserCategory saved = userCategoryRepository.save(CategoryMappers.apply(category, request));

        log.info("User id={} created category id={} name={}", userId, saved.getId(), saved.getName());
        return CategoryMappers.toOwned(saved);
    }

    /**
     * Full replace of one of the caller's own categories.
     *
     * <p>{@code defaultCategoryId} is deliberately left alone by the mapper: renaming a category
     * a user inherited from the defaults only rewrites their own snapshot, so the default and
     * every other user's copy stay untouched.
     */
    @Override
    @Transactional
    public CategoryResponses.Owned update(UUID userId, UUID id, CategoryRequests.SaveCategory request)
    {
        UserCategory category = getOwnCategoryOrThrow(userId, id);
        String name = request.getName().trim();

        // Excluding this id lets a user change only the case of a name ("food" -> "Food").
        if (userCategoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id))
        {
            throw new DuplicateResourceException("You already have a category named '" + name + "'");
        }

        UserCategory saved = userCategoryRepository.save(CategoryMappers.apply(category, request));

        log.info("User id={} updated category id={} name={}", userId, id, saved.getName());
        return CategoryMappers.toOwned(saved);
    }

    /**
     * Deletes one of the caller's own categories.
     *
     * <p>Refused while expenses still reference it: deleting the row would strip the category off
     * those expenses and quietly corrupt the spending history the whole product reports on. The
     * user has to recategorise or delete those expenses first.
     */
    @Override
    @Transactional
    public void delete(UUID userId, UUID id)
    {
        UserCategory category = getOwnCategoryOrThrow(userId, id);

        long used = userCategoryRepository.countExpensesUsing(id);
        if (used > 0)
        {
            throw new OperationNotAllowedException(
                    "Category '" + category.getName() + "' is still used by " + used
                            + " expense(s). Move or delete them first.");
        }

        userCategoryRepository.delete(category);
        log.info("User id={} deleted category id={}", userId, id);
    }

    /** 404 for both "does not exist" and "belongs to someone else", so ids cannot be probed. */
    private UserCategory getOwnCategoryOrThrow(UUID userId, UUID id)
    {
        return userCategoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No category with id " + id));
    }
}
