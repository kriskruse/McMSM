package dk.mcmsm.entities;

public class UserEntity {

    private Long id;
    private String username;
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
