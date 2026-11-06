package com.expensemanagement.backend.UserManagement.Controllers;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;
import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import com.expensemanagement.backend.UserManagement.Mappers.UserMappers;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.ProfilePictureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.UUID;

/**
 * The caller's own profile picture. Like the rest of `/users/me`, the account comes from the
 * token, so there is no id to tamper with and no way to change someone else's picture.
 *
 * <p>The image travels as base64 inside a JSON body, not as a multipart part. A multipart upload
 * from React Native failed before the request ever reached the server on a real device, while the
 * app's plain JSON calls work - so the upload uses the transport that is proven to work. The cost
 * is a third more bytes on the wire; the benefit is that it actually arrives.
 *
 * <p>The image itself is served by {@code AvatarController} from a public, unguessable URL - see
 * the note there for why it is not behind a token.
 */
@RestController
@RequestMapping("/api/users/me/photo")
@Tag(name = "My account", description = "Self-service endpoints for the signed-in user")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
@Slf4j
public class ProfilePictureController
{
    private final ProfilePictureService profilePictureService;

    public ProfilePictureController(ProfilePictureService profilePictureService)
    {
        this.profilePictureService = profilePictureService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload my profile picture",
            description = """
                    Body: `{"image": "<base64>"}` - the image file base64-encoded, without a
                    `data:` prefix.

                    Up to 5 MB once decoded. Formats the JVM can decode: **JPEG, PNG, GIF, BMP**.
                    HEIC and WebP are rejected with 400.

                    The server decodes the bytes, scales the image to at most 512 px on its longest
                    edge and re-encodes it as JPEG, which also strips EXIF (including any GPS
                    location). Any previous picture is replaced and its file deleted, so an old URL
                    stops working.

                    Returns the updated profile: `profilePictureUrl` is the new picture's path,
                    relative to the API base.""")
    @ApiResponse(responseCode = "200", description = "Stored; the updated profile is returned")
    @ApiResponse(responseCode = "400", description = "No image, not valid base64, too large, or "
            + "not a readable image", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public UserResponses.Details upload(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody UserRequests.UploadPicture request)
    {
        byte[] image = decodeBase64(request.getImage());

        // Logged before the work starts, so the console shows that the request arrived even when
        // the image is then rejected - otherwise a 400 leaves no trace at all and looks like a
        // request that never happened.
        log.info("Profile picture upload from user id={} ({} bytes)", jwt.getSubject(), image.length);

        return UserMappers.toDetails(profilePictureService.store(currentUserId(jwt), image));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove my profile picture",
            description = "Deletes the stored file. The account falls back to its initials.")
    @ApiResponse(responseCode = "204", description = "Picture removed")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public void remove(@AuthenticationPrincipal Jwt jwt)
    {
        profilePictureService.remove(currentUserId(jwt));
    }

    /** Base64 to bytes, rejecting anything that is not valid base64. */
    private static byte[] decodeBase64(String encoded)
    {
        try
        {
            return Base64.getDecoder().decode(encoded.trim());
        }
        catch (IllegalArgumentException ex)
        {
            throw new InvalidRequestException("The image data is not valid base64");
        }
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}
