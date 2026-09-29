package com.expensemanagement.backend.Authentication.Services.ServiceImplements;

import com.expensemanagement.backend.Authentication.Dtos.DtoResponses.AuthResponses;
import com.expensemanagement.backend.Authentication.Jwt.JwtService;
import com.expensemanagement.backend.Authentication.Services.ServiceInterface.AuthenticationService;
import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticationServiceImpl implements AuthenticationService
{
    private final UserService userService;
    private final JwtService jwtService;

    public AuthenticationServiceImpl(UserService userService, JwtService jwtService)
    {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @Override
    public AuthResponses.Token login(UserRequests.Authenticate request)
    {
        // Throws InvalidCredentialsException / OperationNotAllowedException. All credential
        // and account-state rules live in there, so this class only turns a verified user
        // into a token.
        UserResponses.Details user = userService.authenticate(request);

        String accessToken = jwtService.generateToken(user.getId(), user.getUserName(), user.getType());

        return AuthResponses.Token.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiresInSeconds())
                .user(user)
                .build();
    }
}
