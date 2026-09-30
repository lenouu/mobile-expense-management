package com.expensemanagement.backend.Authentication.Controllers;

import com.expensemanagement.backend.Authentication.Dtos.DtoResponses.AuthResponses;
import com.expensemanagement.backend.Authentication.Services.ServiceInterface.AuthenticationService;
import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>Both routes are listed as {@code permitAll} in {@link com.expensemanagement.backend.Authentication.Config.SecurityConfig} -
 * they have to be reachable without a token, since they are how a client obtains one.
 *
 * <p>{@code @SecurityRequirements} (with no value) removes the global Bearer requirement that
 * {@code OpenApiConfig} applies to everything else, so Swagger UI shows no padlock here.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register an account and exchange credentials for a JWT")
@SecurityRequirements
public class AuthController
{
    private final UserService userService;
    private final AuthenticationService authenticationService;

    public AuthController(UserService userService, AuthenticationService authenticationService)
    {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    /**
     * Registers an account.
     *
     * <p>The account is created {@code ACTIVE} and can log in immediately. The status is set
     * server-side and never taken from the request, so a client cannot choose its own lifecycle
     * state. When you add email verification, this is where the mail is sent from - and the
     * point at which you would switch registration back to {@code PENDING}.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new account",
            description = """
                    Creates the account ACTIVE with the USER role. Both are set by the server,
                    so a request cannot choose its own status or privilege.

                    The response never contains the password or its hash.""")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "409", description = "Username or email already taken", content = @Content)
    public UserResponses.Details register(@Valid @RequestBody UserRequests.CreateAccount request)
    {
        return userService.createAccount(request);
    }

    /**
     * Exchanges credentials for a JWT.
     *
     * <p>401 for wrong credentials, 409 if the account is not ACTIVE.
     */
    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT",
            description = """
                    Accepts either the username or the email in the same field.

                    Wrong credentials and unknown accounts both return the same 401 message, so
                    the endpoint cannot be used to discover which usernames exist.""")
    @ApiResponse(responseCode = "200", description = "Authenticated",
            content = @Content(schema = @Schema(implementation = AuthResponses.Token.class)))
    @ApiResponse(responseCode = "401", description = "Invalid username/email or password", content = @Content)
    @ApiResponse(responseCode = "409", description = "Account is not ACTIVE", content = @Content)
    public AuthResponses.Token login(@Valid @RequestBody UserRequests.Authenticate request)
    {
        return authenticationService.login(request);
    }
}
