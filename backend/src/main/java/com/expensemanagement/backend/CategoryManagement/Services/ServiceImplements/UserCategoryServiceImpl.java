package com.expensemanagement.backend.CategoryManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Mappers.CategoryMappers;
import com.expensemanagement.backend.CategoryManagement.Repositories.DefaultCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.UserCategoryService;
import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public List<CategoryResponses.Owned> findMine(Long userId)
    {
        return userCategoryRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(CategoryMappers::toOwned)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponses.Owned create(Long userId, CategoryRequests.SaveCategory request)
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
}
