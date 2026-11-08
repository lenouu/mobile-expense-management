package com.expensemanagement.backend.IncomeManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses.TotalIncome;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceImplements.IncomeServiceImpl;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTotalUnitTest {

	@Mock
	private IncomeRepository incomeRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FinancialPeriodRepository periodRepository;

	private IncomeServiceImpl incomeService;

	@BeforeEach
	void setUp() {
		incomeService = new IncomeServiceImpl(incomeRepository, userRepository, periodRepository);
	}

	@Test
	void totalForPeriod_calculatesSumAndCount_forValidPeriodAndUser() {
		Long userId = 1L;
		Long periodId = 10L;
		LocalDate start = LocalDate.of(2026, 9, 1);
		LocalDate end = LocalDate.of(2026, 9, 30);

		User user = new User();
		user.setId(userId);

		FinancialPeriod period = new FinancialPeriod();
		period.setId(periodId);
		period.setUser(user);
		period.setStartDate(start);
		period.setEndDate(end);

		when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
		when(incomeRepository.sumByUserIdAndDateBetween(userId, start, end)).thenReturn(new BigDecimal("1500.50"));
		when(incomeRepository.countByUserIdAndDateBetween(userId, start, end)).thenReturn(3L);

		TotalIncome result = incomeService.totalForPeriod(userId, periodId);

		assertThat(result).isNotNull();
		assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("1500.50"));
		assertThat(result.getPeriodId()).isEqualTo(periodId);
		assertThat(result.getPeriodStartDate()).isEqualTo(start);
		assertThat(result.getPeriodEndDate()).isEqualTo(end);
		assertThat(result.getRecordCount()).isEqualTo(3L);

		verify(periodRepository).findById(periodId);
		verify(incomeRepository).sumByUserIdAndDateBetween(userId, start, end);
		verify(incomeRepository).countByUserIdAndDateBetween(userId, start, end);
	}

	@Test
	void totalForPeriod_whenNoRecords_returnsZeroAmountAndZeroCount() {
		Long userId = 1L;
		Long periodId = 10L;
		LocalDate start = LocalDate.of(2026, 9, 1);
		LocalDate end = LocalDate.of(2026, 9, 30);

		User user = new User();
		user.setId(userId);

		FinancialPeriod period = new FinancialPeriod();
		period.setId(periodId);
		period.setUser(user);
		period.setStartDate(start);
		period.setEndDate(end);

		when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
		when(incomeRepository.sumByUserIdAndDateBetween(userId, start, end)).thenReturn(BigDecimal.ZERO);
		when(incomeRepository.countByUserIdAndDateBetween(userId, start, end)).thenReturn(0L);

		TotalIncome result = incomeService.totalForPeriod(userId, periodId);

		assertThat(result).isNotNull();
		assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(result.getRecordCount()).isEqualTo(0L);
	}

	@Test
	void totalForPeriod_periodNotFound_throwsResourceNotFoundException() {
		Long userId = 1L;
		Long nonExistentPeriodId = 999L;

		when(periodRepository.findById(nonExistentPeriodId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> incomeService.totalForPeriod(userId, nonExistentPeriodId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No financial period with id " + nonExistentPeriodId);
	}

	@Test
	void totalForPeriod_periodBelongsToDifferentUser_throwsResourceNotFoundException() {
		Long currentUserId = 1L;
		Long otherUserId = 2L;
		Long periodId = 10L;

		User otherUser = new User();
		otherUser.setId(otherUserId);

		FinancialPeriod period = new FinancialPeriod();
		period.setId(periodId);
		period.setUser(otherUser);
		period.setStartDate(LocalDate.of(2026, 9, 1));
		period.setEndDate(LocalDate.of(2026, 9, 30));

		when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));

		assertThatThrownBy(() -> incomeService.totalForPeriod(currentUserId, periodId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No financial period with id " + periodId);
	}
}
