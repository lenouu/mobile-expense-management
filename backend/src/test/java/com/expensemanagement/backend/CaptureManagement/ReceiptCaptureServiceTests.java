package com.expensemanagement.backend.CaptureManagement;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureSource;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.CaptureManagement.Entities.CapturedTransaction;
import com.expensemanagement.backend.CaptureManagement.Mappers.CaptureMappers;
import com.expensemanagement.backend.CaptureManagement.Parsers.MobileMoneySmsParser;
import com.expensemanagement.backend.CaptureManagement.Repositories.CapturedTransactionRepository;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements.CaptureServiceImpl;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements.ReceiptCaptureService;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.ReceiptOcrProvider;
import com.expensemanagement.backend.Exceptions.GlobalExceptionHandler;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ReceiptReadingUnavailableException;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptCaptureServiceTests
{
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAPTURE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EXPENSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String RECEIPT_TEXT = "Marche Central\n12/05/2024\nTOTAL TTC 5 000 FCFA";
    private static final LocalDate EXTRACTED_DATE = LocalDate.of(2024, 5, 12);

    @Mock private ReceiptOcrProvider provider;
    @Mock private CapturedTransactionRepository captureRepository;
    @Mock private UserRepository userRepository;
    @Mock private User owner;
    // Deliberately independent: receipt capture must not depend on or call the expense service.
    @Mock private ExpenseService expenseService;
    @Mock private MobileMoneySmsParser smsParser;
    @Mock private ErrorLogService errorLogService;

    private ReceiptCaptureService receiptService;
    private CaptureServiceImpl reviewService;

    @BeforeEach
    void setUp()
    {
        receiptService = new ReceiptCaptureService(provider, captureRepository, userRepository);
        reviewService = new CaptureServiceImpl(captureRepository, userRepository, expenseService, smsParser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XAF", "EUR"})
    void captureSavesOnlyAPendingReceiptInTheOwnersBaseCurrency(String currency) throws IOException
    {
        byte[] image = smallPng();
        when(provider.extractText(same(image), eq("image/png"))).thenReturn(RECEIPT_TEXT);
        stubReceiptSave(currency);

        CaptureResponses.Receipt response = receiptService.capture(OWNER_ID, image, "image/png");

        CapturedTransaction saved = savedReceipt();
        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getSource()).isEqualTo(CaptureSource.RECEIPT);
        assertThat(saved.getStatus()).isEqualTo(CaptureStatus.PENDING);
        assertThat(saved.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(saved.getCurrency()).isEqualTo(currency);
        assertThat(saved.getTransactionDate()).isEqualTo(EXTRACTED_DATE);
        assertThat(saved.getMerchant()).isEqualTo("Marche Central");
        assertThat(saved.getConfidence()).isEqualTo(1.0);
        assertThat(saved.getProvider()).isNull();
        assertThat(saved.getType()).isNull();
        assertThat(saved.getExpenseId()).isNull();
        assertThat(saved.getReviewedAt()).isNull();
        assertThat(response.captureId()).isEqualTo(CAPTURE_ID);
        assertThat(response.extracted().amount()).isEqualTo(Money.of(new BigDecimal("5000.00"), currency));
        assertThat(response.extracted().date()).isEqualTo(EXTRACTED_DATE);
        assertThat(response.extracted().merchant()).isEqualTo("Marche Central");
        assertThat(response.confidence()).isEqualTo(1.0);
        verify(provider).extractText(same(image), eq("image/png"));
        verify(userRepository).findById(OWNER_ID);
        verifyNoMoreInteractions(captureRepository);
        verifyNoInteractions(expenseService);
    }

    @Test
    void providerFailureCreatesNoDraftAndMapsToBadGateway() throws IOException
    {
        byte[] image = smallPng();
        ReceiptReadingUnavailableException failure = new ReceiptReadingUnavailableException();
        when(provider.extractText(same(image), eq("image/png"))).thenThrow(failure);

        assertThatThrownBy(() -> receiptService.capture(OWNER_ID, image, "image/png"))
                .isSameAs(failure);
        assertThat(new GlobalExceptionHandler(errorLogService)
                .handleReceiptReadingUnavailable(failure).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(provider).extractText(same(image), eq("image/png"));
        verifyNoInteractions(captureRepository, userRepository, expenseService, errorLogService);
    }

    @Test
    void emptyOcrStillCreatesAPendingReceiptWithNullExtractedFieldsAndNullSafeDetails() throws IOException
    {
        byte[] image = smallPng();
        when(provider.extractText(same(image), eq("image/png"))).thenReturn("");
        stubReceiptSave("XAF");

        CaptureResponses.Receipt response = receiptService.capture(OWNER_ID, image, "image/png");

        CapturedTransaction saved = savedReceipt();
        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getSource()).isEqualTo(CaptureSource.RECEIPT);
        assertThat(saved.getStatus()).isEqualTo(CaptureStatus.PENDING);
        assertThat(saved.getCurrency()).isEqualTo("XAF");
        assertThat(saved.getAmount()).isNull();
        assertThat(saved.getMerchant()).isNull();
        assertThat(saved.getTransactionDate()).isNull();
        assertThat(saved.getConfidence()).isEqualTo(0.0);
        assertThat(saved.getExpenseId()).isNull();
        assertThat(response.captureId()).isEqualTo(CAPTURE_ID);
        assertThat(response.extracted()).isEqualTo(new CaptureResponses.Extracted(null, null, null));
        assertThat(response.confidence()).isZero();
        assertEmptyReceiptDetails(CaptureMappers.toDetails(saved));
        verifyNoMoreInteractions(captureRepository);
        verifyNoInteractions(expenseService);
    }

    @Test
    void oversizedPngIsResizedToBoundedJpegBeforeTheProviderIsCalled() throws IOException
    {
        byte[] image = noisyPng();
        assertThat(image.length).isGreaterThan(ReceiptCaptureService.OCR_MAX_BYTES)
                .isLessThanOrEqualTo(ReceiptCaptureService.MAX_UPLOAD_BYTES);
        stubReceiptSave("XAF");
        when(provider.extractText(any(byte[].class), eq("image/jpeg"))).thenAnswer(invocation -> {
            byte[] resized = invocation.getArgument(0);
            assertThat(resized).isNotSameAs(image);
            assertThat(resized.length).isPositive().isLessThanOrEqualTo(ReceiptCaptureService.OCR_MAX_BYTES);
            assertThat(resized[0]).isEqualTo((byte) 0xff);
            assertThat(resized[1]).isEqualTo((byte) 0xd8);
            assertThat(resized[2]).isEqualTo((byte) 0xff);
            try (MemoryCacheImageInputStream input =
                         new MemoryCacheImageInputStream(new ByteArrayInputStream(resized)))
            {
                ImageReader reader = ImageIO.getImageReaders(input).next();
                try
                {
                    reader.setInput(input);
                    BufferedImage decoded = reader.read(0);
                    assertThat(decoded).isNotNull();
                    assertThat(decoded.getWidth()).isPositive().isLessThanOrEqualTo(1600);
                    assertThat(decoded.getHeight()).isPositive().isLessThanOrEqualTo(1600);
                }
                finally
                {
                    reader.dispose();
                }
            }
            return "";
        });

        CaptureResponses.Receipt response = receiptService.capture(OWNER_ID, image, "image/png");

        assertThat(response.captureId()).isEqualTo(CAPTURE_ID);
        assertThat(savedReceipt().getStatus()).isEqualTo(CaptureStatus.PENDING);
        verify(provider).extractText(any(byte[].class), eq("image/jpeg"));
        verifyNoMoreInteractions(provider, captureRepository);
        verifyNoInteractions(expenseService);
    }

    @Test
    void webpJustOverTheOcrLimitIsRejectedWithoutProviderOrDraftWhenItCannotBeDecoded()
    {
        // A recognized WebP signature, but no JDK-decodable raster: resizing must fail locally.
        byte[] image = new byte[ReceiptCaptureService.OCR_MAX_BYTES + 1];
        byte[] signature = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        System.arraycopy(signature, 0, image, 0, signature.length);
        assertThat(image.length).isLessThanOrEqualTo(ReceiptCaptureService.MAX_UPLOAD_BYTES);

        OperationNotAllowedException failure = catchThrowableOfType(
                () -> receiptService.capture(OWNER_ID, image, "image/webp"), OperationNotAllowedException.class);

        assertThat(failure).isNotNull().hasMessageContaining("cannot be resized");
        assertThat(new GlobalExceptionHandler(errorLogService).handleNotAllowed(failure).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(provider, captureRepository, userRepository, expenseService, errorLogService);
    }

    @Test
    void confirmingAReceiptPassesTheExactCorrectedRequestAndLinksTheCreatedExpense()
    {
        CapturedTransaction capture = pendingReceipt();
        capture.setAmount(new BigDecimal("5000.00"));
        capture.setTransactionDate(EXTRACTED_DATE);
        capture.setMerchant("Marche Central");
        capture.setConfidence(1.0);
        Money correctedAmount = Money.of(new BigDecimal("4200.00"), "XAF");
        LocalDate correctedDate = LocalDate.of(2024, 5, 10);
        UUID categoryId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID budgetId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        ExpenseRequests.SaveExpense corrected = ExpenseRequests.SaveExpense.builder()
                .amount(correctedAmount)
                .date(correctedDate)
                .description("Corrected groceries")
                .categoryId(categoryId)
                .budgetId(budgetId)
                .build();
        when(captureRepository.findByIdAndUserId(CAPTURE_ID, OWNER_ID)).thenReturn(Optional.of(capture));
        when(expenseService.create(eq(OWNER_ID), same(corrected)))
                .thenReturn(ExpenseResponses.Details.builder().id(EXPENSE_ID).build());
        when(captureRepository.saveAndFlush(same(capture))).thenReturn(capture);

        CaptureResponses.Details response = reviewService.confirm(OWNER_ID, CAPTURE_ID, corrected);

        verify(expenseService).create(eq(OWNER_ID), same(corrected));
        verifyNoMoreInteractions(expenseService);
        assertThat(corrected.getAmount()).isEqualTo(correctedAmount);
        assertThat(corrected.getDate()).isEqualTo(correctedDate);
        assertThat(corrected.getDescription()).isEqualTo("Corrected groceries");
        assertThat(corrected.getCategoryId()).isEqualTo(categoryId);
        assertThat(corrected.getBudgetId()).isEqualTo(budgetId);
        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.CONFIRMED);
        assertThat(capture.getExpenseId()).isEqualTo(EXPENSE_ID);
        assertThat(capture.getReviewedAt()).isNotNull();
        assertThat(response.getId()).isEqualTo(CAPTURE_ID);
        assertThat(response.getStatus()).isEqualTo(CaptureStatus.CONFIRMED);
        assertThat(response.getExpenseId()).isEqualTo(EXPENSE_ID);
        assertThat(response.getReviewedAt()).isEqualTo(capture.getReviewedAt());
        // The draft retains the OCR values; they are not substituted into the reviewed expense.
        assertThat(capture.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(capture.getTransactionDate()).isEqualTo(EXTRACTED_DATE);
        assertThat(capture.getMerchant()).isEqualTo("Marche Central");
        verify(captureRepository).findByIdAndUserId(CAPTURE_ID, OWNER_ID);
        verify(captureRepository).saveAndFlush(same(capture));
        verifyNoMoreInteractions(captureRepository);
        verifyNoInteractions(provider, userRepository, smsParser);
    }

    @Test
    void emptyReceiptCanBeFoundInOwnerScopedSingleAndListQueriesAndDismissed()
    {
        CapturedTransaction capture = pendingReceipt();
        PageRequest pageable = PageRequest.of(0, 10);
        Page<CapturedTransaction> page = new PageImpl<>(List.of(capture), pageable, 1);
        when(captureRepository.findByIdAndUserId(CAPTURE_ID, OWNER_ID)).thenReturn(Optional.of(capture));
        when(captureRepository.findAllByUserId(OWNER_ID, pageable)).thenReturn(page);
        when(captureRepository.findAllByUserIdAndStatus(OWNER_ID, CaptureStatus.PENDING, pageable)).thenReturn(page);
        when(captureRepository.saveAndFlush(same(capture))).thenReturn(capture);

        CaptureResponses.Details single = reviewService.findMine(OWNER_ID, CAPTURE_ID);
        Page<CaptureResponses.Details> all = reviewService.findMine(OWNER_ID, null, pageable);
        Page<CaptureResponses.Details> pending = reviewService.findMine(OWNER_ID, CaptureStatus.PENDING, pageable);

        assertEmptyReceiptDetails(single);
        assertThat(single.getStatus()).isEqualTo(CaptureStatus.PENDING);
        assertThat(all.getTotalElements()).isEqualTo(1);
        assertThat(all.getContent()).singleElement().satisfies(ReceiptCaptureServiceTests::assertEmptyReceiptDetails);
        assertThat(pending.getContent()).singleElement().satisfies(ReceiptCaptureServiceTests::assertEmptyReceiptDetails);

        CaptureResponses.Details dismissed = reviewService.dismiss(OWNER_ID, CAPTURE_ID);

        assertEmptyReceiptDetails(dismissed);
        assertThat(dismissed.getStatus()).isEqualTo(CaptureStatus.DISMISSED);
        assertThat(capture.getStatus()).isEqualTo(CaptureStatus.DISMISSED);
        assertThat(dismissed.getReviewedAt()).isNotNull().isEqualTo(capture.getReviewedAt());
        verify(captureRepository, times(2)).findByIdAndUserId(CAPTURE_ID, OWNER_ID);
        verify(captureRepository).findAllByUserId(OWNER_ID, pageable);
        verify(captureRepository).findAllByUserIdAndStatus(OWNER_ID, CaptureStatus.PENDING, pageable);
        verify(captureRepository).saveAndFlush(same(capture));
        verifyNoMoreInteractions(captureRepository);
        verifyNoInteractions(expenseService, provider, userRepository, smsParser);
    }

    private void stubReceiptSave(String currency)
    {
        when(owner.getBaseCurrency()).thenReturn(currency);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(captureRepository.save(any(CapturedTransaction.class))).thenAnswer(invocation -> {
            CapturedTransaction capture = invocation.getArgument(0);
            capture.setId(CAPTURE_ID);
            return capture;
        });
    }

    private CapturedTransaction savedReceipt()
    {
        ArgumentCaptor<CapturedTransaction> captor = ArgumentCaptor.forClass(CapturedTransaction.class);
        verify(captureRepository).save(captor.capture());
        return captor.getValue();
    }

    private CapturedTransaction pendingReceipt()
    {
        CapturedTransaction capture = new CapturedTransaction();
        capture.setId(CAPTURE_ID);
        capture.setUser(owner);
        capture.setSource(CaptureSource.RECEIPT);
        capture.setStatus(CaptureStatus.PENDING);
        capture.setCurrency("XAF");
        capture.setConfidence(0.0);
        return capture;
    }

    private static void assertEmptyReceiptDetails(CaptureResponses.Details details)
    {
        assertThat(details.getId()).isEqualTo(CAPTURE_ID);
        assertThat(details.getSource()).isEqualTo(CaptureSource.RECEIPT);
        assertThat(details.getAmount()).isNull();
        assertThat(details.getFee()).isNull();
        assertThat(details.getTotal()).isNull();
        assertThat(details.getProvider()).isNull();
        assertThat(details.getType()).isNull();
        assertThat(details.getCounterparty()).isNull();
        assertThat(details.getReference()).isNull();
        assertThat(details.getMerchant()).isNull();
        assertThat(details.getTransactionDate()).isNull();
        assertThat(details.getTransactionTime()).isNull();
        assertThat(details.getConfidence()).isEqualTo(0.0);
        assertThat(details.getSuggestedDescription()).isNull();
        assertThat(details.getExpenseId()).isNull();
    }

    private static byte[] smallPng() throws IOException
    {
        return encodePng(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
    }

    private static byte[] noisyPng() throws IOException
    {
        // Seeded RGB noise yields an incompressible PNG below 5 MB with a side exceeding 1600.
        BufferedImage image = new BufferedImage(1800, 700, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(123456789L);
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                image.setRGB(x, y, random.nextInt(1 << 24));
            }
        }
        return encodePng(image);
    }

    private static byte[] encodePng(BufferedImage image) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Explicit memory cache avoids ImageIO's default temporary-file cache.
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes))
        {
            assertThat(ImageIO.write(image, "png", output)).isTrue();
        }
        return bytes.toByteArray();
    }
}
