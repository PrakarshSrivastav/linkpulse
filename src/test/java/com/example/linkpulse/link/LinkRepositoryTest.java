package com.example.linkpulse.link;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

@DataJpaTest(showSql=false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LinkRepositoryTest {

    @Autowired
    LinkRepository repository;

    @Test
    void savesAndFindsByCode() {
        repository.saveAndFlush(new Link("abc1234", "https://example.com", null));

        var found = repository.findByCode("abc1234");

        Assertions.assertThat(found).isPresent();
        Assertions.assertThat(found.get().getLongUrl()).isEqualTo("https://example.com");
        Assertions.assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void unknownCodeReturnsEmpty() {
        Assertions.assertThat(repository.findByCode("nope123")).isEmpty();
    }

    @Test
    void duplicateCodeIsRejectedByDatabase() {
        repository.saveAndFlush(new Link("dup1234", "https://a.com", null));

        Assertions.assertThatThrownBy(() ->
                repository.saveAndFlush(new Link("dup1234", "https://b.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiryBoundary() {
        var expiry = Instant.parse("2030-01-01T00:00:00Z");
        var link = new Link("exp1234", "https://x.com", expiry);

        Assertions.assertThat(link.isExpiredAt(expiry.minusSeconds(1))).isFalse();
        Assertions.assertThat(link.isExpiredAt(expiry)).isTrue();
        Assertions.assertThat(new Link("nev1234", "https://x.com", null).isExpiredAt(expiry)).isFalse();
    }
}