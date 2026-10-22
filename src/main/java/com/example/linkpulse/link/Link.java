package com.example.linkpulse.link;


import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name="links")
public class Link {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 32)
    private String code;

    @Column(name="long_url", nullable=false, updatable = false, length = 2048)
    private String longUrl;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="expires_at")
    private Instant expiresAt;

    protected Link(){

    }

    public Link(String code, String longUrl, Instant expiresAt){
        this.code=code;
        this.longUrl=longUrl;
        this.expiresAt=expiresAt;
        this.createdAt=Instant.now();
    }
    public boolean isExpiredAt(Instant now){
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
