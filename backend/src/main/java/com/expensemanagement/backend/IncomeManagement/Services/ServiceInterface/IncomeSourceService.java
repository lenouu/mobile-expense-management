package com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface;

import java.util.UUID;

import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeSourceRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeSourceResponses;
import com.expensemanagement.backend.IncomeManagement.Entities.IncomeSource;
import com.expensemanagement.backend.UserManagement.Entities.User;

/** The user's own income sources. */
public interface IncomeSourceService
{
    IncomeSourceResponses.Details create(UUID userId, IncomeSourceRequests.SaveIncomeSource request);

    IncomeSourceResponses.Details update(UUID userId, UUID sourceId,
                                         IncomeSourceRequests.SaveIncomeSource request);

    /**
     * Deletes a source that no income refers to.
     *
     * @throws com.expensemanagement.backend.Exceptions.OperationNotAllowedException when incomes still use
     *         it; the message names them
     */
    void delete(UUID userId, UUID sourceId);

    IncomeSourceResponses.Listing listMine(UUID userId);

    /**
     * Gives a brand-new account the starter sources, in the same transaction as its creation.
     *
     * <p>Without this a new user could not record any income at all: the source is required, and
     * the list would be empty.
     */
    void provisionDefaults(User user);

    /**
     * The source, if this user owns it.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException when it does not exist or
     *         belongs to someone else
     */
    IncomeSource requireOwned(UUID userId, UUID sourceId);
}
