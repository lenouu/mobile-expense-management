package com.expensemanagement.backend.PeriodManagement.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.PeriodManagement.Calendar.PeriodCalendar;
import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import com.expensemanagement.backend.PeriodManagement.Mappers.PeriodMappers;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodSettingsRepository;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Financial periods (S4-TECH-1).
 *
 * <p>Not {@code readOnly} at class level, unlike the other services: even reading the current
 * period may have to create it - and the default settings - the first time it is asked for.
 */
@Service
@Transactional
@Slf4j
public class FinancialPeriodServiceImpl implements FinancialPeriodService
{
    /** Default for a user who never chose a length: one calendar month. */
    private static final int DEFAULT_COUNT = 1;
    private static final PeriodUnit DEFAULT_UNIT = PeriodUnit.MONTHS;

    /** Longest period a user may choose, per unit: one year. */
    private static final Map<PeriodUnit, Integer> MAXIMUM_COUNT =
            Map.of(PeriodUnit.DAYS, 366, PeriodUnit.WEEKS, 52, PeriodUnit.MONTHS, 12);

    /** How far ahead periods may be created, so a typo in a date cannot create thousands. */
    private static final int MAX_YEARS_AHEAD = 1;

    private final FinancialPeriodRepository periodRepository;
    private final FinancialPeriodSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public FinancialPeriodServiceImpl(FinancialPeriodRepository periodRepository,
                                      FinancialPeriodSettingsRepository settingsRepository,
                                      UserRepository userRepository)
    {
        this.periodRepository = periodRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    @Override
    public FinancialPeriod current(Long userId)
    {
        return containing(userId, LocalDate.now())
                // Only possible if the account was created "in the future" by a clock problem.
                .orElseThrow(() -> new IllegalStateException("User id=" + userId + " has no period for today"));
    }

    @Override
    public Optional<FinancialPeriod> containing(Long userId, LocalDate date)
    {
        if (date.isAfter(LocalDate.now().plusYears(MAX_YEARS_AHEAD)))
        {
            throw new InvalidRequestException("Date " + date + " is more than a year in the future");
        }

        FinancialPeriod first = ensureFirstPeriod(userId);
        if (date.isBefore(first.getStartDate()))
        {
            return Optional.empty();
        }

        createPeriodsThrough(userId, date);
        return periodRepository.findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(userId, date, date);
    }

    @Override
    public FinancialPeriod findMine(Long userId, Long periodId)
    {
        return periodRepository.findByIdAndUserId(periodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No financial period with id " + periodId));
    }

    @Override
    public Optional<FinancialPeriod> previous(FinancialPeriod period)
    {
        if (period.getSequence() <= 1)
        {
            return Optional.empty();
        }
        return periodRepository.findByUserIdAndSequence(period.getUser().getId(), period.getSequence() - 1);
    }

    @Override
    public FinancialPeriod next(FinancialPeriod period)
    {
        Long userId = period.getUser().getId();
        createPeriodsThrough(userId, period.getEndDate().plusDays(1));
        return periodRepository.findByUserIdAndSequence(userId, period.getSequence() + 1)
                .orElseThrow(() -> new IllegalStateException("Next period of id=" + period.getId() + " was not created"));
    }

    @Override
    public LocalDate openingWindowEnd(FinancialPeriod period, double percent)
    {
        return PeriodCalendar.openingWindowEnd(period.getStartDate(), period.getEndDate(), percent);
    }

    @Override
    public boolean isInOpeningWindow(FinancialPeriod period, double percent, LocalDate date)
    {
        return period.contains(date) && !date.isAfter(openingWindowEnd(period, percent));
    }

    @Override
    public FinancialPeriodSettings settings(Long userId)
    {
        return settingsRepository.findByUserId(userId).orElseGet(() -> createDefaultSettings(userId));
    }

    @Override
    public FinancialPeriodSettings changeLength(Long userId, int count, PeriodUnit unit)
    {
        if (unit == null)
        {
            throw new InvalidRequestException("A period unit is required (DAYS, WEEKS or MONTHS)");
        }
        if (!PeriodCalendar.isAtLeastOneWeek(count, unit))
        {
            throw new InvalidRequestException("A financial period must last at least one week ("
                    + PeriodCalendar.MINIMUM_DAYS + " days)");
        }
        if (count > MAXIMUM_COUNT.get(unit))
        {
            throw new InvalidRequestException("A financial period can last at most one year ("
                    + MAXIMUM_COUNT.get(unit) + " " + unit.name().toLowerCase() + ")");
        }

        // Create every period up to today with the old length first, so the change cannot
        // rewrite periods that have already happened but were never asked for.
        FinancialPeriod current = current(userId);

        // Upcoming periods created ahead of time (e.g. by next()) still have the old length.
        // Nothing refers to a future period yet, so they are dropped and recreated on demand.
        periodRepository.deleteByUserIdAndSequenceGreaterThan(userId, current.getSequence());

        FinancialPeriodSettings settings = settings(userId);
        settings.setLengthCount(count);
        settings.setLengthUnit(unit);
        settings.setAnchorDate(current.getEndDate().plusDays(1));
        settings.setAnchorSequence(current.getSequence() + 1);
        settings.setDefaultLength(false);

        log.info("User id={} changed period length to {} {} from {}", userId, count, unit, settings.getAnchorDate());
        return settingsRepository.save(settings);
    }

    @Override
    public Page<PeriodResponses.Period> history(Long userId, Pageable pageable)
    {
        LocalDate today = LocalDate.now();
        FinancialPeriod current = current(userId);
        return periodRepository.findAllByUserIdAndSequenceLessThanEqual(userId, current.getSequence(), pageable)
                .map(period -> PeriodMappers.toPeriod(period, today));
    }

    // ------------------------------------------------------------------

    private FinancialPeriod ensureFirstPeriod(Long userId)
    {
        return periodRepository.findByUserIdAndSequence(userId, 1)
                .orElseGet(() -> createPeriod(settings(userId), 1));
    }

    /** Creates the user's periods one by one until one covers {@code date}. */
    private void createPeriodsThrough(Long userId, LocalDate date)
    {
        FinancialPeriod latest = periodRepository.findFirstByUserIdOrderBySequenceDesc(userId)
                .orElseGet(() -> ensureFirstPeriod(userId));
        if (!latest.getEndDate().isBefore(date))
        {
            return;
        }

        FinancialPeriodSettings settings = settings(userId);
        while (latest.getEndDate().isBefore(date))
        {
            latest = createPeriod(settings, latest.getSequence() + 1);
        }
    }

    private FinancialPeriod createPeriod(FinancialPeriodSettings settings, int sequence)
    {
        long index = sequence - settings.getAnchorSequence();
        if (index < 0)
        {
            throw new IllegalStateException("Period " + sequence + " is before the current run, which starts at "
                    + settings.getAnchorSequence());
        }

        FinancialPeriod period = new FinancialPeriod();
        period.setUser(settings.getUser());
        period.setSequence(sequence);
        period.setLengthCount(settings.getLengthCount());
        period.setLengthUnit(settings.getLengthUnit());
        period.setStartDate(PeriodCalendar.startOf(settings.getAnchorDate(), settings.getLengthCount(),
                settings.getLengthUnit(), index));
        period.setEndDate(PeriodCalendar.endOf(settings.getAnchorDate(), settings.getLengthCount(),
                settings.getLengthUnit(), index));
        return periodRepository.save(period);
    }

    private FinancialPeriodSettings createDefaultSettings(Long userId)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No user with id " + userId));

        FinancialPeriodSettings settings = new FinancialPeriodSettings();
        settings.setUser(user);
        settings.setLengthCount(DEFAULT_COUNT);
        settings.setLengthUnit(DEFAULT_UNIT);
        // Counted from sign-up, so the first period starts the day the account was created.
        settings.setAnchorDate(user.getCreatedAt().toLocalDate());
        settings.setAnchorSequence(1);
        settings.setDefaultLength(true);
        return settingsRepository.save(settings);
    }
}
