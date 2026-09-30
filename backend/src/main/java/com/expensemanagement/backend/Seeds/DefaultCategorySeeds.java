package com.expensemanagement.backend.Seeds;

import com.expensemanagement.backend.CategoryManagement.Entities.DefaultCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.DefaultCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Starter set of default expense categories (US09).
 *
 * <p>Unlike {@link UserSeeds} this is not development data - a real platform needs a sensible
 * category structure from day one - so it is not behind {@code app.seed.enabled}. It only runs
 * when the table is empty, so categories an administrator has edited are never overwritten.
 */
@Component
@Slf4j
public class DefaultCategorySeeds implements ApplicationRunner
{
    private final DefaultCategoryRepository categoryRepository;

    public DefaultCategorySeeds(DefaultCategoryRepository categoryRepository)
    {
        this.categoryRepository = categoryRepository;
    }

    private record Seed(String name, String description, String icon, String color)
    {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("Food & Groceries", "Supermarket, market and everyday food", "cart", "#1F9D55"),
            new Seed("Restaurants", "Eating out, cafes and takeaway", "restaurant", "#E67E22"),
            new Seed("Transport", "Fuel, public transport, taxis and parking", "car", "#2D7FF9"),
            new Seed("Housing", "Rent, mortgage and home maintenance", "home", "#8E44AD"),
            new Seed("Utilities", "Electricity, water, gas, internet and phone", "bolt", "#F1C40F"),
            new Seed("Health", "Doctor, pharmacy and insurance", "heart", "#D93025"),
            new Seed("Education", "School fees, books and courses", "book", "#16A085"),
            new Seed("Entertainment", "Outings, streaming and hobbies", "film", "#C0392B"),
            new Seed("Shopping", "Clothes, electronics and other purchases", "bag", "#D35400"),
            new Seed("Other", "Anything that does not fit another category", "dots", "#7F8C8D")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args)
    {
        long existing = categoryRepository.count();
        if (existing > 0)
        {
            log.info("DefaultCategorySeeds skipped - {} categor(ies) already present.", existing);
            return;
        }

        List<DefaultCategory> categories = SEEDS.stream().map(DefaultCategorySeeds::toCategory).toList();
        categoryRepository.saveAll(categories);

        log.info("DefaultCategorySeeds inserted {} default categories.", categories.size());
    }

    private static DefaultCategory toCategory(Seed seed)
    {
        DefaultCategory category = new DefaultCategory();
        category.setName(seed.name());
        category.setDescription(seed.description());
        category.setIcon(seed.icon());
        category.setColor(seed.color());
        return category;
    }
}
