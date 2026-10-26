package com.expensemanagement.backend.PeriodManagement.Repositories;

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialPeriodSettingsRepository extends JpaRepository<FinancialPeriodSettings, Long>
{
    Optional<FinancialPeriodSettings> findByUserId(Long userId);
}
