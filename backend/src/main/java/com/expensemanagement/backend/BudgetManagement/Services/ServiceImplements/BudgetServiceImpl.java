package com.expensemanagement.backend.BudgetManagement.Services.ServiceImplements;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.CategoryAllocationRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Mappers.BudgetMappers;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetService;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Budget and category allocation service (S4-TECH-2, US28).
 * Enforces that total category allocations do not exceed the overall budget amount,
 * while permitting allocations to sum to less than or equal to the overall budget.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class BudgetServiceImpl implements BudgetService
{
    private final BudgetRepository budgetRepository;
    private final CategoryAllocationRepository allocationRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final FinancialPeriodRepository periodRepository;
    private final UserRepository userRepository;

    public BudgetServiceImpl(BudgetRepository budgetRepository,
                             CategoryAllocationRepository allocationRepository,
                             UserCategoryRepository userCategoryRepository,
                             FinancialPeriodRepository periodRepository,
                             UserRepository userRepository)
    {
        this.budgetRepository = budgetRepository;
        this.allocationRepository = allocationRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.periodRepository = periodRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public BudgetResponses.BudgetDetails createBudget(Long userId, BudgetRequests.CreateBudget request)
    {
        FinancialPeriod period = getOwnPeriodOrThrow(userId, request.getPeriodId());

        if (budgetRepository.findByUserIdAndPeriodId(userId, period.getId()).isPresent())
        {
            throw new DuplicateResourceException("A budget already exists for period id " + period.getId());
        }

        Budget budget = new Budget();
        budget.setUser(userRepository.getReferenceById(userId));
        budget.setPeriod(period);
        budget.setOverallAmount(request.getOverallAmount());

        Budget saved = budgetRepository.save(budget);
        log.info("User id={} created budget id={} with overallAmount={}", userId, saved.getId(), saved.getOverallAmount());
        return BudgetMappers.toBudgetDetails(saved, List.of());
    }

    @Override
    @Transactional
    public BudgetResponses.BudgetDetails updateBudget(Long userId, Long budgetId, BudgetRequests.UpdateBudget request)
    {
        Budget budget = getBudgetOrThrow(userId, budgetId);

        BigDecimal currentAllocated = allocationRepository.sumAllocationsByBudgetId(budget.getId());
        if (request.getOverallAmount().compareTo(currentAllocated) < 0)
        {
            throw new InvalidRequestException(
                    "Overall budget (" + request.getOverallAmount()
                            + ") cannot be less than total current category allocations (" + currentAllocated + ")"
            );
        }

        budget.setOverallAmount(request.getOverallAmount());
        Budget saved = budgetRepository.saveAndFlush(budget);
        log.info("User id={} updated budget id={} overallAmount to {}", userId, budgetId, request.getOverallAmount());

        List<CategoryAllocation> allocations = allocationRepository.findAllByBudgetId(budgetId);
        return BudgetMappers.toBudgetDetails(saved, allocations);
    }

    @Override
    public BudgetResponses.BudgetDetails getBudget(Long userId, Long budgetId)
    {
        Budget budget = getBudgetOrThrow(userId, budgetId);
        List<CategoryAllocation> allocations = allocationRepository.findAllByBudgetId(budgetId);
        return BudgetMappers.toBudgetDetails(budget, allocations);
    }

    @Override
    public BudgetResponses.BudgetDetails getBudgetByPeriod(Long userId, Long periodId)
    {
        getOwnPeriodOrThrow(userId, periodId);
        Budget budget = budgetRepository.findByUserIdAndPeriodId(userId, periodId)
                .orElseThrow(() -> new ResourceNotFoundException("No budget found for period id " + periodId));

        List<CategoryAllocation> allocations = allocationRepository.findAllByBudgetId(budget.getId());
        return BudgetMappers.toBudgetDetails(budget, allocations);
    }

    @Override
    @Transactional
    public BudgetResponses.AllocationDetails addOrUpdateAllocation(Long userId, Long budgetId, CategoryAllocationRequests.SaveAllocation request)
    {
        Budget budget = getBudgetOrThrow(userId, budgetId);
        UserCategory category = getOwnCategoryOrThrow(userId, request.getCategoryId());

        Optional<CategoryAllocation> existing = allocationRepository.findByBudgetIdAndCategoryId(budgetId, category.getId());
        Long existingAllocationId = existing.map(CategoryAllocation::getId).orElse(null);

        // Core validation logic (S4-TECH-2)
        validateAllocationAgainstBudget(budget, request.getAllocatedAmount(), existingAllocationId);

        CategoryAllocation allocation = existing.orElseGet(() -> {
            CategoryAllocation ca = new CategoryAllocation();
            ca.setBudget(budget);
            ca.setCategory(category);
            return ca;
        });

        allocation.setAllocatedAmount(request.getAllocatedAmount());
        CategoryAllocation saved = allocationRepository.saveAndFlush(allocation);

        log.info("User id={} saved category allocation id={} for budget id={}, category id={}, amount={}",
                userId, saved.getId(), budgetId, category.getId(), saved.getAllocatedAmount());

        return BudgetMappers.toAllocationDetails(saved);
    }

    @Override
    @Transactional
    public BudgetResponses.AllocationDetails updateAllocation(Long userId, Long budgetId, Long allocationId, CategoryAllocationRequests.UpdateAllocation request)
    {
        Budget budget = getBudgetOrThrow(userId, budgetId);
        CategoryAllocation allocation = allocationRepository.findByIdAndBudgetId(allocationId, budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("No allocation with id " + allocationId + " in budget " + budgetId));

        // Core validation logic (S4-TECH-2)
        validateAllocationAgainstBudget(budget, request.getAllocatedAmount(), allocationId);

        allocation.setAllocatedAmount(request.getAllocatedAmount());
        CategoryAllocation saved = allocationRepository.saveAndFlush(allocation);

        log.info("User id={} updated allocation id={} to amount={}", userId, allocationId, request.getAllocatedAmount());
        return BudgetMappers.toAllocationDetails(saved);
    }

    @Override
    @Transactional
    public void deleteAllocation(Long userId, Long budgetId, Long allocationId)
    {
        getBudgetOrThrow(userId, budgetId);
        CategoryAllocation allocation = allocationRepository.findByIdAndBudgetId(allocationId, budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("No allocation with id " + allocationId + " in budget " + budgetId));

        allocationRepository.delete(allocation);
        log.info("User id={} deleted allocation id={}", userId, allocationId);
    }

    @Override
    public List<BudgetResponses.AllocationDetails> getAllocations(Long userId, Long budgetId)
    {
        getBudgetOrThrow(userId, budgetId);
        return allocationRepository.findAllByBudgetId(budgetId)
                .stream()
                .map(BudgetMappers::toAllocationDetails)
                .toList();
    }

    @Override
    public void validateAllocationAgainstBudget(Budget budget, BigDecimal proposedAmount, Long existingAllocationId)
    {
        if (proposedAmount == null || proposedAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new InvalidRequestException("Allocation amount must be greater than 0");
        }

        BigDecimal otherAllocationsTotal = existingAllocationId != null
                ? allocationRepository.sumAllocationsByBudgetIdExcluding(budget.getId(), existingAllocationId)
                : allocationRepository.sumAllocationsByBudgetId(budget.getId());

        BigDecimal newTotal = otherAllocationsTotal.add(proposedAmount);

        if (newTotal.compareTo(budget.getOverallAmount()) > 0)
        {
            throw new InvalidRequestException(
                    "Total category allocations (" + newTotal + ") cannot exceed overall budget (" + budget.getOverallAmount() + ")"
            );
        }
    }

    private Budget getBudgetOrThrow(Long userId, Long budgetId)
    {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No budget with id " + budgetId));
    }

    private UserCategory getOwnCategoryOrThrow(Long userId, Long categoryId)
    {
        return userCategoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new InvalidRequestException("Category " + categoryId + " is not one of your categories"));
    }

    private FinancialPeriod getOwnPeriodOrThrow(Long userId, Long periodId)
    {
        return periodRepository.findByIdAndUserId(periodId, userId)
                .orElseThrow(() -> new InvalidRequestException("Financial period " + periodId + " is not one of your periods"));
    }
}
