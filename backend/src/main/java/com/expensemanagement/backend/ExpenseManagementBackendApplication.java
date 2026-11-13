package com.expensemanagement.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Note on paged responses: this class deliberately does <em>not</em> carry
 * {@code @EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)}.
 *
 * <p>That annotation looks like the obvious way to stop Spring Data warning about the unstable
 * {@code PageImpl} JSON shape, but it replaces Boot's {@code Pageable} argument resolver with
 * its own - and that resolver is what applies
 * {@code spring.data.web.pageable.max-page-size}. Adding it silently removes the {@code ?size=}
 * cap, so a client can request the whole table again.
 *
 * <p>Instead every paged endpoint wraps its result in {@link com.expensemanagement.backend.Shared.PageResponse},
 * which produces the contract's flat {@code {data, page, size, totalElements, totalPages}}
 * envelope while leaving Boot's resolver (and therefore the configured cap) in place.
 */
@SpringBootApplication
@EnableScheduling
public class ExpenseManagementBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseManagementBackendApplication.class, args);
	}

}
