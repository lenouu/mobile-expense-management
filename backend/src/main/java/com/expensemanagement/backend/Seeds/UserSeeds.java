package com.expensemanagement.backend.Seeds;

import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Development seed data: 2 admins and 20 ordinary users.
 *
 * <p>Runs once on startup, and only when {@code app.seed.enabled=true}. It is switched off by
 * default in code ({@code matchIfMissing} is false), so forgetting the property fails safe -
 * seeding known credentials into a real database would be a serious hole.
 *
 * <p>Every account is created {@code ACTIVE} with the password
 * {@value #DEFAULT_PASSWORD}, so they can be used in Swagger UI immediately - unlike
 * accounts created through {@code POST /api/auth/register}, which start {@code PENDING} and
 * cannot log in until an admin activates them.
 *
 * <p>Each account also stores the same BCrypt hash, because hashing once instead of 22 times
 * keeps startup fast (BCrypt is deliberately slow). Real registration hashes per user, so
 * two real users with the same password get different hashes.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@Slf4j
public class UserSeeds implements ApplicationRunner
{
    /** Shared password for every seeded account. Development only. */
    public static final String DEFAULT_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeds(UserRepository userRepository, PasswordEncoder passwordEncoder)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * One row of seed data.
     *
     * <p>A private record rather than 22 literal {@code User} constructions: it keeps the
     * table below readable and makes it obvious at a glance which accounts are admins.
     */
    private record Seed(String userName, String email, String firstName, String lastName,
                        LocalDate dateOfBirth, UserType type)
    {
    }

    private static final List<Seed> SEEDS = List.of(
            // ---------------- 2 admins ----------------
            new Seed("admin.amina", "admin.amina@example.com", "Amina", "Haddad",
                    LocalDate.of(1985, 3, 12), UserType.ADMIN),
            new Seed("admin.youssef", "admin.youssef@example.com", "Youssef", "Benali",
                    LocalDate.of(1982, 11, 4), UserType.ADMIN),

            // ---------------- 20 ordinary users ----------------
            new Seed("sara.mansouri", "sara.mansouri@example.com", "Sara", "Mansouri",
                    LocalDate.of(1995, 4, 12), UserType.USER),
            new Seed("ahmed.benali", "ahmed.benali@example.com", "Ahmed", "Benali",
                    LocalDate.of(1990, 7, 23), UserType.USER),
            new Seed("leila.haddad", "leila.haddad@example.com", "Leila", "Haddad",
                    LocalDate.of(1993, 1, 30), UserType.USER),
            new Seed("omar.cherif", "omar.cherif@example.com", "Omar", "Cherif",
                    LocalDate.of(1988, 9, 5), UserType.USER),
            new Seed("nadia.bouzid", "nadia.bouzid@example.com", "Nadia", "Bouzid",
                    LocalDate.of(1997, 6, 18), UserType.USER),
            new Seed("karim.taleb", "karim.taleb@example.com", "Karim", "Taleb",
                    LocalDate.of(1991, 2, 14), UserType.USER),
            new Seed("imane.zahra", "imane.zahra@example.com", "Imane", "Zahra",
                    LocalDate.of(1999, 12, 1), UserType.USER),
            new Seed("younes.amrani", "younes.amrani@example.com", "Younes", "Amrani",
                    LocalDate.of(1987, 5, 9), UserType.USER),
            new Seed("fatima.alaoui", "fatima.alaoui@example.com", "Fatima", "Alaoui",
                    LocalDate.of(1994, 8, 27), UserType.USER),
            new Seed("mehdi.tazi", "mehdi.tazi@example.com", "Mehdi", "Tazi",
                    LocalDate.of(1992, 3, 3), UserType.USER),
            new Seed("salma.idrissi", "salma.idrissi@example.com", "Salma", "Idrissi",
                    LocalDate.of(1996, 10, 21), UserType.USER),
            new Seed("bilal.hamdi", "bilal.hamdi@example.com", "Bilal", "Hamdi",
                    LocalDate.of(1989, 11, 16), UserType.USER),
            new Seed("rania.saidi", "rania.saidi@example.com", "Rania", "Saidi",
                    LocalDate.of(2000, 1, 8), UserType.USER),
            new Seed("anis.mokhtar", "anis.mokhtar@example.com", "Anis", "Mokhtar",
                    LocalDate.of(1986, 4, 25), UserType.USER),
            new Seed("hind.benamar", "hind.benamar@example.com", "Hind", "Benamar",
                    LocalDate.of(1998, 7, 11), UserType.USER),
            new Seed("rachid.fassi", "rachid.fassi@example.com", "Rachid", "Fassi",
                    LocalDate.of(1984, 6, 2), UserType.USER),
            new Seed("dounia.kabbaj", "dounia.kabbaj@example.com", "Dounia", "Kabbaj",
                    LocalDate.of(1993, 9, 19), UserType.USER),
            new Seed("tarik.lahlou", "tarik.lahlou@example.com", "Tarik", "Lahlou",
                    LocalDate.of(1990, 12, 30), UserType.USER),
            new Seed("meryem.sefrioui", "meryem.sefrioui@example.com", "Meryem", "Sefrioui",
                    LocalDate.of(1995, 2, 6), UserType.USER),
            new Seed("hamza.belkacem", "hamza.belkacem@example.com", "Hamza", "Belkacem",
                    LocalDate.of(1997, 8, 14), UserType.USER)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args)
    {
        // Idempotence guard. Today ddl-auto=create wipes the table on every start so this
        // never triggers, but the moment the schema is managed by update (or migrations)
        // a second run would otherwise blow up on the unique userName/email constraints.
        long existing = userRepository.count();
        if (existing > 0)
        {
            log.info("UserSeeds skipped - {} account(s) already present.", existing);
            return;
        }

        String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);

        List<User> users = SEEDS.stream()
                .map(seed -> toUser(seed, passwordHash))
                .toList();

        userRepository.saveAll(users);

        List<String> adminUserNames = SEEDS.stream()
                .filter(seed -> seed.type() == UserType.ADMIN)
                .map(Seed::userName)
                .toList();

        long adminCount = adminUserNames.size();

        log.warn("UserSeeds inserted {} accounts ({} ADMIN, {} USER). Shared password: '{}'. "
                        + "Admin logins: {}. This is development data - set app.seed.enabled=false "
                        + "outside development.",
                users.size(), adminCount, users.size() - adminCount, DEFAULT_PASSWORD, adminUserNames);
    }

    private User toUser(Seed seed, String passwordHash)
    {
        User user = new User();
        user.setUserName(seed.userName());
        user.setEmail(seed.email());
        user.setFirstName(seed.firstName());
        user.setLastName(seed.lastName());
        user.setDateOfBirth(seed.dateOfBirth());
        user.setPasswordHash(passwordHash);

        // Set explicitly rather than relying on the entity's @PrePersist hook, which would
        // leave both at their restricted defaults (PENDING / USER). A PENDING account cannot
        // authenticate, and the admins need the ADMIN role, so the seeds would be useless.
        user.setStatus(UserAccountState.ACTIVE);
        user.setType(seed.type());

        // createdAt / updatedAt are intentionally left null - @PrePersist stamps them.
        return user;
    }
}
