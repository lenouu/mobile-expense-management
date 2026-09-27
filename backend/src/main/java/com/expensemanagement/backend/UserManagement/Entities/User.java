package com.expensemanagement.backend.UserManagement.Entities;

import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.util.Date;

@Entity
public class User
{
    @Column
    String userName;
    String email ;
    String password_hash ;
    String firstName;
    String lastName;
    Date createdAt;
    Date updatedAt;
    String ReferenceToProfilePicture ;
    UserAccountState status ;
}
