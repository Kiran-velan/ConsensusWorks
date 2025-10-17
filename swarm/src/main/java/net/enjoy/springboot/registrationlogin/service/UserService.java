package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.dto.UserDto;
import net.enjoy.springboot.registrationlogin.entity.User;

import java.util.List;

public interface UserService {

    // Save user information to the database
    void saveUser(UserDto userDto);

    // Check if the email is already registered
    boolean isEmailRegistered(String email);

    // Find a user by email address
    User findUserByEmail(String email);

    // Find all registered users (typically for admin view)
    List<UserDto> findAllUsers();

    // Register a new user (this might internally use saveUser but could include additional logic)
    void registerUser(UserDto userDto);

    // Additional method for handling login (optional)
    boolean validateLogin(String email, String password);

    // Method to get the currently logged-in user
    User getCurrentLoggedInUser();
}
