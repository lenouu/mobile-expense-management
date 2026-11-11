package com.expensemanagement.backend.IncomeManagement.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses;
import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import com.expensemanagement.backend.IncomeManagement.Entities.IncomeSource;
import com.expensemanagement.backend.IncomeManagement.Mappers.IncomeMappers;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface.IncomeSourceService;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface.IncomeService;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.Shared.DateRanges;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.RoundingMode;

/**
 * Incomes (S2-TECH-2). Same privacy model as {@code ExpenseServiceImpl}: every query is scoped
 * by the owner's id from the token.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class IncomeServiceImpl implements IncomeService
{
    private final IncomeRepository incomeRepository;
    private final UserRepository userRepository;
    private final FinancialPeriodRepository periodRepository;
    private final IncomeSourceService incomeSourceService;

    public IncomeServiceImpl(IncomeRepository incomeRepository, UserRepository userRepository,
                             FinancialPeriodRepository periodRepository,
                             IncomeSourceService incomeSourceService)
    {
        this.incomeRepository = incomeRepository;
        this.userRepository = userRepository;
        this.periodRepository = periodRepository;
        this.incomeSourceService = incomeSourceService;
    }

    @Override
    public Page<IncomeResponses.Details> findMine(UUID userId, LocalDate from, LocalDate to, Pageable pageable)
    {
        DateRanges.Range range = DateRanges.of(from, to);
        return incomeRepository.findAllByUserIdAndDateBetween(userId, range.from(), range.to(), pageable)
                .map(IncomeMappers::toDetails);
    }

    @Override
    public IncomeResponses.Details findMine(UUID userId, UUID id)
    {
        return IncomeMappers.toDetails(getIncomeOrThrow(userId, id));
    }

    @Override
    @Transactional
    public IncomeResponses.Details create(UUID userId, IncomeRequests.SaveIncome request)
    {
        Income income = new Income();
        // A reference, not a query: the id comes from a verified token, so the user exists.
        income.setUser(userRepository.getReferenceById(userId));
        IncomeSource source = incomeSourceService.requireOwned(userId, request.getSourceId());
        Income saved = incomeRepository.save(IncomeMappers.apply(income, request, source));

        log.info("User id={} added income id={}", userId, saved.getId());
        return IncomeMappers.toDetails(saved);
    }

    @Override
    @Transactional
    public IncomeResponses.Details update(UUID userId, UUID id, IncomeRequests.SaveIncome request)
    {
        Income income = getIncomeOrThrow(userId, id);
        IncomeSource source = incomeSourceService.requireOwned(userId, request.getSourceId());
        IncomeMappers.apply(income, request, source);

        // Flushed now so @PreUpdate has stamped updatedAt before the response is built.
        return IncomeMappers.toDetails(incomeRepository.saveAndFlush(income));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id)
    {
        incomeRepository.delete(getIncomeOrThrow(userId, id));
        log.info("User id={} deleted income id={}", userId, id);
    }

    /**
     * US13: the total is always calculated fresh from the database (no caching, no stale totals).
     * This guarantees that creates, edits and deletes are always reflected immediately.
     */
    @Override
    public IncomeResponses.TotalIncome totalForPeriod(UUID userId, UUID periodId)
    {
        FinancialPeriod period = periodRepository.findById(periodId)
                .filter(p -> p.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("No financial period with id " + periodId));

        BigDecimal total = incomeRepository.sumByUserIdAndDateBetween(userId,
                period.getStartDate(), period.getEndDate());

        long count = incomeRepository.countByUserIdAndDateBetween(userId,
                period.getStartDate(), period.getEndDate());

        return IncomeResponses.TotalIncome.builder()
                .total(Money.of(total))
                .periodId(period.getId())
                .periodStartDate(period.getStartDate())
                .periodEndDate(period.getEndDate())
                .recordCount(count)
                .build();
    }

    @Override
    public IncomeResponses.Summary summary(UUID userId, LocalDate from, LocalDate to)
    {
        String baseCurrency = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."))
                .getBaseCurrency();

        BigDecimal total = incomeRepository.sumByUserIdAndDateBetween(userId, from, to);
        long count = incomeRepository.countByUserIdAndDateBetween(userId, from, to);

        List<IncomeResponses.SourceSlice> slices = new ArrayList<>();
        for (Object[] row : incomeRepository.sumBySource(userId, from, to))
        {
            UUID sourceId = (UUID) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long entries = ((Number) row[2]).longValue();
            IncomeSource source = incomeSourceService.requireOwned(userId, sourceId);

            slices.add(IncomeResponses.SourceSlice.builder()
                    .source(toSourceRef(source))
                    .total(Money.of(amount, baseCurrency))
                    .count(entries)
                    .share(total.signum() == 0 ? 0
                            : amount.multiply(BigDecimal.valueOf(100))
                                    .divide(total, 1, RoundingMode.HALF_UP)
                                    .doubleValue())
                    .build());
        }

        slices.sort((a, b) -> b.getTotal().amount().compareTo(a.getTotal().amount()));

        return IncomeResponses.Summary.builder()
                .from(from)
                .to(to)
                .total(Money.of(total, baseCurrency))
                .count(count)
                .bySource(slices)
                .build();
    }

    private IncomeResponses.SourceRef toSourceRef(IncomeSource source)
    {
        return IncomeResponses.SourceRef.builder()
                .id(source.getId())
                .name(source.getName())
                .icon(source.getIcon())
                .color(source.getColor())
                .build();
    }
    private Income getIncomeOrThrow(UUID userId, UUID id)
    {
        return incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No income with id " + id));
    }
}

