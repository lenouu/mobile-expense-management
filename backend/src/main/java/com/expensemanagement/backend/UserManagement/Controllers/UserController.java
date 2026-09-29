package com.expensemanagement.backend.UserManagement.Controllers;

import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account endpoints for the signed-in user.
 *
 * <p><strong>Every route here operates on the caller, and the caller's id comes from the JWT -
 * never from the URL.</strong> That is deliberate and it is the single most important thing
 * about this class. A tempting design is {@code PUT /api/users/{id}}, but that is an
 * insecure-direct-object-reference hole: nothing stops a signed-in user from sending someone
 * else's id and editing their profile, changing their password, or deleting their account.
 * There is no authorisation check that can fix that cleanly, because the request is
 * indistinguishable from a legitimate one.
 *
 * <p>By deriving the id from the token's {@code sub} claim instead, the only account a caller
 * can touch through these routes is their own. Admin operations on <em>other</em> users live
 * in {@link AdminUserController} under {@code /api/admin/**}, where a role check applies.
 *
 * <p>All four routes require a valid token but no particular role, so {@code USER} and
 * {@code ADMIN} both reach them.
 */
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "My account", description = "Self-service endpoints for the signed-in user")
public class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get my profile",
            description = "Returns the full profile of the account the token belongs to.")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @io.swagger.v3.oas.annotations.media.Content)
    public UserResponses.Details me(@AuthenticationPrincipal Jwt jwt)
    {
        return userService.findById(currentUserId(jwt));
    }

    /**
     * Partial update: only the fields present in the body are applied, so a client can send
     * a single field without wiping the rest.
     *
     * <p>{@code userName} is updatable here, which means the service re-checks uniqueness
     * before saving. {@code status} and {@code type} are absent from
     * {@code UpdateAccount} on purpose - self-promotion to ADMIN is not a profile edit.
     */
    @PatchMapping
    @Operation(summary = "Update my profile",
            description = """
                    PATCH semantics: omit a field to leave it unchanged.

                    userName can be changed but is uniqueness-checked. status, type and
                    password are absent by design - see the password and admin endpoints.""")
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @ApiResponse(responseCode = "409", description = "Requested username already taken",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public UserResponses.Details updateMe(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UserRequests.UpdateAccount request)
    {
        return userService.updateAccount(currentUserId(jwt), request);
    }

    /**
     * {@code PUT} rather than {@code PATCH} because this replaces the credential wholesale,
     * and {@code /password} rather than a body field because the two new-password fields
     * plus the old one make the intent clearer at the route level.
     *
     * <p>Returns 204: there is nothing meaningful to send back, and returning the profile
     * would tempt clients into treating a password change as profile data.
     */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password",
            description = """
                    Requires the current password, so a stolen token alone cannot lock the
                    real owner out. Fails with 401 if the current password is wrong and 409
                    if newPassword and confirmNewPassword do not match.""")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "401", description = "Current password is incorrect",
            content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(responseCode = "409", description = "New passwords do not match",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public void changeMyPassword(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody UserRequests.ChangePassword request)
    {
        userService.changePassword(currentUserId(jwt), request);
    }

    /**
     * Soft delete: the row stays and {@code status} becomes {@code DELETED}.
     *
     * <p>Carries a body (the password) even though {@code DELETE} bodies are unusual. The
     * re-authentication guard is worth the slight awkwardness - dropping it would let anyone
     * holding an unlocked device destroy the account. If your mobile HTTP client refuses to
     * send a DELETE body, move this to {@code POST /api/users/me/deletion} rather than
     * removing the password.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my account (soft delete)",
            description = """
                    Sets the account status to DELETED, keeping the row and its history.
                    Requires the password as a re-authentication guard.

                    A deleted account can no longer sign in, and its unique username and
                    email remain reserved.""")
    @ApiResponse(responseCode = "204", description = "Account soft-deleted")
    @ApiResponse(responseCode = "401", description = "Password is incorrect",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public void deleteMe(@AuthenticationPrincipal Jwt jwt,
                         @Valid @RequestBody UserRequests.DeleteAccount request)
    {
        userService.deleteAccount(currentUserId(jwt), request);
    }

    /**
     * Reads the caller's id out of the token.
     *
     * <p>{@code sub} is written by {@code JwtService.generateToken} as the user id. It is a
     * {@code String} because the JWT spec requires a string subject, which is why this
     * parses rather than casts.
     */
    private Long currentUserId(Jwt jwt)
    {
        return Long.valueOf(jwt.getSubject());
    }
}
