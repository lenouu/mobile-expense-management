package com.expensemanagement.backend.CategoryManagement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

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
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
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

	@Mock
	private ExpenseRepository expenseRepository;

	private UserCategoryServiceImpl userCategoryService;

	@BeforeEach
	void setUp() {
		userCategoryService = new UserCategoryServiceImpl(
				userCategoryRepository,
				defaultCategoryRepository,
				userRepository,
				expenseRepository
		);
	}

	@Test
	void delete_whenCategoryExistsAndNoExpensesLinked_deletesSuccessfully() {
		Long userId = 1L;
		Long categoryId = 42L;

		User user = new User();
		user.setId(userId);

		UserCategory category = new UserCategory();
		category.setId(categoryId);
		category.setUser(user);
		category.setName("Custom Hobbies");

		when(userCategoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
		when(expenseRepository.existsByCategoryId(categoryId)).thenReturn(false);

		userCategoryService.delete(userId, categoryId);

		verify(userCategoryRepository).findByIdAndUserId(categoryId, userId);
		verify(expenseRepository).existsByCategoryId(categoryId);
		verify(userCategoryRepository).delete(category);
	}

	@Test
	void delete_whenCategoryHasLinkedExpenses_throwsOperationNotAllowedException() {
		Long userId = 1L;
		Long categoryId = 42L;

		User user = new User();
		user.setId(userId);

		UserCategory category = new UserCategory();
		category.setId(categoryId);
		category.setUser(user);
		category.setName("Groceries");

		when(userCategoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(category));
		when(expenseRepository.existsByCategoryId(categoryId)).thenReturn(true);

		assertThatThrownBy(() -> userCategoryService.delete(userId, categoryId))
				.isInstanceOf(OperationNotAllowedException.class)
				.hasMessageContaining("Cannot delete category because it has active linked expenses");

		verify(userCategoryRepository).findByIdAndUserId(categoryId, userId);
		verify(expenseRepository).existsByCategoryId(categoryId);
		verify(userCategoryRepository, never()).delete(category);
	}

	@Test
	void delete_whenCategoryDoesNotExist_throwsResourceNotFoundException() {
		Long userId = 1L;
		Long nonExistentId = 999L;

		when(userCategoryRepository.findByIdAndUserId(nonExistentId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userCategoryService.delete(userId, nonExistentId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No category with id " + nonExistentId);

		verify(userCategoryRepository).findByIdAndUserId(nonExistentId, userId);
		verify(expenseRepository, never()).existsByCategoryId(nonExistentId);
		verify(userCategoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void delete_whenCategoryBelongsToDifferentUser_throwsResourceNotFoundException() {
		Long currentUserId = 1L;
		Long otherUserCategoryId = 50L;

		when(userCategoryRepository.findByIdAndUserId(otherUserCategoryId, currentUserId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userCategoryService.delete(currentUserId, otherUserCategoryId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No category with id " + otherUserCategoryId);

		verify(userCategoryRepository).findByIdAndUserId(otherUserCategoryId, currentUserId);
		verify(expenseRepository, never()).existsByCategoryId(otherUserCategoryId);
		verify(userCategoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
	}
}
