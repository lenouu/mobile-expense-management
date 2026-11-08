package com.expensemanagement.backend.UserManagement.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import com.expensemanagement.backend.UserManagement.Services.ServiceInterface.ProfilePictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores profile pictures as JPEG files under {@code app.uploads.dir/avatars}.
 *
 * <h2>Why files on disk rather than in the database</h2>
 * An avatar is read far more often than written and has no relational meaning, so a column of
 * bytes would bloat every user query for no benefit. The database keeps only the file name.
 *
 * <h2>Why every upload is re-encoded</h2>
 * Two reasons, both worth the milliseconds:
 * <ul>
 *   <li>The uploaded bytes are never trusted. Only something the JDK can decode to a raster gets
 *       stored, whatever the request claims its content type is, so a script renamed to
 *       {@code .jpg} is rejected rather than served back to other clients.</li>
 *   <li>Re-encoding drops EXIF. Phone photos routinely carry GPS coordinates, and writing them to
 *       a file that is served to anyone holding its URL would leak the user's location.</li>
 * </ul>
 *
 * <h2>Why the file name is random</h2>
 * It doubles as the cache key: a new picture is a new name, therefore a new URL, so no client can
 * keep showing the previous one. It also means the name a user uploads is never used on disk,
 * which removes path traversal as a concern.
 */
@Service
@Slf4j
public class ProfilePictureServiceImpl implements ProfilePictureService
{
    /** Longest edge of the stored image. A 512 px square is ample for every avatar in the app. */
    private static final int MAX_EDGE = 512;

    /** Bigger than any phone photo needs to be; small enough to bound memory and disk use. */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Exactly the shape this service writes - anything else never touches the disk. */
    private static final Pattern STORED_FILE =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");

    private final UserRepository userRepository;
    private final Path avatarDirectory;

    public ProfilePictureServiceImpl(UserRepository userRepository,
                                     @Value("${app.uploads.dir:./uploads}") String uploadsDir)
    {
        this.userRepository = userRepository;
        this.avatarDirectory = Paths.get(uploadsDir, "avatars").toAbsolutePath().normalize();
        try
        {
            Files.createDirectories(this.avatarDirectory);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(
                    "Could not create the avatar directory " + this.avatarDirectory, ex);
        }
        log.info("Profile pictures are stored in {}", this.avatarDirectory);
    }

    @Override
    @Transactional
    public User store(UUID userId, byte[] image)
    {
        if (image == null || image.length == 0)
        {
            throw new InvalidRequestException("Choose an image to upload");
        }
        if (image.length > MAX_BYTES)
        {
            throw new InvalidRequestException("Images must be " + (MAX_BYTES / (1024 * 1024)) + " MB or smaller");
        }

        User user = getUserOrThrow(userId);

        byte[] jpeg;
        try
        {
            jpeg = reencode(image);
        }
        catch (InvalidRequestException ex)
        {
            // Logged because a rejected image otherwise answers 400 with no trace in the console,
            // which makes a format problem look like "the request never arrived".
            log.warn("Rejected profile picture from user id={} ({} bytes): {}",
                    userId, image.length, ex.getMessage());
            throw ex;
        }

        String fileName = UUID.randomUUID() + ".jpg";
        write(fileName, jpeg);

        // Only after the new file is safely on disk: a failed write must not lose the old picture.
        deleteQuietly(user.getProfilePictureReference());

        user.setProfilePictureReference(fileName);
        User saved = userRepository.save(user);

        log.info("User id={} uploaded a profile picture ({})", userId, fileName);
        return saved;
    }

    @Override
    @Transactional
    public User remove(UUID userId)
    {
        User user = getUserOrThrow(userId);
        deleteQuietly(user.getProfilePictureReference());

        user.setProfilePictureReference(null);
        User saved = userRepository.save(user);

        log.info("User id={} removed their profile picture", userId);
        return saved;
    }

    @Override
    public Resource load(String fileName)
    {
        Path file = resolveStoredFile(fileName);
        if (!Files.isRegularFile(file))
        {
            throw new ResourceNotFoundException("No picture named " + fileName);
        }
        return new FileSystemResource(file);
    }

    // ------------------------------------------------------------------

    /** Decodes the upload, scales it down and re-encodes it as JPEG - dropping EXIF on the way. */
    private static byte[] reencode(byte[] image)
    {
        BufferedImage source;
        try (InputStream input = new ByteArrayInputStream(image))
        {
            source = ImageIO.read(input);
        }
        catch (IOException ex)
        {
            throw new InvalidRequestException("That file could not be read as an image");
        }

        if (source == null)
        {
            throw new InvalidRequestException(
                    "That file is not an image the server can read. Use a JPEG or PNG.");
        }

        BufferedImage scaled = scaleToFit(source);

        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(scaled, "jpg", output))
            {
                throw new IllegalStateException("This JVM has no JPEG encoder");
            }
            return output.toByteArray();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException("Could not encode the profile picture", ex);
        }
    }

    /** Scales down to {@link #MAX_EDGE}; never scales up, so a small image stays sharp. */
    private static BufferedImage scaleToFit(BufferedImage source)
    {
        int width = source.getWidth();
        int height = source.getHeight();
        double factor = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));

        // TYPE_INT_RGB, not the source's type: JPEG has no alpha channel, and writing an image
        // that carries one produces colour artefacts.
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // A white background flattens any transparency instead of leaving it black.
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, Color.WHITE, null);
        }
        finally
        {
            graphics.dispose();
        }
        return target;
    }

    /** Writes via a temporary file in the same directory, so a reader never sees a partial image. */
    private void write(String fileName, byte[] bytes)
    {
        Path target = avatarDirectory.resolve(fileName);
        Path temporary = avatarDirectory.resolve(fileName + ".tmp");
        try
        {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex)
        {
            deleteQuietly(temporary.getFileName().toString());
            throw new UncheckedIOException("Could not save the profile picture", ex);
        }
    }

    /** Resolves a name that passed {@link #STORED_FILE}, and nothing else. */
    private Path resolveStoredFile(String fileName)
    {
        if (fileName == null || !STORED_FILE.matcher(fileName).matches())
        {
            throw new ResourceNotFoundException("No picture named " + fileName);
        }
        Path file = avatarDirectory.resolve(fileName).normalize();
        if (!avatarDirectory.equals(file.getParent()))
        {
            throw new ResourceNotFoundException("No picture named " + fileName);
        }
        return file;
    }

    /** Best effort: a leftover file must never fail the request that was trying to clean it up. */
    private void deleteQuietly(String fileName)
    {
        if (fileName == null || fileName.isBlank() || !STORED_FILE.matcher(fileName).matches())
        {
            return;
        }
        try
        {
            Files.deleteIfExists(avatarDirectory.resolve(fileName));
        }
        catch (IOException ex)
        {
            log.warn("Could not delete the old profile picture {}: {}", fileName, ex.getMessage());
        }
    }

    private User getUserOrThrow(UUID userId)
    {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No user with id " + userId));
    }
}
