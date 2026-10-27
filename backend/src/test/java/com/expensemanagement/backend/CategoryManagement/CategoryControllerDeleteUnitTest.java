package com.expensemanagement.backend.CategoryManagement;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.expensemanagement.backend.CategoryManagement.Controllers.CategoryController;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.DefaultCategoryService;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.UserCategoryService;
import com.expensemanagement.backend.Exceptions.GlobalExceptionHandler;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;

@ExtendWith(MockitoExtension.class)
class CategoryControllerDeleteUnitTest {

	@Mock
	private DefaultCategoryService defaultCategoryService;

	@Mock
	private UserCategoryService userCategoryService;

	@Mock
	private ErrorLogService errorLogService;

	private MockMvc mockMvc;

	private final Jwt mockJwt = new Jwt(
			"token",
			Instant.now(),
			Instant.now().plusSeconds(3600),
			Map.of("alg", "none"),
			Map.of("sub", "123")
	);

	@BeforeEach
	void setUp() {
		CategoryController controller = new CategoryController(defaultCategoryService, userCategoryService);
		GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(errorLogService);

		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(exceptionHandler)
				.setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
					@Override
					public boolean supportsParameter(MethodParameter parameter) {
						return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
								&& parameter.getParameterType().equals(Jwt.class);
					}

					@Override
					public Object resolveArgument(MethodParameter parameter,
												  ModelAndViewContainer mavContainer,
												  NativeWebRequest webRequest,
												  WebDataBinderFactory binderFactory) {
						return mockJwt;
					}
				})
				.build();
	}

	@Test
	void delete_whenCategoryIsUnused_returns204NoContent() throws Exception {
		Long categoryId = 42L;
		doNothing().when(userCategoryService).delete(123L, categoryId);

		mockMvc.perform(delete("/api/categories/" + categoryId))
				.andExpect(status().isNoContent());

		verify(userCategoryService).delete(123L, categoryId);
	}

	@Test
	void delete_whenCategoryHasLinkedExpenses_returns409Conflict() throws Exception {
		Long categoryId = 42L;
		doThrow(new OperationNotAllowedException("Cannot delete category because it has active linked expenses"))
				.when(userCategoryService).delete(123L, categoryId);

		mockMvc.perform(delete("/api/categories/" + categoryId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("operation_not_allowed"))
				.andExpect(jsonPath("$.message").value("Cannot delete category because it has active linked expenses"));

		verify(userCategoryService).delete(123L, categoryId);
	}

	@Test
	void delete_whenCategoryNotFound_returns404NotFound() throws Exception {
		Long categoryId = 999L;
		doThrow(new ResourceNotFoundException("No category with id " + categoryId))
				.when(userCategoryService).delete(123L, categoryId);

		mockMvc.perform(delete("/api/categories/" + categoryId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("not_found"))
				.andExpect(jsonPath("$.message").value("No category with id " + categoryId));

		verify(userCategoryService).delete(123L, categoryId);
	}
}
