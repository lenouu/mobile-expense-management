package com.expensemanagement.backend.Seeds;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodSettingsRepository;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Development expenses for one seeded account, so the Transactions screen has something to show
 * without anybody typing forty rows by hand.
 *
 * <p>Only expenses: no incomes, no budgets, nothing else. Every row is filed under the user's
 * <em>own</em> category copies (expenses reference those, not the platform defaults), and every
 * date falls inside the current financial period and no later than today - a date outside the
 * period would be correct data that the default "This period" window could never display.
 *
 * <p>Runs after {@link UserSeeds} ({@code @Order(3)}), which is what creates the account and
 * provisions its categories. Skips itself when the account is missing, or when it already has
 * expenses, so it is safe to run on every boot: with {@code ddl-auto=create} the schema is new
 * each time, and with a real database it simply does nothing after the first run.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Slf4j
public class ExpenseSeeds implements ApplicationRunner
{
    /** The account these expenses belong to. */
    private static final String TARGET_USER_NAME = "sara.mansouri";

    private static final String CURRENCY = "XAF";

    /**
     * One row of seed data: what it was, how much, and which of the account's categories it
     * belongs to. Forty-eight rows, so the Transactions screen spans three pages at twenty a
     * page and the pager has something to page through.
     */
    private record Seed(String description, String amount, String category)
    {
    }

    private static final List<Seed> SEEDS = List.of(
            // Food and groceries
            new Seed("Weekly groceries at the market", "18500", "Food & Groceries"),
            new Seed("Bread and milk", "1200", "Food & Groceries"),
            new Seed("Rice, oil and spices", "7400", "Food & Groceries"),
            new Seed("Fruit and vegetables", "3600", "Food & Groceries"),
            new Seed("Coffee beans", "4500", "Food & Groceries"),
            new Seed("Snacks for the office", "2100", "Food & Groceries"),
            new Seed("Water bottles", "900", "Food & Groceries"),
            new Seed("Groceries run", "12300", "Food & Groceries"),

            // Eating out
            new Seed("Lunch with the team", "5500", "Restaurants"),
            new Seed("Breakfast on the way to work", "1800", "Restaurants"),
            new Seed("Dinner with friends", "12400", "Restaurants"),
            new Seed("Street food", "1500", "Restaurants"),
            new Seed("Afternoon coffee", "1200", "Restaurants"),
            new Seed("Sunday brunch", "8700", "Restaurants"),

            // Transport
            new Seed("Taxi to the airport", "9500", "Transport"),
            new Seed("Fuel", "15000", "Transport"),
            new Seed("Bus tickets", "600", "Transport"),
            new Seed("Parking", "500", "Transport"),
            new Seed("Ride home in the rain", "2200", "Transport"),
            new Seed("Motorbike service", "6800", "Transport"),
            new Seed("Taxi to the office", "2500", "Transport"),
            new Seed("Fuel top-up", "8000", "Transport"),

            // Housing and utilities
            new Seed("Electricity bill", "14500", "Utilities"),
            new Seed("Water bill", "4200", "Utilities"),
            new Seed("Internet subscription", "19900", "Utilities"),
            new Seed("Phone credit", "3000", "Utilities"),
            new Seed("Plumber for the leaking tap", "7500", "Housing"),
            new Seed("Cleaning supplies", "3400", "Housing"),
            new Seed("Furniture repair", "9500", "Housing"),

            // Health
            new Seed("Pharmacy — painkillers", "2800", "Health"),
            new Seed("Doctor appointment", "10000", "Health"),
            new Seed("Dental check-up", "15000", "Health"),
            new Seed("Vitamins", "5600", "Health"),

            // Education
            new Seed("Course materials", "12500", "Education"),
            new Seed("Textbook", "8900", "Education"),
            new Seed("Online course subscription", "11000", "Education"),

            // Entertainment
            new Seed("Cinema tickets", "6000", "Entertainment"),
            new Seed("Streaming subscription", "5500", "Entertainment"),
            new Seed("Concert ticket", "18000", "Entertainment"),
            new Seed("Board game", "7500", "Entertainment"),

            // Shopping
            new Seed("Running shoes", "24000", "Shopping"),
            new Seed("T-shirt", "6500", "Shopping"),
            new Seed("Phone case", "3000", "Shopping"),
            new Seed("Kitchen scale", "8200", "Shopping"),
            new Seed("Gift for a birthday", "15000", "Shopping"),

            // Other
            new Seed("Haircut", "4000", "Other"),
            new Seed("Laundry", "2500", "Other"),
            new Seed("Charity donation", "5000", "Other"),
            new Seed("Bank charges", "1500", "Other"));

    /**
     * How far back the seeded account's first period is back-dated, in days.
     *
     * <p>Three past months plus the current one. The current period still ends within days of
     * today - so these expenses are spread over the last month, as before - and the three periods
     * before it exist, which is what gives the Budget tab a history to show.
     */
    private static final int HISTORY_DAYS = 119;

    private final UserRepository userRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final ExpenseRepository expenseRepository;
    private final FinancialPeriodService periodService;
    private final FinancialPeriodSettingsRepository settingsRepository;
    private final FinancialPeriodRepository periodRepository;

    public ExpenseSeeds(UserRepository userRepository,
                        UserCategoryRepository userCategoryRepository,
                        ExpenseRepository expenseRepository,
                        FinancialPeriodService periodService,
                        FinancialPeriodSettingsRepository settingsRepository,
                        FinancialPeriodRepository periodRepository)
    {
        this.userRepository = userRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.expenseRepository = expenseRepository;
        this.periodService = periodService;
        this.settingsRepository = settingsRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args)
    {
        Optional<User> maybeUser = userRepository.findByUserName(TARGET_USER_NAME);
        if (maybeUser.isEmpty())
        {
            log.info("ExpenseSeeds skipped - {} was not seeded.", TARGET_USER_NAME);
            return;
        }

        User user = maybeUser.get();
        List<UserCategory> categories = userCategoryRepository.findAllByUserIdOrderByNameAsc(user.getId());
        if (categories.isEmpty())
        {
            log.info("ExpenseSeeds skipped - {} has no categories to file expenses under.", TARGET_USER_NAME);
            return;
        }

        Map<String, UserCategory> byName = categories.stream()
                .collect(Collectors.toMap(UserCategory::getName, Function.identity(), (first, other) -> first));

        LocalDate today = LocalDate.now();

        // A newly seeded account signs up today, so its first period starts today and every date
        // inside it would be today - a month of history collapsed into one day. Back-dating the
        // anchor makes the current period cover the last month, which is what makes the seeded
        // data useful in the app: a spread of days, and a "This period" window that contains them.
        //
        // Only when no period exists yet: changing the anchor after periods have been created does
        // not move them, so it would achieve nothing and could contradict their dates.
        boolean hasPeriods = periodRepository.findFirstByUserIdOrderBySequenceDesc(user.getId()).isPresent();
        if (!hasPeriods)
        {
            FinancialPeriodSettings settings = periodService.settings(user.getId());
            settings.setAnchorDate(today.minusDays(HISTORY_DAYS));
            settings.setAnchorSequence(1);
            settings.setLengthCount(1);
            settings.setLengthUnit(PeriodUnit.MONTHS);
            settings.setDefaultLength(true);
            settingsRepository.save(settings);
        }

        // The window these dates have to sit in: inside the current period, and never in the future.
        FinancialPeriod period = periodService.current(user.getId());
        LocalDate from = period.getStartDate();
        long span = Math.max(1, ChronoUnit.DAYS.between(from, today) + 1);

        List<Expense> expenses = new ArrayList<>(SEEDS.size());
        for (int i = 0; i < SEEDS.size(); i++)
        {
            Seed seed = SEEDS.get(i);

            // Falls back to the first category so a renamed seed cannot silently drop rows.
            UserCategory category = byName.getOrDefault(seed.category(), categories.get(0));

            Expense expense = new Expense();
            expense.setUser(user);
            expense.setCategory(category);
            expense.setAmount(new BigDecimal(seed.amount()));
            expense.setCurrency(CURRENCY);
            // Spread across the elapsed part of the period, cycling so several land on a day.
            expense.setDate(today.minusDays(i % span));
            expense.setDescription(seed.description());

            expenses.add(expense);
        }

        expenseRepository.saveAll(expenses);

        log.warn("ExpenseSeeds inserted {} expenses for '{}' spanning {} to {}. "
                        + "Development data - set app.seed.enabled=false outside development.",
                expenses.size(), TARGET_USER_NAME, from, today);
    }
}
