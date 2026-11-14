package com.expensemanagement.backend.Ai;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settles the name a model suggests against the names the user actually has.
 *
 * <p>The prompt gives the model the real categories and income sources, but an answer can still
 * come back as a near miss ("Groceries" for "Food & Groceries"), a synonym ("Salary" for "Salary"
 * vs "Freelance"), or null when it is unsure. Passing that straight to the app leaves a row the
 * user must complete by hand, so each name is resolved here first:
 *
 * <ol>
 *   <li>an exact match, ignoring case;</li>
 *   <li>otherwise the first name that contains the suggestion or is contained by it - but only
 *       when the shorter of the two is at least three characters, so a stray "s" cannot match
 *       everything;</li>
 *   <li>otherwise a catch-all category or source ("Other", "Other income", "Autre", ...);</li>
 *   <li>otherwise the first name in the list, which is alphabetical because the queries order
 *       by name.</li>
 * </ol>
 *
 * <p>Null is returned only when the account has nothing to choose from, which the app already
 * explains in the review.
 */
public final class AiVocabulary
{
    /** Names that read as "none of the above" in the languages this product is used in. */
    private static final Set<String> CATEGORY_CATCH_ALL = Set.of(
            "other", "others", "other expense", "autre", "autres", "misc", "miscellaneous",
            "divers", "uncategorized", "general");

    private static final Set<String> SOURCE_CATCH_ALL = Set.of(
            "other income", "other", "others", "autre", "autres", "autre revenu", "misc",
            "miscellaneous", "divers");

    private AiVocabulary()
    {
    }

    /**
     * @param suggested the name the model answered with; may be null
     * @param names     the caller's category names, ordered by name
     * @return a name that exists for this account, or null when the list is empty
     */
    public static String resolveCategory(String suggested, List<String> names)
    {
        return resolve(suggested, names, CATEGORY_CATCH_ALL);
    }

    /**
     * @param suggested the name the model answered with; may be null
     * @param names     the caller's income-source names, ordered by name
     * @return a name that exists for this account, or null when the list is empty
     */
    public static String resolveIncomeSource(String suggested, List<String> names)
    {
        return resolve(suggested, names, SOURCE_CATCH_ALL);
    }

    private static String resolve(String suggested, List<String> names, Set<String> catchAll)
    {
        if (names == null || names.isEmpty())
        {
            return null;
        }

        // Sorted here rather than relying on the caller, so the fallback is always the same name
        // for the same account - "the first one" must not depend on query order.
        List<String> ordered = names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();

        String wanted = normalise(suggested);

        if (!wanted.isEmpty())
        {
            for (String name : ordered)
            {
                if (normalise(name).equals(wanted))
                {
                    return name;
                }
            }

            for (String name : ordered)
            {
                String candidate = normalise(name);
                String shorter = candidate.length() <= wanted.length() ? candidate : wanted;

                if (shorter.length() >= 3 && (candidate.contains(wanted) || wanted.contains(candidate)))
                {
                    return name;
                }
            }
        }

        for (String name : ordered)
        {
            if (catchAll.contains(normalise(name)))
            {
                return name;
            }
        }

        return ordered.get(0);
    }

    private static String normalise(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
