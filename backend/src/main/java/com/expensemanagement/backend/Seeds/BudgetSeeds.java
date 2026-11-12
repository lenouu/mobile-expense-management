package com.expensemanagement.backend.Seeds;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Past and running budgets for the seeded account, so the Budget tab opens with a history rather
 * than an empty screen.
 *
 * <p>Each budget is a window of its own - which is the point of the feature - and carries the
 * expenses that were recorded under it. A plan with no spending would show a flawless month nobody
 * lived.
 *
 * <p>Two past budgets are written already closed, verdicts and all, and no notification is
 * generated for them: a user who signs in should not find months of recap waiting. The most recent
 * finished one is left open, so the first read of the tab closes it and writes the one recap the
 * feature is meant to produce. A fourth budget starts today, so the tab also has something running.
 */
@Component
@Order(4)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Slf4j
public class BudgetSeeds implements ApplicationRunner
{
    private static final String TARGET_USER_NAME = "sara.mansouri";

    /** One budget's story: the window, the plan, and what was spent under it. */
    private record Story(
            String name,
            int startsDaysAgo,
            int endsDaysAgo,
            int overallAmount,
            Map<String, Integer> allocations,
            List<Spent> spending,
            boolean leaveOpen,
            boolean running)
    {
    }

    /** One expense recorded under a budget, {@code daysIn} days after it started. */
    private record Spent(String description, int amount, String category, int daysIn)
    {
    }

    private static final List<Story> HISTORY = List.of(
            // Three months ago: planned carefully, finished comfortably inside it.
            new Story("Autumn plan", 90, 61, 120_000,
                    Map.of("Food & Groceries", 45_000, "Transport", 25_000, "Utilities", 25_000,
                            "Entertainment", 25_000),
                    List.of(
                            new Spent("Market run", 21_000, "Food & Groceries", 2),
                            new Spent("Groceries top-up", 15_500, "Food & Groceries", 11),
                            new Spent("Fuel", 12_000, "Transport", 4),
                            new Spent("Bus passes", 6_500, "Transport", 17),
                            new Spent("Electricity", 18_000, "Utilities", 6),
                            new Spent("Internet", 19_900, "Utilities", 9),
                            new Spent("Cinema", 6_000, "Entertainment", 13),
                            new Spent("Streaming", 5_500, "Entertainment", 20)),
                    false, false),

            // Two months ago: the plan was optimistic and the month was not.
            new Story(null, 60, 31, 150_000,
                    Map.of("Food & Groceries", 55_000, "Transport", 30_000, "Shopping", 40_000,
                            "Health", 25_000),
                    List.of(
                            new Spent("Weekly groceries", 24_000, "Food & Groceries", 3),
                            new Spent("Groceries", 22_000, "Food & Groceries", 14),
                            new Spent("Eating out", 18_500, "Food & Groceries", 21),
                            new Spent("Fuel", 16_000, "Transport", 5),
                            new Spent("Taxi", 9_500, "Transport", 18),
                            new Spent("New shoes", 32_000, "Shopping", 8),
                            new Spent("Birthday gift", 15_000, "Shopping", 22),
                            new Spent("Doctor", 10_000, "Health", 12),
                            new Spent("Pharmacy", 8_400, "Health", 24),
                            new Spent("Concert tickets", 18_000, "Entertainment", 16)),
                    false, false),

            // Last month: finished just inside the plan, and left open so the recap appears.
            new Story("Tight month", 30, 1, 140_000,
                    Map.of("Food & Groceries", 50_000, "Transport", 28_000, "Utilities", 42_000,
                            "Education", 20_000),
                    List.of(
                            new Spent("Groceries", 26_000, "Food & Groceries", 2),
                            new Spent("Market", 18_000, "Food & Groceries", 12),
                            new Spent("Lunch at work", 7_500, "Food & Groceries", 19),
                            new Spent("Fuel", 14_000, "Transport", 6),
                            new Spent("Motorbike service", 6_800, "Transport", 15),
                            new Spent("Electricity", 16_000, "Utilities", 8),
                            new Spent("Water", 4_200, "Utilities", 8),
                            new Spent("Internet", 19_900, "Utilities", 10),
                            new Spent("Course materials", 12_500, "Education", 17)),
                    true, false),

            // Starting today: a week of 30 000, empty, so the tab has something running.
            new Story("Week of focus", 0, -6, 30_000,
                    Map.of("Food & Groceries", 18_000, "Transport", 12_000),
                    List.of(),
                    false, true));

    private final UserRepository userRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryAllocationRepository allocationRepository;

    public BudgetSeeds(UserRepository userRepository,
                       UserCategoryRepository userCategoryRepository,
                       ExpenseRepository expenseRepository,
                       BudgetRepository budgetRepository,
                       CategoryAllocationRepository allocationRepository)
    {
        this.userRepository = userRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
        this.allocationRepository = allocationRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args)
    {
        Optional<User> maybeUser = userRepository.findByUserName(TARGET_USER_NAME);
        if (maybeUser.isEmpty())
        {
            log.info("BudgetSeeds skipped - {} was not seeded.", TARGET_USER_NAME);
            return;
        }

        User user = maybeUser.get();

        if (!budgetRepository.findAllByUserIdNewestFirst(user.getId()).isEmpty())
        {
            log.info("BudgetSeeds skipped - {} already has budgets.", TARGET_USER_NAME);
            return;
        }

        Map<String, UserCategory> categories = userCategoryRepository
                .findAllByUserIdOrderByNameAsc(user.getId())
                .stream()
                .collect(Collectors.toMap(
                        category -> category.getName().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (first, other) -> first));

        LocalDate today = LocalDate.now();
        int created = 0;

        for (Story story : HISTORY)
        {
            LocalDate start = today.minusDays(story.startsDaysAgo());
            LocalDate end = today.minusDays(story.endsDaysAgo());

            Budget budget = new Budget();
            budget.setUser(user);
            budget.setName(story.name());
            budget.setStartDate(start);
            budget.setEndDate(end);
            budget.setOverallAmount(BigDecimal.valueOf(story.overallAmount()));
            budget.setEditsUsed(story.running() ? 0 : 3);

            if (story.running() || story.leaveOpen())
            {
                // Left ACTIVE on purpose for the finished one: the first read closes it and writes
                // the recap. The running one simply has not ended yet.
                budget.setStatus(BudgetStatus.ACTIVE);
                budget.setClosedAt(null);
            }
            else
            {
                budget.setStatus(BudgetStatus.CLOSED);
                budget.setClosedAt(end.atTime(20, 0));
            }

            Budget saved = budgetRepository.saveAndFlush(budget);
            seedAllocations(saved, story, categories);
            seedSpending(user, saved, story, categories);

            created++;
            log.info("BudgetSeeds wrote '{}' {}..{} with {} planned",
                    story.name(), start, end, story.overallAmount());
        }

        log.warn("BudgetSeeds inserted {} budgets for '{}'. Development data - set "
                + "app.seed.enabled=false outside development.", created, TARGET_USER_NAME);
    }

    private void seedAllocations(Budget budget, Story story, Map<String, UserCategory> categories)
    {
        List<CategoryAllocation> allocations = new ArrayList<>();

        story.allocations().forEach((categoryName, amount) -> {
            UserCategory category = categories.get(categoryName.toLowerCase(Locale.ROOT));
            if (category == null)
            {
                return;
            }

            CategoryAllocation allocation = new CategoryAllocation();
            allocation.setBudget(budget);
            allocation.setCategory(category);
            allocation.setAllocatedAmount(BigDecimal.valueOf(amount));
            allocations.add(allocation);
        });

        allocationRepository.saveAll(allocations);
    }

    private void seedSpending(User user, Budget budget, Story story, Map<String, UserCategory> categories)
    {
        List<Expense> expenses = new ArrayList<>();

        for (Spent spent : story.spending())
        {
            UserCategory category = categories.get(spent.category().toLowerCase(Locale.ROOT));
            if (category == null)
            {
                continue;
            }

            LocalDate date = budget.getStartDate().plusDays(spent.daysIn());
            if (date.isAfter(budget.getEndDate()))
            {
                date = budget.getEndDate();
            }

            Expense expense = new Expense();
            expense.setUser(user);
            expense.setCategory(category);
            // The whole point of the feature: these expenses count against this budget.
            expense.setBudget(budget);
            expense.setAmount(BigDecimal.valueOf(spent.amount()));
            expense.setCurrency("XAF");
            expense.setDate(date);
            expense.setDescription(spent.description());
            expenses.add(expense);
        }

        expenseRepository.saveAll(expenses);
    }
}
