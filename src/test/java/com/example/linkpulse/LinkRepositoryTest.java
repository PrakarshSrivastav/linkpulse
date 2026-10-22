package com.example.linkpulse;

import com.example.linkpulse.link.Link;
import com.example.linkpulse.link.LinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LinkRepositoryTest {

    @Autowired
    LinkRepository repository;

    @Test
    void savesAndFindsByCode() {
        repository.saveAndFlush(new Link("abc1234", "https://example.com", null));

        var found = repository.findByCode("abc1234");

        assertThat(found).isPresent();
        assertThat(found.get().getLongUrl()).isEqualTo("https://example.com");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void unknownCodeReturnsEmpty() {
        assertThat(repository.findByCode("nope123")).isEmpty();
    }

    @Test
    void duplicateCodeIsRejectedByDatabase() {
        repository.saveAndFlush(new Link("dup1234", "https://a.com", null));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new Link("dup1234", "https://b.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiryBoundary() {
        var expiry = Instant.parse("2030-01-01T00:00:00Z");
        var link = new Link("exp1234", "https://x.com", expiry);

        assertThat(link.isExpiredAt(expiry.minusSeconds(1))).isFalse();
        assertThat(link.isExpiredAt(expiry)).isTrue();
        assertThat(new Link("nev1234", "https://x.com", null).isExpiredAt(expiry)).isFalse();
    }
}