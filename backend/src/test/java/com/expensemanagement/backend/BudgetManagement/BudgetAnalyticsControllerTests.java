package com.expensemanagement.backend.BudgetManagement;

import com.expensemanagement.backend.Authentication.Config.SecurityConfig;
import com.expensemanagement.backend.BudgetManagement.Controllers.BudgetAnalyticsController;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetAnalyticsResponses;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetAnalyticsService;
import com.expensemanagement.backend.Exceptions.GlobalExceptionHandler;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer MockMvc tests for {@link BudgetAnalyticsController} (US41, US42).
 *
 * <p>Lightweight slice test: no Docker, no Testcontainers, no database.
 */
@WebMvcTest(BudgetAnalyticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-only-jwt-secret-0123456789abcdef",
        "app.cors.allowed-origins=http://localhost:3000"
})
class BudgetAnalyticsControllerTests
{
    private static final String BASE_URL = "/api/v1/analytics/budget";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetAnalyticsService analyticsService;

    @MockitoBean
    private ErrorLogService errorLogService;

    // ------------------------------------------------------------------
    // Authentication & Authorization
    // ------------------------------------------------------------------

    @Test
    void endpointsRequireToken_returns401() throws Exception
    {
        mockMvc.perform(get(BASE_URL + "/overall-balance").param("periodId", PERIOD_ID.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(BASE_URL + "/category-balance").param("periodId", PERIOD_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminToken_returns403() throws Exception
    {
        mockMvc.perform(get(BASE_URL + "/overall-balance")
                        .param("periodId", PERIOD_ID.toString())
                        .with(adminToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE_URL + "/category-balance")
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
        mockMvc.perform(get(BASE_URL + "/overall-balance")
                        .with(userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_parameter"));

        mockMvc.perform(get(BASE_URL + "/category-balance")
                        .with(userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_parameter"));
    }

    @Test
    void invalidPeriodId_returns400() throws Exception
    {
        mockMvc.perform(get(BASE_URL + "/overall-balance")
                        .param("periodId", "not-a-valid-uuid")
                        .with(userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parameter"));
    }

    // ------------------------------------------------------------------
    // US41: overall balance response shape
    // ------------------------------------------------------------------

    @Test
    void overallBalance_successResponseShape() throws Exception
    {
        BudgetAnalyticsResponses.OverallBalance response = BudgetAnalyticsResponses.OverallBalance.builder()
                .periodId(PERIOD_ID)
                .budgetAmount(Money.of(new BigDecimal("100000.00"), "XAF"))
                .actualSpent(Money.of(new BigDecimal("75000.00"), "XAF"))
                .balance(Money.of(new BigDecimal("25000.00"), "XAF"))
                .build();

        when(analyticsService.overallBalance(USER_ID, PERIOD_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/overall-balance")
                        .param("periodId", PERIOD_ID.toString())
                        .with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(PERIOD_ID.toString()))
                .andExpect(jsonPath("$.budgetAmount.amount").value(100000.00))
                .andExpect(jsonPath("$.budgetAmount.currency").value("XAF"))
                .andExpect(jsonPath("$.actualSpent.amount").value(75000.00))
                .andExpect(jsonPath("$.actualSpent.currency").value("XAF"))
                .andExpect(jsonPath("$.balance.amount").value(25000.00))
                .andExpect(jsonPath("$.balance.currency").value("XAF"));
    }

    // ------------------------------------------------------------------
    // US42: category balance response shape
    // ------------------------------------------------------------------

    @Test
    void categoryBalance_successResponseShape() throws Exception
    {
        BudgetAnalyticsResponses.CategoryBalance item = BudgetAnalyticsResponses.CategoryBalance.builder()
                .categoryId(CATEGORY_ID)
                .allocated(Money.of(new BigDecimal("30000.00"), "XAF"))
                .spent(Money.of(new BigDecimal("35000.00"), "XAF"))
                .balance(Money.of(new BigDecimal("-5000.00"), "XAF"))
                .overspent(true)
                .build();

        when(analyticsService.categoryBalance(USER_ID, PERIOD_ID)).thenReturn(List.of(item));

        mockMvc.perform(get(BASE_URL + "/category-balance")
                        .param("periodId", PERIOD_ID.toString())
                        .with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoryId").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$[0].allocated.amount").value(30000.00))
                .andExpect(jsonPath("$[0].allocated.currency").value("XAF"))
                .andExpect(jsonPath("$[0].spent.amount").value(35000.00))
                .andExpect(jsonPath("$[0].spent.currency").value("XAF"))
                .andExpect(jsonPath("$[0].balance.amount").value(-5000.00))
                .andExpect(jsonPath("$[0].balance.currency").value("XAF"))
                .andExpect(jsonPath("$[0].overspent").value(true));
    }

    @Test
    void categoryBalance_emptyAllocations_returnsEmptyArray() throws Exception
    {
        when(analyticsService.categoryBalance(USER_ID, PERIOD_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE_URL + "/category-balance")
                        .param("periodId", PERIOD_ID.toString())
                        .with(userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
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
