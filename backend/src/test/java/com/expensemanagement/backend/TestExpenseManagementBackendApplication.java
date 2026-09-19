package com.expensemanagement.backend;

import org.springframework.boot.SpringApplication;

public class TestExpenseManagementBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ExpenseManagementBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
