package com.expensemanagement.backend.UserManagement.Services.ServiceInterface;

import com.expensemanagement.backend.UserManagement.Entities.User;
import org.springframework.core.io.Resource;

import java.util.UUID;

/**
 * Profile pictures: store, serve and remove (the avatar shown on the profile screen).
 *
 * <p>Files live on disk under {@code app.uploads.dir}, not in the database and not in the build
 * output, so they survive a rebuild. The database only remembers the file name.
 *
 * <p>The image arrives as decoded bytes rather than a {@code MultipartFile}: the client sends it
 * base64-encoded in a JSON body, because a multipart upload from React Native failed before the
 * request ever reached the server on a real device. A JSON POST is the same transport every other
 * call in the app already uses.
 */
public interface ProfilePictureService
{
    /**
     * Validates, re-encodes and stores a new picture for one user, replacing any previous one.
     *
     * @param image the file's bytes, already base64-decoded
     * @return the updated user, whose {@code profilePictureReference} is the new file name
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the image is
     *         empty, too large, or not something that can be decoded
     */
    User store(UUID userId, byte[] image);

    /**
     * Removes the user's picture and the file behind it.
     *
     * @return the updated user, with no picture
     */
    User remove(UUID userId);

    /**
     * The bytes of one stored avatar.
     *
     * <p>{@code fileName} comes from a URL, so it is validated against the exact shape this
     * service writes before anything touches the disk.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if there is no
     *         such file
     */
    Resource load(String fileName);
}
