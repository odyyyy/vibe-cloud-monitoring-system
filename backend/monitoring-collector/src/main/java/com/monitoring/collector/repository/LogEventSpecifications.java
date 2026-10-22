package com.monitoring.collector.repository;

import com.monitoring.collector.dto.request.LogsQueryRequest;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.facet.LogContextValue;
import com.monitoring.collector.facet.LogFacetField;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The single definition of "which log events match this query".
 *
 * <p>Both the paginated log search and the facet aggregation build their predicate here, so the
 * facet counts always describe exactly the row set the user sees in the result list.
 *
 * <p>Filters:
 * <ul>
 *   <li>application, time range and severity are plain column predicates;</li>
 *   <li>{@code searchText} is a literal, case-insensitive substring of the message; LIKE
 *       wildcards in the text are escaped so they are matched literally;</li>
 *   <li>the facet filters ({@code host}, {@code service}, {@code namespace}) are exact matches
 *       on a member of the JSON document stored in {@code context}. The member is located with
 *       {@code LOCATE}, which behaves identically on MySQL and H2 and needs no JSON functions;
 *       the needle is a bound parameter and never part of the SQL text.</li>
 * </ul>
 */
@Component
public class LogEventSpecifications {

    /** Escape character for the LIKE pattern; {@code !} is used because MySQL and H2 disagree
     *  about the default escape character. */
    public static final char LIKE_ESCAPE = '!';

    /**
     * Builds the predicate for a log query.
     *
     * @param request the validated query parameters
     * @return a specification matching exactly the rows of the main search
     */
    public Specification<LogEvent> matching(LogsQueryRequest request) {
        String searchText = normalize(request.getSearchText());

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("application").get("id"), request.getApplicationId()));
            predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), request.getFrom()));
            predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), request.getTo()));

            if (request.getLevel() != null) {
                predicates.add(cb.equal(root.get("level"), request.getLevel()));
            }

            if (searchText != null) {
                String pattern = "%" + escapeLike(searchText.toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("message")), bind(cb, pattern), LIKE_ESCAPE));
            }

            addContextFilter(predicates, cb, root, LogFacetField.HOST, request.getHost());
            addContextFilter(predicates, cb, root, LogFacetField.SERVICE, request.getService());
            addContextFilter(predicates, cb, root, LogFacetField.NAMESPACE, request.getNamespace());

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Adds an exact-match filter on a JSON member of the {@code context} document.
     *
     * <p>{@code LOCATE(needle, context) > 0} matches the canonical text of the member, for
     * example {@code "host":"server-1"}. The needle is passed as a bind parameter, so a value
     * with quotes or other special characters cannot change the statement.
     */
    private static void addContextFilter(List<Predicate> predicates,
                                         CriteriaBuilder cb,
                                         Root<LogEvent> root,
                                         LogFacetField field,
                                         String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String member = LogContextValue.member(field.contextKey(), value);
        Expression<Integer> position = cb.function(
                "locate", Integer.class, bind(cb, member), root.get("context"));
        predicates.add(cb.greaterThan(position, 0));
    }

    /**
     * Creates an expression that is sent to the database as a bind parameter instead of being
     * inlined into the statement text.
     */
    private static Expression<String> bind(CriteriaBuilder cb, String value) {
        if (cb instanceof HibernateCriteriaBuilder hibernate) {
            return hibernate.value(value);
        }
        return cb.literal(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Escapes LIKE wildcards so the search text is matched literally, not as a pattern. */
    public static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
