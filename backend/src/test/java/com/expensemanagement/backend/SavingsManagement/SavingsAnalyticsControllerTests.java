package com.expensemanagement.backend.SavingsManagement;

import com.expensemanagement.backend.Authentication.Config.SecurityConfig;
import com.expensemanagement.backend.Exceptions.GlobalExceptionHandler;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import com.expensemanagement.backend.SavingsManagement.Controllers.SavingsAnalyticsController;
import com.expensemanagement.backend.SavingsManagement.Dtos.DtoResponses.SavingsResponses;
import com.expensemanagement.backend.SavingsManagement.Services.ServiceInterface.SavingsService;
import com.expensemanagement.backend.Shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer MockMvc tests for {@link SavingsAnalyticsController} (US48).
 *
 * <p>Lightweight slice test: no Docker, no Testcontainers, no database.
 */
@WebMvcTest(SavingsAnalyticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-only-jwt-secret-0123456789abcdef",
        "app.cors.allowed-origins=http://localhost:3000"
})
class SavingsAnalyticsControllerTests
{
    private static final String BASE_URL = "/api/v1/analytics/savings";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavingsService savingsService;

    @MockitoBean
    private ErrorLogService errorLogService;

    // ------------------------------------------------------------------
    // Authentication & Authorization
    // ------------------------------------------------------------------

    @Test
    void endpointRequiresToken_returns401() throws Exception
    {
        mockMvc.perform(get(BASE_URL).param("periodId", PERIOD_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminToken_returns403() throws Exception
    {
        mockMvc.perform(get(BASE_URL)
                        .param("periodId", PERIOD_ID.toString())
                        .with(adminToken()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Validation: missing or invalid periodId
    // ------------------------------------------------------------------

    @Test
    void missingPeriodId_returns400() throws Exception
    {
        mockMvc.perform(get(BASE_URL).with(userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_parameter"));
    }

    @Test
    void invalidPeriodId_returns400() throws Exception
    {
        mockMvc.perform(get(BASE_URL)
                        .param("periodId", "invalid-uuid")
                        .with(userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parameter"));
    }

    // ------------------------------------------------------------------
    // Response shape with explicit nulls
    // ------------------------------------------------------------------

    @Test
    void savingsCase_debtIsExplicitNull() throws Exception
    {
        SavingsResponses.PeriodSummary response = SavingsResponses.PeriodSummary.builder()
                .periodId(PERIOD_ID)
                .totalIncome(Money.of(new BigDecimal("100000.00"), "XAF"))
                .totalExpenses(Money.of(new BigDecimal("60000.00"), "XAF"))
                .carriedDebt(Money.of(new BigDecimal("0.00"), "XAF"))
                .effectiveIncome(Money.of(new BigDecimal("100000.00"), "XAF"))
                .savings(Money.of(new BigDecimal("40000.00"), "XAF"))
                .debt(null)
                .build();

        when(savingsService.summaryFor(USER_ID, PERIOD_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("periodId", PERIOD_ID.toString())
                        .with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(PERIOD_ID.toString()))
                .andExpect(jsonPath("$.totalIncome.amount").value(100000.00))
                .andExpect(jsonPath("$.totalIncome.currency").value("XAF"))
                .andExpect(jsonPath("$.totalExpenses.amount").value(60000.00))
                .andExpect(jsonPath("$.totalExpenses.currency").value("XAF"))
                .andExpect(jsonPath("$.carriedDebt.amount").value(0.00))
                .andExpect(jsonPath("$.carriedDebt.currency").value("XAF"))
                .andExpect(jsonPath("$.effectiveIncome.amount").value(100000.00))
                .andExpect(jsonPath("$.effectiveIncome.currency").value("XAF"))
                .andExpect(jsonPath("$.savings.amount").value(40000.00))
                .andExpect(jsonPath("$.savings.currency").value("XAF"))
                .andExpect(jsonPath("$.debt").value(nullValue()));
    }

    @Test
    void debtCase_savingsIsExplicitNull() throws Exception
    {
        SavingsResponses.PeriodSummary response = SavingsResponses.PeriodSummary.builder()
                .periodId(PERIOD_ID)
                .totalIncome(Money.of(new BigDecimal("50000.00"), "XAF"))
                .totalExpenses(Money.of(new BigDecimal("80000.00"), "XAF"))
                .carriedDebt(Money.of(new BigDecimal("0.00"), "XAF"))
                .effectiveIncome(Money.of(new BigDecimal("50000.00"), "XAF"))
                .savings(null)
                .debt(Money.of(new BigDecimal("30000.00"), "XAF"))
                .build();

        when(savingsService.summaryFor(USER_ID, PERIOD_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("periodId", PERIOD_ID.toString())
                        .with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(PERIOD_ID.toString()))
                .andExpect(jsonPath("$.savings").value(nullValue()))
                .andExpect(jsonPath("$.debt.amount").value(30000.00))
                .andExpect(jsonPath("$.debt.currency").value("XAF"));
    }

    @Test
    void carriedDebtCase_bothNullWhenNetIsZero() throws Exception
    {
        SavingsResponses.PeriodSummary response = SavingsResponses.PeriodSummary.builder()
                .periodId(PERIOD_ID)
                .totalIncome(new Money(new BigDecimal("50000.00"), "XAF"))
                .totalExpenses(new Money(new BigDecimal("20000.00"), "XAF"))
                .carriedDebt(new Money(new BigDecimal("30000.00"), "XAF"))
                .effectiveIncome(new Money(new BigDecimal("20000.00"), "XAF"))
                .savings(null)
                .debt(null)
                .build();

        when(savingsService.summaryFor(USER_ID, PERIOD_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("periodId", PERIOD_ID.toString())
                        .with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carriedDebt.amount").value(30000.00))
                .andExpect(jsonPath("$.effectiveIncome.amount").value(20000.00))
                .andExpect(jsonPath("$.savings").value(nullValue()))
                .andExpect(jsonPath("$.debt").value(nullValue()));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userToken()
    {
        return jwt()
                .jwt(builder -> builder.subject(USER_ID.toString()).claim("type", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminToken()
    {
        return jwt()
                .jwt(builder -> builder.subject(USER_ID.toString()).claim("type", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

