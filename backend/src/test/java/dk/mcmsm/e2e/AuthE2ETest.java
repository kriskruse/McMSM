package dk.mcmsm.e2e;

import dk.mcmsm.dto.responses.LoginResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthE2ETest extends BaseE2ETest {

    @Test
    void register_newUser_succeeds() {
        var response = post("/api/register", Map.of("username", "testuser", "password", "secret123"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("testuser");
    }

    @Test
    void register_duplicateUsername_returns401() {
        post("/api/register", Map.of("username", "dupeuser", "password", "pass1"), String.class);

        var response = post("/api/register", Map.of("username", "dupeuser", "password", "pass2"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_validCredentials_succeeds() {
        post("/api/register", Map.of("username", "loginuser", "password", "correctpass"), String.class);

        var response = post("/api/login", Map.of("username", "loginuser", "password", "correctpass"), LoginResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().username()).isEqualTo("loginuser");
    }

    @Test
    void login_wrongPassword_returns401() {
        post("/api/register", Map.of("username", "wrongpass", "password", "correct"), String.class);

        var response = post("/api/login", Map.of("username", "wrongpass", "password", "incorrect"), LoginResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void login_unknownUser_returns401() {
        var response = post("/api/login", Map.of("username", "ghost", "password", "anything"), LoginResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }
}
