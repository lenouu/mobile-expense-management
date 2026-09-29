package com.expensemanagement.backend.Swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Everything Swagger/OpenAPI that is <em>configuration</em> lives in this package.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>The OpenAPI document metadata and the JWT "Authorize" button.</li>
 *   <li>Owning the set of public documentation URLs, so {@code SecurityConfig} does not have
 *       to hardcode them and there is a single place to disable docs for production.</li>
 * </ol>
 *
 * <p>The {@code securityScheme} plus a global {@code SecurityRequirement} is what makes the
 * padlock appear on every operation and lets Swagger UI attach
 * {@code Authorization: Bearer <token>} to the requests it sends. Without it, "Try it out"
 * would call protected endpoints unauthenticated and 401 on everything.
 *
 * <p><strong>What deliberately stays outside this package:</strong> the
 * {@code @Operation} / {@code @Tag} / {@code @ApiResponse} annotations on the controllers.
 * Those are per-endpoint metadata and belong next to the endpoint they describe - moving
 * them here would mean renaming a controller route silently leaves the docs describing the
 * old one. Same reasoning as not moving {@code @NotBlank} off a DTO into a "Validation"
 * package.
 */
@Configuration
public class SwaggerConfig
{
    /** Name used to reference this scheme from {@code @SecurityRequirement} elsewhere. */
    public static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Documentation routes that must be reachable without a token, otherwise you could not
     * open the UI to obtain one.
     *
     * <p>Consumed by {@code SecurityConfig}. Note these are permitted anonymously, which
     * means anyone who can reach the app can read the whole API surface. Before deploying,
     * set {@code springdoc.api-docs.enabled=false} and {@code springdoc.swagger-ui.enabled=false}
     * and empty this array.
     */
    public static final String[] PUBLIC_DOC_PATHS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    public OpenAPI expenseManagementOpenAPI()
    {
        return new OpenAPI()
                .info(new Info()
                        .title("Expense Management API")
                        .version("v1")
                        .description("""
                                REST API for the mobile expense management app.

                                **How to authenticate**
                                1. `POST /api/auth/login` with a username *or* email and copy the
                                   `accessToken` from the response.
                                   Use a seeded login below - in particular an ADMIN, since a
                                   freshly registered account is only a USER.
                                2. Click **Authorize** above and paste the token (no `Bearer `
                                   prefix needed - Swagger adds it).

                                New accounts can also be created with `POST /api/auth/register`;
                                they are ACTIVE immediately.

                                **Seeded development logins** (password `Password123!`):
                                `admin.amina` and `admin.youssef` are ADMIN;
                                `sara.mansouri`, `ahmed.benali` and 18 others are USER.

                                Tokens are signed JWTs valid for 60 minutes. There is no refresh
                                token, so after expiry you log in again.

                                **Roles** - the `type` claim drives RBAC:
                                - `USER` - may act on their own account only.
                                - `ADMIN` - may additionally list users and change status/type.

                                A 401 means no valid token; a 403 means a valid token without the
                                required role.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken returned by POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
