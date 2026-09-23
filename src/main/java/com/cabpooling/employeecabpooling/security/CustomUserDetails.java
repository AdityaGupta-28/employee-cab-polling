package com.cabpooling.employeecabpooling.security;

import com.cabpooling.employeecabpooling.model.entity.Employee;
import com.cabpooling.employeecabpooling.model.enums.Gender;
import com.cabpooling.employeecabpooling.model.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String name;
    private final Role role;
    private final Gender gender;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Employee employee) {
        this.id = employee.getId();
        this.email = employee.getEmail();
        this.password = employee.getPasswordHash();
        this.name = employee.getName();
        this.role = employee.getRole();
        this.gender = employee.getGender();
        this.authorities = List.of(new SimpleGrantedAuthority(employee.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
