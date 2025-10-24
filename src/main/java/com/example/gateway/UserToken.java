package com.example.gateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "user_token")
public class UserToken {
    @Id
    private Long userId;

    @Column(name = "token_version")
    private Long tokenVersion;
}
