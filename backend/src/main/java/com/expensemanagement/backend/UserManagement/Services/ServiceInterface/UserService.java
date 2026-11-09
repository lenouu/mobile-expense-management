package com.expensemanagement.backend.UserManagement.Services.ServiceInterface;

import java.util.UUID;
import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService
{
    UserResponses.Details createAccount(UserRequests.CreateAccount request);

    UserResponses.Details authenticate(UserRequests.Authenticate request);

    UserResponses.Details findById(UUID id);

    UserResponses.Details findByUserName(String userName);

    Page<UserResponses.Summary> findAll(Pageable pageable);

    Page<UserResponses.Summary> findAllByStatus(UserAccountState status, Pageable pageable);

    Page<UserResponses.Summary> searchByName(String name, Pageable pageable);

    UserResponses.Details updateAccount(UUID id, UserRequests.UpdateAccount request);

    void changePassword(UUID id, UserRequests.ChangePassword request);

    // ------------------------------------------------------------------
    // Lifecycle and privilege (admin only)
    // ------------------------------------------------------------------

    UserResponses.Details updateStatus(UUID id, UserRequests.UpdateAccountStatus request);

    UserResponses.Details changeType(UUID id, UserType type);

    void deleteAccount(UUID id, UserRequests.DeleteAccount request);
}
