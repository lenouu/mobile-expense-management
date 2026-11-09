package com.expensemanagement.backend.CategoryManagement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.DefaultCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceImplements.UserCategoryServiceImpl;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserCategoryServiceDeleteUnitTest {

	@Mock
	private UserCategoryRepository userCategoryRepository;

	@Mock
	private DefaultCategoryRepository defaultCategoryRepository;

	@Mock
	private UserRepository userRepository;

	private UserCategoryServiceImpl userCategoryService;

	@BeforeEach
	void setUp() {
		userCategoryService = new UserCategoryServiceImpl(
				userCategoryRepository,
				defaultCategoryRepository,
				userRepository
		);
	}

	@Test
	void delete_whenCategoryExistsAndNoExpensesLinked_deletesSuccessfully() {
		UUID userId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();

		User user = new User();
		user.setId(userId);

		UserCategory category = new UserCategory();
		category.setId(categoryId);
		category.setUser(user);
		category.setName("Custom Hobbies");

		when(userCategoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
		when(userCategoryRepository.countExpensesUsing(categoryId)).thenReturn(0L);

		userCategoryService.delete(userId, categoryId);

		verify(userCategoryRepository).findByIdAndUserId(categoryId, userId);
		verify(userCategoryRepository).countExpensesUsing(categoryId);
		verify(userCategoryRepository).delete(category);
	}

	@Test
	void delete_whenCategoryHasLinkedExpenses_throwsOperationNotAllowedException() {
		UUID userId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();

		User user = new User();
		user.setId(userId);

		UserCategory category = new UserCategory();
		category.setId(categoryId);
		category.setUser(user);
		category.setName("Groceries");

		when(userCategoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
		when(userCategoryRepository.countExpensesUsing(categoryId)).thenReturn(3L);

		assertThatThrownBy(() -> userCategoryService.delete(userId, categoryId))
				.isInstanceOf(OperationNotAllowedException.class)
				.hasMessageContaining("Category 'Groceries' is still used by 3 expense(s)");

		verify(userCategoryRepository).findByIdAndUserId(categoryId, userId);
		verify(userCategoryRepository).countExpensesUsing(categoryId);
		verify(userCategoryRepository, never()).delete(category);
	}

	@Test
	void delete_whenCategoryNotFound_throwsResourceNotFoundException() {
		UUID userId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();

		when(userCategoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userCategoryService.delete(userId, categoryId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No category with id " + categoryId);

		verify(userCategoryRepository).findByIdAndUserId(categoryId, userId);
		verify(userCategoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
	}
}
