package com.expensemanagement.backend.CategoryManagement.Repositories;

import com.expensemanagement.backend.CategoryManagement.Entities.DefaultCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefaultCategoryRepository extends JpaRepository<DefaultCategory, Long>
{
    boolean existsByNameIgnoreCase(String name);

    /** Same check for a rename: another category may not already use the name. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Page<DefaultCategory> findAllByActive(boolean active, Pageable pageable);

    List<DefaultCategory> findAllByActiveTrueOrderByNameAsc();
}
