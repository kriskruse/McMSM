package dk.broegger_kruse.mcmsm.controller;

import dk.broegger_kruse.mcmsm.entities.UserEntity;
import dk.broegger_kruse.mcmsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data")
public class UserDataBaseController {
    private static final Logger logger = LoggerFactory.getLogger(UserDataBaseController.class);

    private final UserRepository userRepository;

    public UserDataBaseController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserEntity>> getAllUsers() {
        logger.info("Fetching all users.");
        var users = userRepository.findAll();
        logger.info("Fetched {} users.", users.size());
        return ResponseEntity.ok(users);
    }
}