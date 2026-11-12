package com.expensemanagement.backend.Seeds;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import com.expensemanagement.backend.IncomeManagement.Entities.IncomeSource;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeSourceRepository;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Income sources and a few months of income for the seeded account.
 *
 * <p>Sources come first, because every payment needs one. The set is the same starter list every
 * account gets, with the icons and colours the app draws. The payments themselves are the ordinary
 * shape of a month: a salary, some freelance work, and a gift - enough for the income list's
 * by-source block to mean something when the screen is first opened.
 */
@Component
@Order(5)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Slf4j
public class IncomeSeeds implements ApplicationRunner
{
    private static final String TARGET_USER_NAME = "sara.mansouri";

    /** The starter set: name, icon, colour, and what belongs in it. */
    private record SourceSeed(String name, String icon, String color, String description)
    {
    }

    private static final List<SourceSeed> SOURCES = List.of(
            new SourceSeed("Salary", "💼", "#1F9D55", "Your regular pay"),
            new SourceSeed("Freelance", "💻", "#2D7FF9", "Contract and side work"),
            new SourceSeed("Gift", "🎁", "#8E44AD", "Money received from someone"),
            new SourceSeed("Refund", "🧾", "#F1C40F", "Money given back to you"),
            new SourceSeed("Other income", "✨", "#7F8C8D", "Anything that does not fit another source"));

    /** One payment: source name, label, amount, and how many days ago it arrived. */
    private record Payment(String sourceName, String name, int amount, int daysAgo, String description)
    {
    }

    private static final List<Payment> PAYMENTS = List.of(
            new Payment("Salary", "October salary", 480_000, 42, "Net pay, October"),
            new Payment("Freelance", "Logo design", 90_000, 35, "Two-week contract"),
            new Payment("Gift", "From my brother", 25_000, 28, null),
            new Payment("Salary", "November salary", 480_000, 11, "Net pay, November"),
            new Payment("Freelance", "Website copy", 60_000, 6, "Five pages"),
            new Payment("Salary", null, 480_000, 72, "Net pay, September"));

    private final UserRepository userRepository;
    private final IncomeSourceRepository sourceRepository;
    private final IncomeRepository incomeRepository;

    public IncomeSeeds(UserRepository userRepository,
                       IncomeSourceRepository sourceRepository,
                       IncomeRepository incomeRepository)
    {
        this.userRepository = userRepository;
        this.sourceRepository = sourceRepository;
        this.incomeRepository = incomeRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args)
    {
        Optional<User> maybeUser = userRepository.findByUserName(TARGET_USER_NAME);
        if (maybeUser.isEmpty())
        {
            log.info("IncomeSeeds skipped - {} was not seeded.", TARGET_USER_NAME);
            return;
        }

        User user = maybeUser.get();

        if (!sourceRepository.findAllByUserIdOrderByNameAsc(user.getId()).isEmpty())
        {
            log.info("IncomeSeeds skipped - {} already has income sources.", TARGET_USER_NAME);
            return;
        }

        Map<String, IncomeSource> sources = new LinkedHashMap<>();
        List<IncomeSource> created = new ArrayList<>();

        for (SourceSeed seed : SOURCES)
        {
            IncomeSource source = new IncomeSource();
            source.setUser(user);
            source.setName(seed.name());
            source.setIcon(seed.icon());
            source.setColor(seed.color());
            source.setDescription(seed.description());
            created.add(source);
            sources.put(seed.name().toLowerCase(Locale.ROOT), source);
        }

        sourceRepository.saveAll(created);

        List<Income> payments = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Payment payment : PAYMENTS)
        {
            IncomeSource source = sources.get(payment.sourceName().toLowerCase(Locale.ROOT));
            if (source == null)
            {
                continue;
            }

            Income income = new Income();
            income.setUser(user);
            income.setSource(source);
            income.setName(payment.name());
            income.setAmount(BigDecimal.valueOf(payment.amount()));
            income.setCurrency("XAF");
            income.setDate(today.minusDays(payment.daysAgo()));
            income.setDescription(payment.description());
            payments.add(income);
        }

        incomeRepository.saveAll(payments);

        log.warn("IncomeSeeds inserted {} sources and {} incomes for '{}'. Development data - set "
                + "app.seed.enabled=false outside development.",
                created.size(), payments.size(), TARGET_USER_NAME);
    }
}
