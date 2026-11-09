package com.expensemanagement.backend.UserManagement.Controllers;

import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.ProfilePictureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves stored profile pictures.
 *
 * <h2>Why this one endpoint is public</h2>
 * The file name is 128 random bits, written by the server and never chosen by a user, so the URL
 * acts as an unguessable capability: only the account owner's app ever receives it. Keeping the
 * token out of the image request is worth it, because it lets the bytes be cached for a year and
 * fetched by an {@code <img>} tag - which is exactly what the web build needs, since an image
 * element cannot send an {@code Authorization} header.
 *
 * <p>This is not a hole in the privacy rule: that rule protects income, expenses, budgets, savings
 * and debt. A profile picture is not financial data, and no other endpoint here exposes anyone
 * else's.
 *
 * <p>Cache busting needs no version parameter: every upload writes a new random name and deletes
 * the old file, so a changed picture is a changed URL, and {@code immutable} stays honest.
 */
@RestController
@RequestMapping("/api/avatars")
@Tag(name = "Avatars", description = "Stored profile pictures, addressed by an unguessable file name")
public class AvatarController
{
    private final ProfilePictureService profilePictureService;

    public AvatarController(ProfilePictureService profilePictureService)
    {
        this.profilePictureService = profilePictureService;
    }

    @GetMapping("/{file:.+}")
    @Operation(summary = "One stored profile picture",
            description = """
                    Public: the random file name is the only credential, which is what allows a
                    year-long immutable cache and lets a plain image tag load it.

                    Answers 404 for a name that is not a stored picture.""")
    @ApiResponse(responseCode = "200", description = "The JPEG image")
    @ApiResponse(responseCode = "404", description = "No such picture", content = @Content)
    public ResponseEntity<Resource> get(@PathVariable String file)
    {
        Resource image = profilePictureService.load(file);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image);
    }
}
