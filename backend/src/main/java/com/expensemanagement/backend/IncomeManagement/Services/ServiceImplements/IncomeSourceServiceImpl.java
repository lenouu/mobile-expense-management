package com.expensemanagement.backend.IncomeManagement.Services.ServiceImplements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeSourceRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeSourceResponses;
import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import com.expensemanagement.backend.IncomeManagement.Entities.IncomeSource;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeSourceRepository;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface.IncomeSourceService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * Income sources (the "where did it come from" list).
 *
 * <p>Every rule here mirrors the expense categories, on purpose: the two lists sit side by side in
 * one screen and are taught to the user by the same gestures. Names are unique per account, and a
 * source that is in use cannot be deleted - the message names the payments that block it, so the
 * user is told exactly what to move.
 */
@Service
@Transactional
public class IncomeSourceServiceImpl implements IncomeSourceService
{
    private static final String DEFAULT_COLOR = "#01916D";

    /** The starter set every account begins with: name, icon, colour, description. */
    private static final List<String[]> DEFAULTS = List.of(
            new String[]{"Salary", "💼", "#1F9D55", "Your regular pay"},
            new String[]{"Freelance", "💻", "#2D7FF9", "Contract and side work"},
            new String[]{"Gift", "🎁", "#8E44AD", "Money received from someone"},
            new String[]{"Refund", "🧾", "#F1C40F", "Money given back to you"},
            new String[]{"Other income", "✨", "#7F8C8D", "Anything that does not fit another source"});


    private final IncomeSourceRepository sourceRepository;
    private final IncomeRepository incomeRepository;
    private final UserRepository userRepository;

    public IncomeSourceServiceImpl(IncomeSourceRepository sourceRepository,
                                   IncomeRepository incomeRepository,
                                   UserRepository userRepository)
    {
        this.sourceRepository = sourceRepository;
        this.incomeRepository = incomeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void provisionDefaults(User user)
    {
        if (sourceRepository.countByUserId(user.getId()) > 0)
        {
            return;
        }

        List<IncomeSource> sources = new ArrayList<>();
        for (String[] seed : DEFAULTS)
        {
            IncomeSource source = new IncomeSource();
            source.setUser(user);
            source.setName(seed[0]);
            source.setIcon(seed[1]);
            source.setColor(seed[2]);
            source.setDescription(seed[3]);
            sources.add(source);
        }

        sourceRepository.saveAll(sources);
    }

    @Override
    public IncomeSourceResponses.Details create(UUID userId, IncomeSourceRequests.SaveIncomeSource request)
    {
        User user = getUserOrThrow(userId);
        String name = request.getName().trim();

        if (sourceRepository.existsByUserIdAndNameIgnoreCase(userId, name))
        {
            throw new DuplicateResourceException("You already have a source called " + name + ".");
        }

        IncomeSource source = new IncomeSource();
        source.setUser(user);
        source.setName(name);
        source.setIcon(blankToNull(request.getIcon()));
        source.setColor(blankToNull(request.getColor()) == null ? DEFAULT_COLOR : request.getColor().trim());
        source.setDescription(blankToNull(request.getDescription()));

        return toDetails(sourceRepository.save(source));
    }

    @Override
    public IncomeSourceResponses.Details update(UUID userId, UUID sourceId,
                                                IncomeSourceRequests.SaveIncomeSource request)
    {
        IncomeSource source = requireSource(userId, sourceId);
        String name = request.getName().trim();

        if (sourceRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, sourceId))
        {
            throw new DuplicateResourceException("You already have another source called " + name + ".");
        }

        source.setName(name);
        source.setIcon(blankToNull(request.getIcon()));
        if (blankToNull(request.getColor()) != null)
        {
            source.setColor(request.getColor().trim());
        }
        source.setDescription(blankToNull(request.getDescription()));

        return toDetails(sourceRepository.save(source));
    }

    @Override
    public void delete(UUID userId, UUID sourceId)
    {
        IncomeSource source = requireSource(userId, sourceId);

        long used = incomeRepository.countBySourceIdAndUserId(sourceId, userId);
        if (used > 0)
        {
            // Name the offenders: a count alone leaves the user hunting through their history.
            String examples = describeIncomes(incomeRepository
                    .findTop3BySourceIdAndUserIdOrderByDateDesc(sourceId, userId));

            throw new OperationNotAllowedException(used + (used == 1 ? " income entry uses " : " income entries use ")
                    + source.getName() + ": " + examples + "."
                    + (used > 3 ? " and " + (used - 3) + " more." : "")
                    + " Move or delete them first.");
        }

        sourceRepository.delete(source);
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeSourceResponses.Listing listMine(UUID userId)
    {
        List<IncomeSourceResponses.Details> items = sourceRepository
                .findAllByUserIdOrderByNameAsc(userId)
                .stream()
                .map(this::toDetails)
                .toList();

        return IncomeSourceResponses.Listing.builder().items(items).total(items.size()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeSource requireOwned(UUID userId, UUID sourceId)
    {
        return requireSource(userId, sourceId);
    }

    @Override
    public IncomeSource findOrCreateByName(UUID userId, String name)
    {
        String trimmed = name.trim();
        return sourceRepository.findFirstByUserIdAndNameIgnoreCase(userId, trimmed)
                .orElseGet(() -> {
                    IncomeSource source = new IncomeSource();
                    source.setUser(getUserOrThrow(userId));
                    source.setName(trimmed);
                    source.setColor(DEFAULT_COLOR);
                    return sourceRepository.save(source);
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Names, or dates for entries that were never named: "November salary, 2026-10-02". */
    private String describeIncomes(List<Income> incomes)
    {
        List<String> labels = new ArrayList<>();
        for (Income income : incomes)
        {
            String name = income.getName();
            labels.add(name == null || name.isBlank() ? income.getDate().toString()
                    : name + " (" + income.getDate() + ")");
        }
        return String.join(", ", labels);
    }

    private IncomeSourceResponses.Details toDetails(IncomeSource source)
    {
        return IncomeSourceResponses.Details.builder()
                .id(source.getId())
                .name(source.getName())
                .icon(source.getIcon())
                .color(source.getColor())
                .description(source.getDescription())
                .incomeCount(incomeRepository.countBySourceIdAndUserId(source.getId(), source.getUser().getId()))
                .build();
    }

    private IncomeSource requireSource(UUID userId, UUID sourceId)
    {
        return sourceRepository.findByIdAndUserId(sourceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Income source not found."));
    }

    private User getUserOrThrow(UUID userId)
    {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private static String blankToNull(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
