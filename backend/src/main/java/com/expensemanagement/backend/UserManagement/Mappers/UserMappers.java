package com.expensemanagement.backend.UserManagement.Mappers;

import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;

import java.util.List;


public final class UserMappers
{

    private UserMappers()
    {
        throw new AssertionError("UserMappers is a static utility class and cannot be instantiated");
    }

    // ------------------------------------------------------------------
    // Entity -> Response
    // ------------------------------------------------------------------

    public static UserResponses.Summary toSummary(User user)
    {
        return UserResponses.Summary.builder()
                .id(user.getId())
                .userName(user.getUserName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePictureReference(user.getProfilePictureReference())
                .status(user.getStatus())
                .type(user.getType())
                .build();
    }


    public static UserResponses.Details toDetails(User user)
    {
        return UserResponses.Details.builder()
                .id(user.getId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .profilePictureReference(user.getProfilePictureReference())
                .status(user.getStatus())
                .type(user.getType())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static List<UserResponses.Summary> toSummaryList(List<User> users)
    {
        if (users == null || users.isEmpty())
        {
            return List.of();
        }
        return users.stream().map(UserMappers::toSummary).toList();
    }

    public static List<UserResponses.Details> toDetailsList(List<User> users)
    {
        if (users == null || users.isEmpty())
        {
            return List.of();
        }
        return users.stream().map(UserMappers::toDetails).toList();
    }

    /**
     * Maps a repository page to a page of summaries.
     *

     */
    public static Page<UserResponses.Summary> toSummaryPage(Page<User> users)
    {
        if (users == null)
        {
            return Page.empty();
        }
        return users.map(UserMappers::toSummary);
    }

    public static Page<UserResponses.Details> toDetailsPage(Page<User> users)
    {
        if (users == null)
        {
            return Page.empty();
        }
        return users.map(UserMappers::toDetails);
    }

    // ------------------------------------------------------------------
    // Request -> new Entity
    // ------------------------------------------------------------------


    public static User toEntity(UserRequests.CreateAccount request)
    {
        User user = new User();
        user.setUserName(request.getUserName());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setDateOfBirth(request.getDateOfBirth());
        return user;
    }

    /** Bulk form of {@link #toEntity(UserRequests.CreateAccount)}. Same password caveat. */
    public static List<User> toEntities(List<UserRequests.CreateAccount> requests)
    {
        if (requests == null || requests.isEmpty())
        {
            return List.of();
        }
        return requests.stream().map(UserMappers::toEntity).toList();
    }

    // ------------------------------------------------------------------
    // Request -> existing Entity (in-place mutation)
    // ------------------------------------------------------------------

    public static User applyUpdate(User user, UserRequests.UpdateAccount request)
    {
        if (StringUtils.hasText(request.getUserName()))
        {
            user.setUserName(request.getUserName());
        }
        if (StringUtils.hasText(request.getFirstName()))
        {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName()))
        {
            user.setLastName(request.getLastName());
        }
        if (request.getDateOfBirth() != null)
        {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getProfilePictureReference() != null)
        {
            user.setProfilePictureReference(request.getProfilePictureReference());
        }
        return user;
    }

    /**
     * Applies an administrative lifecycle transition.
     */
    public static User applyStatus(User user, UserRequests.UpdateAccountStatus request)
    {
        if (request.getStatus() != null)
        {
            user.setStatus(request.getStatus());
        }
        return user;
    }

    /**
     * Soft delete: keeps the row and its history, only flips the status.
     */
    public static User applyDeletion(User user)
    {
        user.setStatus(UserAccountState.DELETED);
        return user;
    }
}
