package dk.broegger_kruse.applicationdemo.controller;

import dk.broegger_kruse.applicationdemo.dto.requests.LoginRequestDto;
import dk.broegger_kruse.applicationdemo.dto.requests.RegisterRequestDto;
import dk.broegger_kruse.applicationdemo.dto.responses.LoginResponseDto;
import dk.broegger_kruse.applicationdemo.dto.responses.RegisterResponseDto;
import dk.broegger_kruse.applicationdemo.entities.UserEntity;
import dk.broegger_kruse.applicationdemo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    private final UserRepository userRepository;

    public LoginController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequest) {
        logger.info("Login requested for username='{}'.", loginRequest.username());
        boolean valid = userRepository
                .findByUsernameAndPassword(loginRequest.username(), loginRequest.password())
                .isPresent();

        if (valid) {
            logger.info("Login succeeded for username='{}'.", loginRequest.username());
        } else {
            logger.warn("Login failed for username='{}'.", loginRequest.username());
        }

        return valid
                ? ResponseEntity.ok(new LoginResponseDto(true, "Login successful!", loginRequest.username()))
                : ResponseEntity.status(401).body(new LoginResponseDto(false, "Login failed!", loginRequest.username()));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequestDto registerRequest) {
        logger.info("Registration requested for username='{}'.", registerRequest.username());
        boolean valid  = !(userRepository
                .existsByUsername(registerRequest.username()));

        if (valid) {
            userRepository.save(new UserEntity(registerRequest.username(), registerRequest.password()));
            logger.info("Registration succeeded for username='{}'.", registerRequest.username());
            return ResponseEntity.ok(
                    new RegisterResponseDto(true, "Registration successful!", registerRequest.username()).toString());
        }
        logger.warn("Registration failed because username is already taken: username='{}'.", registerRequest.username());
        return ResponseEntity.status(401).body(
                new RegisterResponseDto(false, "Registration failed!", registerRequest.username()).toString());
    }
}
