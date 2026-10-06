package com.expensemanagement.backend.CategoryManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Mappers.CategoryMappers;
import com.expensemanagement.backend.CategoryManagement.Repositories.DefaultCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.UserCategoryService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Each user's own categories (S2-TECH-1).
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

    public UserCategoryServiceImpl(UserCategoryRepository userCategoryRepository,
                                   DefaultCategoryRepository defaultCategoryRepository)
    {
        this.userCategoryRepository = userCategoryRepository;
        this.defaultCategoryRepository = defaultCategoryRepository;
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
}
