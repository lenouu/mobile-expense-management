package com.expensemanagement.backend.ExpenseManagement;

import com.expensemanagement.backend.Authentication.Config.SecurityConfig;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.ExpenseManagement.Controllers.ExpenseController;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.security.jwt.secret=aggregation-tests-only-secret-0123456789abcdef")
class ExpenseAggregationControllerTests
{
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired private MockMvc mvc;
    @MockitoBean private ExpenseService expenseService;
    @MockitoBean private FinancialPeriodService periodService;
    @MockitoBean private ErrorLogService errorLogService;

    @Test
    void spendingEndpointDelegatesWithTokenUuidAndReturnsMoney() throws Exception
    {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        ExpenseResponses.SpendingSummary summary = ExpenseResponses.SpendingSummary.builder()
                .from(from)
                .to(to)
                .totalSpent(Money.of(new BigDecimal("750.25"), "EUR"))
                .categories(List.of(ExpenseResponses.CategorySpending.builder()
                        .categoryId(CATEGORY_ID)
                        .categoryName("Food")
                        .categoryIcon("food-icon")
                        .categoryColor("#FF0000")
                        .totalSpent(Money.of(new BigDecimal("750.25"), "EUR"))
                        .build()))
                .build();
        when(expenseService.getSpendingSummary(USER_ID, from, to)).thenReturn(summary);

        mvc.perform(get("/api/expenses/spending").param("from", from.toString()).param("to", to.toString())
                        .param("userId", UUID.randomUUID().toString())
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(to.toString()))
                .andExpect(jsonPath("$.totalSpent.amount").value(750.25))
                .andExpect(jsonPath("$.totalSpent.currency").value("EUR"))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.categories[0].categoryIcon").value("food-icon"))
                .andExpect(jsonPath("$.categories[0].categoryColor").value("#FF0000"))
                .andExpect(jsonPath("$.categories[0].totalSpent.amount").value(750.25))
                .andExpect(jsonPath("$.categories[0].totalSpent.currency").value("EUR"));
        verify(expenseService).getSpendingSummary(USER_ID, from, to);
        verifyNoInteractions(periodService);
    }

    @Test
    void spendingEndpointWithOmittedDatesForwardsNulls() throws Exception
    {
        when(expenseService.getSpendingSummary(USER_ID, null, null))
                .thenReturn(ExpenseResponses.SpendingSummary.builder()
                        .totalSpent(Money.of(BigDecimal.ZERO, "XAF"))
                        .categories(List.of())
                        .build());

        mvc.perform(get("/api/expenses/spending")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(nullValue()))
                .andExpect(jsonPath("$.to").value(nullValue()))
                .andExpect(jsonPath("$.totalSpent.amount").value(0))
                .andExpect(jsonPath("$.totalSpent.currency").value("XAF"))
                .andExpect(jsonPath("$.categories.length()").value(0));
        verify(expenseService).getSpendingSummary(USER_ID, null, null);
        verifyNoInteractions(periodService);
    }

    @Test
    void invalidRangeReturnsCurrentErrorCode() throws Exception
    {
        LocalDate from = LocalDate.of(2026, 9, 30);
        LocalDate to = LocalDate.of(2026, 9, 1);
        when(expenseService.getSpendingSummary(USER_ID, from, to))
                .thenThrow(new InvalidRequestException("'from' must not be after 'to'"));

        mvc.perform(get("/api/expenses/spending").param("from", from.toString()).param("to", to.toString())
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void malformedDateReturnsBadRequestWithoutCallingService() throws Exception
    {
        mvc.perform(get("/api/expenses/spending").param("from", "not-a-date")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parameter"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void spendingEndpointRequiresAuthentication() throws Exception
    {
        mvc.perform(get("/api/expenses/spending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        verifyNoInteractions(expenseService);
    }
}
