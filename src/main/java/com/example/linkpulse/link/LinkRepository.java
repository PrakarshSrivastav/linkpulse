package com.example.linkpulse.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);

    List<Link> findTop100ByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @Transactional
    @Modifying
    @Query("delete from Link l where l.code = :code and l.ownerId = :ownerId")
    int deleteOwned(@Param("code") String code, @Param("ownerId") Long ownerId);

    Optional<Link> findByCodeAndOwnerId(String code, Long ownerId);
}