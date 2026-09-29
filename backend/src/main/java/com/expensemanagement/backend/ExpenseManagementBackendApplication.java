package com.expensemanagement.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * The {@code PageSerializationMode.VIA_DTO} setting matters for the paged admin endpoints.
 *
 * <p>By default Spring Data serialises a {@code PageImpl} directly, and it warns at runtime
 * that the resulting JSON shape is not guaranteed to be stable - the flat
 * {@code {"content":[...],"totalElements":22,"size":20}} form is an implementation detail
 * that has changed between versions. {@code VIA_DTO} makes it emit Spring Data's official
 * {@code PagedModel} instead, where the metadata sits under a {@code page} object:
 *
 * <pre>
 * {
 *   "content": [ ... ],
 *   "page": { "size": 20, "number": 0, "totalElements": 22, "totalPages": 2 }
 * }
 * </pre>
 *
 * <p>Worth pinning down now rather than later: mobile clients parse this, and a Spring
 * upgrade silently changing the envelope is the kind of breakage that only shows up in
 * production.
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class ExpenseManagementBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseManagementBackendApplication.class, args);
	}

}
