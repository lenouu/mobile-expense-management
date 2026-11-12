package com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests.CaptureRequests;
import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureSource;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.CaptureManagement.Entities.CapturedTransaction;
import com.expensemanagement.backend.CaptureManagement.Mappers.CaptureMappers;
import com.expensemanagement.backend.CaptureManagement.Parsers.MobileMoneySmsParser;
import com.expensemanagement.backend.CaptureManagement.Repositories.CapturedTransactionRepository;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.CaptureService;
import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Captured mobile money transactions (S6-TECH-2).
 *
 * <p>Every query is scoped by the owner's id from the token, exactly like expenses.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class CaptureServiceImpl implements CaptureService
{
    private final CapturedTransactionRepository captureRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final MobileMoneySmsParser parser;

    public CaptureServiceImpl(CapturedTransactionRepository captureRepository, UserRepository userRepository,
                              ExpenseService expenseService, MobileMoneySmsParser parser)
    {
        this.captureRepository = captureRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.parser = parser;
    }

    @Override
    @Transactional
    public CaptureResponses.Details captureSms(UUID userId, CaptureRequests.SmsCapture request)
    {
        MobileMoneySmsParser.ParsedSms sms = parser.parse(request.getText(), request.getProvider());

        if (sms.reference() != null)
        {
            Optional<CapturedTransaction> existing =
                    captureRepository.findByUserIdAndProviderAndReference(userId, sms.provider(), sms.reference());
            if (existing.isPresent())
            {
                throw new DuplicateResourceException("This transaction (" + sms.reference()
                        + ") was already captured on " + existing.get().getCreatedAt().toLocalDate()
                        + " and is " + statusWord(existing.get().getStatus()) + ".");
            }
        }

        CapturedTransaction capture = new CapturedTransaction();
        // A reference, not a query: the id comes from a verified token, so the user exists.
        capture.setUser(userRepository.getReferenceById(userId));
        capture.setSource(CaptureSource.MOBILE_MONEY_SMS);
        capture.setProvider(sms.provider());
        capture.setType(sms.type());
        capture.setAmount(sms.amount().normalisedAmount());
        capture.setCurrency(sms.amount().currencyOrDefault());
        capture.setFee(sms.fee() == null ? null : sms.fee().normalisedAmount());
        capture.setCounterparty(sms.counterparty());
        capture.setReference(sms.reference());
        capture.setTransactionDate(sms.date());
        capture.setTransactionTime(sms.time());
        capture.setStatus(CaptureStatus.PENDING);

        CapturedTransaction saved = captureRepository.save(capture);
        // Never log the SMS itself: it holds names, numbers and a balance.
        log.info("User id={} captured {} {} id={}", userId, sms.provider(), sms.type(), saved.getId());
        return CaptureMappers.toDetails(saved);
    }

    @Override
    public Page<CaptureResponses.Details> findMine(UUID userId, CaptureStatus status, Pageable pageable)
    {
        Page<CapturedTransaction> page = status == null
                ? captureRepository.findAllByUserId(userId, pageable)
                : captureRepository.findAllByUserIdAndStatus(userId, status, pageable);
        return page.map(CaptureMappers::toDetails);
    }

    @Override
    public CaptureResponses.Details findMine(UUID userId, UUID id)
    {
        return CaptureMappers.toDetails(getOrThrow(userId, id));
    }

    @Override
    @Transactional
    public CaptureResponses.Details confirm(UUID userId, UUID id, ExpenseRequests.SaveExpense expense)
    {
        CapturedTransaction capture = getPendingOrThrow(userId, id);

        // One transaction: if marking the capture fails, the expense is rolled back with it.
        ExpenseResponses.Details created = expenseService.create(userId, expense);

        capture.setStatus(CaptureStatus.CONFIRMED);
        capture.setExpenseId(created.getId());
        capture.setReviewedAt(now());

        log.info("User id={} confirmed capture id={} as expense id={}", userId, id, created.getId());
        return CaptureMappers.toDetails(captureRepository.saveAndFlush(capture));
    }

    @Override
    @Transactional
    public CaptureResponses.Details dismiss(UUID userId, UUID id)
    {
        CapturedTransaction capture = getPendingOrThrow(userId, id);
        capture.setStatus(CaptureStatus.DISMISSED);
        capture.setReviewedAt(now());

        log.info("User id={} dismissed capture id={}", userId, id);
        return CaptureMappers.toDetails(captureRepository.saveAndFlush(capture));
    }

    private CapturedTransaction getOrThrow(UUID userId, UUID id)
    {
        return captureRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Captured transaction not found"));
    }

    private CapturedTransaction getPendingOrThrow(UUID userId, UUID id)
    {
        CapturedTransaction capture = getOrThrow(userId, id);
        if (capture.getStatus() != CaptureStatus.PENDING)
        {
            throw new OperationNotAllowedException("This transaction was already " + statusWord(capture.getStatus()) + ".");
        }
        return capture;
    }

    private static String statusWord(CaptureStatus status)
    {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static LocalDateTime now()
    {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
