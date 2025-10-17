package net.enjoy.springboot.registrationlogin.controller;

import jakarta.validation.Valid;
import net.enjoy.springboot.registrationlogin.dto.UserDto;
import net.enjoy.springboot.registrationlogin.repository.OtpTokenRepository;
import net.enjoy.springboot.registrationlogin.repository.UserRepository;
import net.enjoy.springboot.registrationlogin.security.OtpService;
import net.enjoy.springboot.registrationlogin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AuthController {
    private final UserService userService;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpTokenRepository otpRepo;

    @Autowired
    private UserRepository userRepo;

    // ✅ Serve home page when user visits "/"
    @GetMapping("/home")
    public String home(HttpServletResponse response) {
        setNoCacheHeaders(response); // Prevent caching
        return "index"; // Load index.html
    }

    // ✅ Serve registration page
    @GetMapping("/register")
    public String showRegistrationForm(Model model, HttpServletResponse response) {
        UserDto user = new UserDto();
        model.addAttribute("user", user);
        setNoCacheHeaders(response);  // Prevent caching
        return "register";
    }

    // ✅ Handle registration form submission
    @PostMapping("/register/save")
    public String registration(@Valid @ModelAttribute("user") UserDto userDto,
                               BindingResult result,
                               Model model,
                               HttpServletResponse response) {

        if (userService.isEmailRegistered(userDto.getEmail())) {
            result.rejectValue("email", null, "There is already an account registered with the same email");
        }

        // ✅ Check if OTP verified
        if (!otpService.isEmailVerified(userDto.getEmail())) {
            result.rejectValue("email", null, "Please verify your email with OTP before registering");
        }

        if (result.hasErrors()) {
            model.addAttribute("user", userDto);
            return "register";
        }

        userService.registerUser(userDto);

        // Once registered, clean up otp token
        otpService.cleanup(userDto.getEmail());

        setNoCacheHeaders(response);  // Prevent caching
        return "redirect:/register?success";
    }

    // ✅ User registration page
    @GetMapping("/userRegister")
    public String RegistrationForm(Model model, HttpServletResponse response) {
        UserDto user = new UserDto();
        model.addAttribute("user", user);
        setNoCacheHeaders(response);  // Prevent caching
        return "userRegister";
    }

    @PostMapping("/userRegister/save")
    public String userRegistration(@Valid @ModelAttribute("user") UserDto userDto,
                                   BindingResult result,
                                   Model model,
                                   HttpServletResponse response) {

        if (userService.isEmailRegistered(userDto.getEmail())) {
            result.rejectValue("email", null, "There is already an account registered with the same email");
        }

        // ✅ Check if OTP verified
        if (!otpService.isEmailVerified(userDto.getEmail())) {
            result.rejectValue("email", null, "Please verify your email with OTP before registering");
        }

        if (result.hasErrors()) {
            model.addAttribute("user", userDto);
            return "userRegister";
        }

        userService.registerUser(userDto);

        // Once registered, clean up otp token
        otpService.cleanup(userDto.getEmail());

        setNoCacheHeaders(response);  // Prevent caching
        return "redirect:/userRegister?success";
    }

    @GetMapping("/login")
    public String login(HttpServletResponse response, Authentication authentication) {
        setNoCacheHeaders(response);
        return "login";
    }

    // ✅ Send OTP
    @PostMapping("/send-otp")
    @ResponseBody
    public String sendOtp(@RequestParam("email") String email) {
        // ✅ Do not allow if already registered
        if (userService.isEmailRegistered(email)) {
            return "Email already registered";
        }

        // ✅ Generate and save OTP against this email
        String otp = otpService.generateOtp(email);
        otpService.sendOtpEmail(email, otp);

        return "OTP sent";
    }

    // ✅ Verify OTP
    @PostMapping("/verify-otp")
    @ResponseBody
    public String verifyOtp(@RequestParam("email") String email,
                            @RequestParam("otp") String otp) {

        boolean verified = otpService.verifyOtp(email, otp);
        if (verified) {
            return "Verified"; // frontend enables register button
        }
        return "Invalid or expired OTP";
    }


    // ✅ Utility function to prevent caching
    private void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setHeader("Expires", "0"); // Proxies
    }
}
