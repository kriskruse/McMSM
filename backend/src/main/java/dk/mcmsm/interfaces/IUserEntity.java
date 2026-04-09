package dk.mcmsm.interfaces;

public interface IUserEntity {
    public Long getId();
    public String getUsername();
    public String getPassword();
    public void setId(Long id);
    public void setUsername(String username);
    public void setPassword(String password);
}
