package com.example.linkpulse.link;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LinkService {
    static final int MAX_ATTEMPTS=3;
    private static final String CODE_CONSTRAINT="uk_links_code";
    private static final Logger log = (Logger) LoggerFactory.getLogger(LinkService.class);
    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;

    public LinkService(LinkRepository repository, CodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator=codeGenerator;
    }
    public Link create(String longUrl, String alias, Instant expireAt){
        if( alias != null){
            return createWithAlias(longUrl, alias, expireAt);
        }
        return createWithRandomCode(longUrl, expireAt);
    }

    private Link createWithAlias(String longUrl, String alias, Instant expireAt) {
        try{
            return repository.saveAndFlush(new Link(alias, longUrl,expireAt));
        }catch(DataIntegrityViolationException e){
            if(isCodeCollision(e)){
                throw new AliasTakenException(alias);
            }throw e;
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
