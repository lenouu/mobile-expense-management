package com.expensemanagement.backend.BudgetManagement.Services.ServiceImplements;

import com.expensemanagement.backend.BudgetManagement.Calendar.BudgetWindow;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Mappers.BudgetMappers;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetService;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;
import com.expensemanagement.backend.NotificationManagement.Services.ServiceInterface.NotificationService;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Budgets (US28-US33).
 *
 * <p>Three rules shape this class, and each of them exists to keep a goal meaningful:
 *
 * <ol>
 * <li><b>One plan, one window.</b> Windows may not overlap, past or future, so "the budget" always
 * names exactly one plan for a given day.</li>
 * <li><b>A split has to add up.</b> If the user splits the amount across categories, the parts must
 * equal the whole. A plan whose parts do not complete it is not a plan.</li>
 * <li><b>The plan stops moving.</b> Edits are allowed freely before the start, then only in the
 * first 6% of the window and at most three times.</li>
 * </ol>
 *
 * <p>What is deliberately <em>not</em> a rule: spending. Nothing here refuses an expense for being
 * over budget; the overage is a negative balance, and recording continues.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class BudgetServiceImpl implements BudgetService
{
    private final BudgetRepository budgetRepository;
    private final CategoryAllocationRepository allocationRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final NotificationService notificationService;

    public BudgetServiceImpl(BudgetRepository budgetRepository,
                             CategoryAllocationRepository allocationRepository,
                             UserCategoryRepository userCategoryRepository,
                             UserRepository userRepository,
                             ExpenseRepository expenseRepository,
                             ExpenseService expenseService,
                             NotificationService notificationService)
    {
        this.budgetRepository = budgetRepository;
        this.allocationRepository = allocationRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    // Creating and reading
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public BudgetResponses.BudgetDetails createBudget(UUID userId, BudgetRequests.CreateBudget request)
    {
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        requireSensibleWindow(userId, start, end, LocalDate.now(), null);

        BigDecimal total = request.getOverallAmount();
        List<BudgetRequests.AllocationItem> allocations = request.getAllocations();
        requireSplitWithinTotal(total, allocations);

        Budget budget = new Budget();
        budget.setUser(userRepository.getReferenceById(userId));
        budget.setName(trimToNull(request.getName()));
        budget.setStartDate(start);
        budget.setEndDate(end);
        budget.setOverallAmount(total);
        budget.setStatus(initialStatus(start, end, LocalDate.now()));
        budget.setEditsUsed(0);

        Budget saved = budgetRepository.saveAndFlush(budget);
        replaceAllocations(userId, saved, allocations);

        log.info("User id={} created budget id={} '{}' {}..{} for {}",
                userId, saved.getId(), saved.getName(), start, end, total);

        return details(refreshStatus(saved));
    }

    @Override
    @Transactional
    public BudgetResponses.BudgetDetails updateBudget(UUID userId, UUID budgetId, BudgetRequests.UpdateBudget request)
    {
        Budget budget = getBudgetOrThrow(userId, budgetId);
        requireEditable(budget);

        LocalDate start = request.getStartDate() != null ? request.getStartDate() : budget.getStartDate();
        LocalDate end = request.getEndDate() != null ? request.getEndDate() : budget.getEndDate();

        requireSensibleWindow(userId, start, end, LocalDate.now(), budget.getId());
        requireSplitWithinTotal(request.getOverallAmount(), request.getAllocations());

        budget.setName(trimToNull(request.getName()));
        budget.setStartDate(start);
        budget.setEndDate(end);
        budget.setOverallAmount(request.getOverallAmount());
        budget.setEditsUsed(budget.getEditsUsed() + 1);

        Budget saved = budgetRepository.saveAndFlush(budget);
        replaceAllocations(userId, saved, request.getAllocations());

        if (!BudgetWindow.hasEditsLeft(saved.getEditsUsed())) {
            saved.setStatus(BudgetStatus.FIXED);
            saved = budgetRepository.saveAndFlush(saved);
        }

        log.info("User id={} replaced the plan of budget id={} (edit {} of {})",
                userId, budgetId, saved.getEditsUsed(), BudgetWindow.EDIT_CAP);

        return details(refreshStatus(saved));
    }

    @Override
    @Transactional
    public BudgetResponses.BudgetDetails getBudget(UUID userId, UUID budgetId)
    {
        return details(refreshStatus(getBudgetOrThrow(userId, budgetId)));
    }

    @Override
    @Transactional
    public List<BudgetResponses.BudgetSummary> listMine(UUID userId)
    {
        return budgetRepository.findAllByUserIdNewestFirst(userId).stream()
                .map(this::refreshStatus)
                .map(budget -> BudgetMappers.toSummary(budget, spentOf(userId, budget)))
                .toList();
    }

    @Override
    @Transactional
    public Page<ExpenseResponses.Details> expensesOf(UUID userId, UUID budgetId, Pageable pageable)
    {
        getBudgetOrThrow(userId, budgetId);
        return expenseService.findMine(userId, null, null, null, null, null, budgetId, pageable);
    }

    @Override
    @Transactional
    public void deleteBudget(UUID userId, UUID budgetId)
    {
        Budget budget = getBudgetOrThrow(userId, budgetId);
        refreshStatus(budget);

        if (budget.getStatus() == BudgetStatus.CLOSED)
        {
            throw new InvalidRequestException(
                    "A finished budget is history and cannot be deleted.");
        }

        // The expenses are the user's financial record (US20: they are tracked whether or not a
        // budget exists). Deleting a plan must not delete history, so the rows are unlinked rather
        // than removed: they stay in the ledger as ordinary expenses.
        int unlinked = expenseRepository.clearBudget(budgetId, userId);

        budgetRepository.delete(budget);

        log.info("User id={} deleted budget id={}; {} expense(s) kept and unlinked from it",
                userId, budgetId, unlinked);
    }

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    /**
     * The window itself: not in the past, at least a week, at most a year, and nobody else's days.
     *
     * <p>{@code excludedBudgetId} lets an update ignore the budget being changed when looking for
     * overlaps.
     */
    private void requireSensibleWindow(UUID userId, LocalDate start, LocalDate end, LocalDate today,
                                      UUID excludedBudgetId)
    {
        if (start.isBefore(today))
        {
            throw new InvalidRequestException("A budget cannot start in the past.");
        }
        if (!end.isAfter(start))
        {
            throw new InvalidRequestException("The end date must be after the start date.");
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < BudgetWindow.MINIMUM_DAYS)
        {
            throw new InvalidRequestException(
                    "A budget must last at least " + BudgetWindow.MINIMUM_DAYS + " days.");
        }
        if (days > BudgetWindow.MAXIMUM_DAYS)
        {
            throw new InvalidRequestException("A budget can last at most a year.");
        }

        Optional<Budget> clash = budgetRepository.findOverlapping(
                userId, excludedBudgetId, start, end);
        if (clash.isPresent())
        {
            Budget other = clash.get();
            throw new InvalidRequestException("These dates overlap another budget ("
                    + other.getStartDate() + " to " + other.getEndDate() + "). Only one budget can run at a time.");
        }
    }

    /**
     * The split may not spend more than the total.
     *
     * <p>US31 puts the ceiling on the allocations: each one, and their sum, must stay inside the
     * budget. Leaving part of it unallocated is allowed and meaningful - a user may plan 100 000 and
     * only split 60 000 of it, keeping the rest for what they have not thought of yet.
     *
     * <p>The one thing that cannot happen is a split that promises more than the plan.
     */
    private void requireSplitWithinTotal(BigDecimal total, List<BudgetRequests.AllocationItem> allocations)
    {
        if (allocations == null || allocations.isEmpty())
        {
            return;
        }

        BigDecimal allocated = allocations.stream()
                .map(BudgetRequests.AllocationItem::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (allocated.compareTo(total) > 0)
        {
            throw new InvalidRequestException("The categories add up to " + allocated
                    + ", which is more than the budget of " + total
                    + " (" + allocated.subtract(total) + " over).");
        }

        long distinct = allocations.stream()
                .map(BudgetRequests.AllocationItem::getCategoryId)
                .distinct()
                .count();
        if (distinct != allocations.size())
        {
            throw new InvalidRequestException("A category can only appear once in a budget.");
        }
    }

    /** Whether the plan may still be changed: intended for the budget's own window. */
    private void requireEditable(Budget budget)
    {
        refreshStatus(budget);

        if (budget.getStatus() == BudgetStatus.CLOSED)
        {
            throw new InvalidRequestException("This budget's window has ended, so it can no longer be changed.");
        }

        if (!BudgetWindow.hasEditsLeft(budget.getEditsUsed()))
        {
            throw new InvalidRequestException("This budget has used its " + BudgetWindow.EDIT_CAP
                    + " edits, so it is fixed.");
        }

        if (!BudgetWindow.isWindowOpen(budget, LocalDateTime.now()))
        {
            throw new InvalidRequestException("The editing window has closed: a running budget can only"
                    + " be changed in the first 6% of its length.");
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private BudgetStatus initialStatus(LocalDate start, LocalDate end, LocalDate today)
    {
        if (today.isBefore(start))
        {
            return BudgetStatus.UPCOMING;
        }

        return today.isAfter(end) ? BudgetStatus.CLOSED : BudgetStatus.ACTIVE;
    }

    /**
     * Brings the status in line with the clock, closing the budget when its window has ended.
     *
     * <p>Called on every read, so the state is right whenever anyone looks - and the recap is
     * written exactly once, guarded by {@code closedAt}.
     */
    private Budget refreshStatus(Budget budget)
    {
        if (budget.getStatus() == BudgetStatus.CLOSED && budget.getClosedAt() != null)
        {
            return budget;
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(budget.getEndDate()))
        {
            budget.setStatus(BudgetStatus.CLOSED);
            budget.setClosedAt(LocalDateTime.now());
            Budget closed = budgetRepository.saveAndFlush(budget);

            announceClosed(closed);
            log.info("Budget id={} closed for {}..{}", closed.getId(),
                    closed.getStartDate(), closed.getEndDate());

            return closed;
        }

        if (today.isBefore(budget.getStartDate()))
        {
            budget.setStatus(BudgetStatus.UPCOMING);
            return budget;
        }

        boolean stillEditable = BudgetWindow.isWindowOpen(budget, LocalDateTime.now())
                && BudgetWindow.hasEditsLeft(budget.getEditsUsed());

        BudgetStatus next = stillEditable ? BudgetStatus.ACTIVE : BudgetStatus.FIXED;
        if (budget.getStatus() != next)
        {
            budget.setStatus(next);
            return budgetRepository.saveAndFlush(budget);
        }

        return budget;
    }

    /** The recap: one notification, with the verdict in its wording. */
    private void announceClosed(Budget budget)
    {
        UUID userId = budget.getUser().getId();
        BigDecimal spent = spentOf(userId, budget);
        boolean met = spent.compareTo(budget.getOverallAmount()) <= 0;

        String title = budget.getName() != null ? "Your budget '" + budget.getName() + "' has ended"
                : "Your budget has ended";
        String body = met
                ? "You planned " + amount(budget.getOverallAmount()) + " and spent " + amount(spent)
                        + ". You stayed within it - well done."
                : "You planned " + amount(budget.getOverallAmount()) + " and spent " + amount(spent)
                        + ", " + amount(spent.subtract(budget.getOverallAmount())) + " over.";

        notificationService.notify(userId, NotificationKind.BUDGET_PERIOD_CLOSED, title, body);
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /** Rewrites the plan's categories wholesale: the request is the plan, not a patch. */
    private void replaceAllocations(UUID userId, Budget budget, List<BudgetRequests.AllocationItem> items)
    {
        allocationRepository.deleteAll(allocationRepository.findAllByBudgetId(budget.getId()));

        if (items == null || items.isEmpty())
        {
            return;
        }

        List<CategoryAllocation> allocations = new ArrayList<>();
        for (BudgetRequests.AllocationItem item : items)
        {
            UserCategory category = userCategoryRepository.findByIdAndUserId(item.getCategoryId(), userId)
                    .orElseThrow(() -> new InvalidRequestException(
                            "Category " + item.getCategoryId() + " is not one of your categories"));

            CategoryAllocation allocation = new CategoryAllocation();
            allocation.setBudget(budget);
            allocation.setCategory(category);
            allocation.setAllocatedAmount(item.getAllocatedAmount());
            allocations.add(allocation);
        }

        allocationRepository.saveAll(allocations);
    }

    private BudgetResponses.BudgetDetails details(Budget budget)
    {
        UUID userId = budget.getUser().getId();
        List<CategoryAllocation> allocations = allocationRepository.findAllByBudgetId(budget.getId());
        ExpenseRepository.Totals totals = expenseRepository.totalsByBudget(userId, budget.getId());

        boolean editable = budget.getStatus() != BudgetStatus.CLOSED
                && BudgetWindow.hasEditsLeft(budget.getEditsUsed())
                && BudgetWindow.isWindowOpen(budget, LocalDateTime.now());

        return BudgetMappers.toBudgetDetails(budget, allocations, totals.getTotal(), totals.getCount(),
                spentByCategoryOf(userId, budget), editable);
    }

    private BigDecimal spentOf(UUID userId, Budget budget)
    {
        return expenseRepository.totalsByBudget(userId, budget.getId()).getTotal();
    }

    private Map<UUID, BigDecimal> spentByCategoryOf(UUID userId, Budget budget)
    {
        Map<UUID, BigDecimal> spent = new HashMap<>();
        for (Object[] row : expenseRepository.sumByCategoryForBudget(userId, budget.getId()))
        {
            spent.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return spent;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String amount(BigDecimal value)
    {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private Budget getBudgetOrThrow(UUID userId, UUID budgetId)
    {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No budget with id " + budgetId));
    }
}
