package com.Balu.Movie_Recommend.Repositories;

import com.Balu.Movie_Recommend.Entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByExternalId(String externalId);
}
