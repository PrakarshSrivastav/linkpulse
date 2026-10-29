package com.example.linkpulse.link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class LinkService {

    static final int MAX_ATTEMPTS = 3;
    private static final String CODE_CONSTRAINT = "uk_links_code";
    private static final Logger log = LoggerFactory.getLogger(LinkService.class);

    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final LinkCache cache;

    public LinkService(LinkRepository repository, CodeGenerator codeGenerator, LinkCache cache) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.cache = cache;
    }

    // Deliberately NOT @Transactional: each insert attempt must get its own
    // transaction, because Postgres aborts a transaction after a unique violation.
    public Link create(String longUrl, String alias, Instant expiresAt) {
        Link link = (alias != null)
                ? createWithAlias(longUrl, alias, expiresAt)
                : createWithRandomCode(longUrl, expiresAt);
        // A "not found" may be cached for this code (someone visited it before it existed).
        cache.evict(link.getCode());
        return link;
    }

    public String resolveLongUrl(String code) {
        Optional<String> cached = cache.get(code);
        if (cached.isPresent()) {
            if (LinkCache.NOT_FOUND.equals(cached.get())) {
                throw new LinkNotFoundException(code);
            }
            return cached.get();
        }

        Instant now = Instant.now();
        Optional<Link> found = repository.findByCode(code);
        if (found.isEmpty()) {
            cache.putNotFound(code);
            throw new LinkNotFoundException(code);
        }
        Link link = found.get();
        if (link.isExpiredAt(now)) {
            throw new LinkExpiredException(code);
        }
        cache.putLink(link, now);
        return link.getLongUrl();
    }

    private Link createWithAlias(String longUrl, String alias, Instant expiresAt) {
        try {
            return repository.saveAndFlush(new Link(alias, longUrl, expiresAt));
        } catch (DataIntegrityViolationException e) {
            if (isCodeCollision(e)) {
                throw new AliasTakenException(alias);
            }
            throw e;
        }
    }

    private Link createWithRandomCode(String longUrl, Instant expiresAt) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            try {
                return repository.saveAndFlush(new Link(code, longUrl, expiresAt));
            } catch (DataIntegrityViolationException e) {
                if (!isCodeCollision(e)) {
                    throw e;
                }
                log.warn("Code collision on attempt {}/{}: {}", attempt, MAX_ATTEMPTS, code);
            }
        }
        throw new CodeGenerationException(MAX_ATTEMPTS);
    }

    private static boolean isCodeCollision(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && CODE_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}