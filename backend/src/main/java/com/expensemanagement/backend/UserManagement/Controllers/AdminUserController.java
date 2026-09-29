package com.expensemanagement.backend.UserManagement.Controllers;

import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative operations on other users. <strong>ADMIN only.</strong>
 *
 * <p>Two independent layers enforce that, on purpose:
 * <ol>
 *   <li>{@code SecurityConfig} requires {@code ROLE_ADMIN} for {@code /api/admin/**}, so an
 *       unauthenticated or USER request never reaches this class.</li>
 *   <li>The class-level {@code @PreAuthorize} below is a second gate that travels with the
 *       code. If someone later moves a route out of the {@code /api/admin} namespace - or
 *       adds a controller under it with a different base path - the URL rule stops applying
 *       but this annotation does not.</li>
 * </ol>
 *
 * <p>Relying on only one of the two is a common way to end up with an unprotected endpoint
 * after a refactor. Note also that {@code UserServiceImpl} carries its own
 * {@code @PreAuthorize} on the genuinely privileged operations, which is why the read
 * methods it shares with {@link UserController} are not annotated there.
 *
 * <p>401 means no valid token; 403 means a valid token without the ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User administration", description = "ADMIN only - manage other users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController
{
    private final UserService userService;

    public AdminUserController(UserService userService)
    {
        this.userService = userService;
    }

    /**
     * Paged user listing, optionally filtered by lifecycle state.
     *
     * <p>{@code page}, {@code size} and {@code sort} are resolved by Spring Data from the
     * query string; the defaults below apply when they are absent. {@code size} is capped by
     * {@code spring.data.web.pageable.max-page-size} in application.properties, so a client
     * cannot ask for the whole table in one request.
     */
    @GetMapping
    @Operation(summary = "List users (paged)",
            description = """
                    Omit `status` to list every account including soft-deleted ones.
                    Add `status=ACTIVE` for the normal "real users" listing.

                    Query params: `page` (0-based), `size` (max 100), `sort`
                    e.g. `?status=ACTIVE&page=0&size=20&sort=userName,asc`""")
    @ApiResponse(responseCode = "200", description = "Page of user summaries")
    @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public PagedModel<UserResponses.Summary> list(
            @Parameter(description = "Filter by lifecycle state; omit for all")
            @RequestParam(required = false) UserAccountState status,
            @ParameterObject @PageableDefault(size = 20, sort = "userName") Pageable pageable)
    {
        // The service exposes these as two methods rather than one nullable-status method,
        // so the choice is made here rather than pushing null handling into the repository.
        Page<UserResponses.Summary> page = status == null
                ? userService.findAll(pageable)
                : userService.findAllByStatus(status, pageable);

        // Wrapped explicitly so the JSON envelope is Spring Data's stable PagedModel rather
        // than a raw PageImpl, without the global annotation that would disable the ?size= cap.
        return new PagedModel<>(page);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by name (paged)",
            description = """
                    Case-insensitive contains-match against first name OR last name, so the
                    same term is applied to both fields.

                    Example: `?name=man&page=0&size=20`""")
    @ApiResponse(responseCode = "200", description = "Page of matching user summaries")
    public PagedModel<UserResponses.Summary> search(
            @Parameter(description = "Substring to match against first or last name")
            @RequestParam String name,
            @ParameterObject @PageableDefault(size = 20, sort = "userName") Pageable pageable)
    {
        return new PagedModel<>(userService.searchByName(name, pageable));
    }

    /**
     * Lookup by username.
     *
     * <p>Declared before nothing in particular matters here - {@code /search} and
     * {@code /by-username/{userName}} are literal-prefixed and cannot collide with the
     * {@code /{id}} mapping below.
     */
    @GetMapping("/by-username/{userName}")
    @Operation(summary = "Get one user by username")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "No such username",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public UserResponses.Details getByUserName(@PathVariable String userName)
    {
        return userService.findByUserName(userName);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one user by id")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "No such id",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public UserResponses.Details getById(@PathVariable Long id)
    {
        return userService.findById(id);
    }

    /**
     * Lifecycle transition: PENDING / ACTIVE / SUSPENDED / DELETED.
     *
     * <p>Used to suspend an abusive account or reactivate a suspended one. Registered accounts
     * are created ACTIVE, so this is not needed to let someone in. The request's
     * {@code reason} has no column on the entity and is written to the application log.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a user's account status",
            description = """
                    Moves an account between PENDING, ACTIVE, SUSPENDED and DELETED.

                    Registered accounts already start ACTIVE, so use this to suspend or
                    reinstate one. The optional `reason` is logged, not stored.""")
    @ApiResponse(responseCode = "200", description = "Status changed")
    @ApiResponse(responseCode = "404", description = "No such id",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public UserResponses.Details changeStatus(@PathVariable Long id,
                                              @Valid @RequestBody UserRequests.UpdateAccountStatus request)
    {
        return userService.updateStatus(id, request);
    }

    /**
     * Promotes or demotes between USER and ADMIN.
     *
     * <p>Refuses to demote the last remaining ADMIN - the service counts admins first -
     * because otherwise one mistake locks everyone out of administration with no way back
     * in through the API. To hand over, promote the successor first, then demote.
     */
    @PatchMapping("/{id}/type")
    @Operation(summary = "Change a user's role (USER / ADMIN)",
            description = """
                    Promotes or demotes an account.

                    The last remaining ADMIN cannot be demoted - promote a successor first,
                    otherwise nobody can administer the system any more.""")
    @ApiResponse(responseCode = "200", description = "Role changed")
    @ApiResponse(responseCode = "404", description = "No such id",
            content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(responseCode = "409", description = "Would remove the last ADMIN",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public UserResponses.Details changeType(@PathVariable Long id,
                                            @Valid @RequestBody UserRequests.UpdateAccountType request)
    {
        return userService.changeType(id, request.getType());
    }
}
