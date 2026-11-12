package com.expensemanagement.backend.ExpenseManagement.Repositories.Projections;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spring Data projection for per-category aggregated spending (S8-TECH-1).
 */
public interface CategorySpendingProjection
{
    UUID getCategoryId();

    String getCategoryName();

    String getCategoryIcon();

    String getCategoryColor();

    BigDecimal getTotalSpent();
}
