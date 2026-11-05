package com.expensemanagement.backend.IncomeManagement.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses;
import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import com.expensemanagement.backend.IncomeManagement.Mappers.IncomeMappers;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface.IncomeService;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.Shared.DateRanges;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    public IncomeServiceImpl(IncomeRepository incomeRepository, UserRepository userRepository,
                             FinancialPeriodRepository periodRepository)
    {
        this.incomeRepository = incomeRepository;
        this.userRepository = userRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    public Page<IncomeResponses.Details> findMine(Long userId, LocalDate from, LocalDate to, Pageable pageable)
    {
        DateRanges.Range range = DateRanges.of(from, to);
        return incomeRepository.findAllByUserIdAndDateBetween(userId, range.from(), range.to(), pageable)
                .map(IncomeMappers::toDetails);
    }

    @Override
    public IncomeResponses.Details findMine(Long userId, Long id)
    {
        return IncomeMappers.toDetails(getIncomeOrThrow(userId, id));
    }

    @Override
    @Transactional
    public IncomeResponses.Details create(Long userId, IncomeRequests.SaveIncome request)
    {
        Income income = new Income();
        // A reference, not a query: the id comes from a verified token, so the user exists.
        income.setUser(userRepository.getReferenceById(userId));
        Income saved = incomeRepository.save(IncomeMappers.apply(income, request));

        log.info("User id={} added income id={}", userId, saved.getId());
        return IncomeMappers.toDetails(saved);
    }

    @Override
    @Transactional
    public IncomeResponses.Details update(Long userId, Long id, IncomeRequests.SaveIncome request)
    {
        Income income = getIncomeOrThrow(userId, id);
        IncomeMappers.apply(income, request);

        // Flushed now so @PreUpdate has stamped updatedAt before the response is built.
        return IncomeMappers.toDetails(incomeRepository.saveAndFlush(income));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id)
    {
        incomeRepository.delete(getIncomeOrThrow(userId, id));
        log.info("User id={} deleted income id={}", userId, id);
    }

    /**
     * US13: the total is always calculated fresh from the database (no caching, no stale totals).
     * This guarantees that creates, edits and deletes are always reflected immediately.
     */
    @Override
    public IncomeResponses.TotalIncome totalForPeriod(Long userId, Long periodId)
    {
        FinancialPeriod period = periodRepository.findById(periodId)
                .filter(p -> p.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("No financial period with id " + periodId));

        BigDecimal total = incomeRepository.sumByUserIdAndDateBetween(userId,
                period.getStartDate(), period.getEndDate());

        long count = incomeRepository.countByUserIdAndDateBetween(userId,
                period.getStartDate(), period.getEndDate());

        return IncomeResponses.TotalIncome.builder()
                .total(total)
                .periodId(period.getId())
                .periodStartDate(period.getStartDate())
                .periodEndDate(period.getEndDate())
                .recordCount(count)
                .build();
    }

    private Income getIncomeOrThrow(Long userId, Long id)
    {
        return incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No income with id " + id));
    }
}

