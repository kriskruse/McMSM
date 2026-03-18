package dk.broegger_kruse.applicationdemo.entities;

import dk.broegger_kruse.applicationdemo.interfaces.IUserEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class UserEntity implements IUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    public UserEntity(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public UserEntity() {

    }

    public Long getId() {return id;}
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }

    public void setId(Long id) { this.id = id;}
    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
}
