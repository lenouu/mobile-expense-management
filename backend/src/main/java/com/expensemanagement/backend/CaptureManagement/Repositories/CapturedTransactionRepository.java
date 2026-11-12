package com.expensemanagement.backend.CaptureManagement.Repositories;

import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.CaptureManagement.Entities.CapturedTransaction;
import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Every lookup is scoped by owner, so another user's capture is simply not found. */
public interface CapturedTransactionRepository extends JpaRepository<CapturedTransaction, UUID>
{
    Optional<CapturedTransaction> findByIdAndUserId(UUID id, UUID userId);

    Page<CapturedTransaction> findAllByUserId(UUID userId, Pageable pageable);

    Page<CapturedTransaction> findAllByUserIdAndStatus(UUID userId, CaptureStatus status, Pageable pageable);

    Optional<CapturedTransaction> findByUserIdAndProviderAndReference(UUID userId, MobileMoneyProvider provider,
                                                                      String reference);
}
