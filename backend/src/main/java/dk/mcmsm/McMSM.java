package dk.mcmsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class McMSM {

    static void main(String[] args) {

        SpringApplication.run(McMSM.class, args);
    }
}
