package com.example.linkpulse.link;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LinkServiceTest {

    @Autowired
    LinkRepository repository;

    @Autowired
    LinkCache linkCache;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAllInBatch();
        redis.delete(redis.keys(LinkCache.KEY_PREFIX + "*"));
    }
    @Test
    void retriesWithNewCodeOnCollision() {
        repository.saveAndFlush(new Link("collide", "https://existing.com", null));
        var service = new LinkService(repository, new FakeCodeGenerator("collide", "collide", "fresh01"), linkCache);
        Link created = service.create("https://new.com", null, null);

        assertThat(created.getCode()).isEqualTo("fresh01");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        repository.saveAndFlush(new Link("collide", "https://existing.com", null));
        var service = new LinkService(repository, new FakeCodeGenerator("collide", "collide", "collide"), linkCache);
        assertThatThrownBy(() -> service.create("https://new.com", null, null))
                .isInstanceOf(CodeGenerationException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void takenAliasIsRejected() {
        repository.saveAndFlush(new Link("my-alias", "https://existing.com", null));
        var service = new LinkService(repository, new FakeCodeGenerator(), linkCache);
        assertThatThrownBy(() -> service.create("https://new.com", "my-alias", null))
                .isInstanceOf(AliasTakenException.class);
    }

    static class FakeCodeGenerator implements CodeGenerator {
        private final Iterator<String> codes;

        FakeCodeGenerator(String... codes) {
            this.codes = List.of(codes).iterator();
        }

        @Override
        public String generate() {
            return codes.next();
        }
    }
}