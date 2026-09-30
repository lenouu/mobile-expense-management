package com.expensemanagement.backend.CategoryManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Entities.DefaultCategory;
import com.expensemanagement.backend.CategoryManagement.Mappers.CategoryMappers;
import com.expensemanagement.backend.CategoryManagement.Repositories.DefaultCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.DefaultCategoryService;
import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default categories (US09).
 *
 * <p>Same layering as {@code UserServiceImpl}: read-only transactions by default, and
 * {@code @PreAuthorize} on the admin operations in addition to the URL rule in
 * {@code SecurityConfig}.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class DefaultCategoryServiceImpl implements DefaultCategoryService
{
    private final DefaultCategoryRepository categoryRepository;

    public DefaultCategoryServiceImpl(DefaultCategoryRepository categoryRepository)
    {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Page<CategoryResponses.Details> findAll(Boolean active, Pageable pageable)
    {
        Page<DefaultCategory> page = active == null
                ? categoryRepository.findAll(pageable)
                : categoryRepository.findAllByActive(active, pageable);
        return page.map(CategoryMappers::toDetails);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponses.Details findById(Long id)
    {
        return CategoryMappers.toDetails(getCategoryOrThrow(id));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponses.Details create(CategoryRequests.SaveCategory request)
    {
        String name = request.getName().trim();

        // Checked before insert so the caller gets a clean 409, and ignoring case so "food"
        // cannot sit next to "Food".
        if (categoryRepository.existsByNameIgnoreCase(name))
        {
            throw new DuplicateResourceException("A category named '" + name + "' already exists");
        }

        DefaultCategory saved = categoryRepository.save(CategoryMappers.apply(new DefaultCategory(), request));

        log.info("Admin created default category id={} name={}", saved.getId(), saved.getName());
        return CategoryMappers.toDetails(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponses.Details update(Long id, CategoryRequests.SaveCategory request)
    {
        DefaultCategory category = getCategoryOrThrow(id);
        String name = request.getName().trim();

        // Excluding this id lets an admin change only the case of a name ("food" -> "Food").
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id))
        {
            throw new DuplicateResourceException("A category named '" + name + "' already exists");
        }

        DefaultCategory saved = categoryRepository.save(CategoryMappers.apply(category, request));

        log.info("Admin updated default category id={}", id);
        return CategoryMappers.toDetails(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponses.Details changeStatus(Long id, boolean active)
    {
        DefaultCategory category = getCategoryOrThrow(id);
        category.setActive(active);
        DefaultCategory saved = categoryRepository.save(category);

        log.info("Admin {} default category id={}", active ? "activated" : "deactivated", id);
        return CategoryMappers.toDetails(saved);
    }

    @Override
    public List<CategoryResponses.Summary> findActive()
    {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(CategoryMappers::toSummary)
                .toList();
    }

    private DefaultCategory getCategoryOrThrow(Long id)
    {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No default category with id " + id));
    }
}
