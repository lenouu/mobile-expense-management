package com.expensemanagement.backend.PeriodManagement.Repositories;

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinancialPeriodSettingsRepository extends JpaRepository<FinancialPeriodSettings, UUID>
{
    Optional<FinancialPeriodSettings> findByUserId(UUID userId);
}
