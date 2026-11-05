package com.example.linkpulse.link;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService service;
    private final String baseUrl;

    public LinkController(LinkService service, @Value("${linkpulse.base-url}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    @PostMapping
    public ResponseEntity<CreateLinkResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody CreateLinkRequest request) {
        Link link = service.create(userId(jwt), request.url(), request.alias(), request.expiresAt());
        String shortUrl = shortUrl(link);
        return ResponseEntity
                .created(URI.create(shortUrl))
                .body(new CreateLinkResponse(link.getCode(), shortUrl));
    }

    @GetMapping
    public List<LinkSummary> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwned(userId(jwt)).stream()
                .map(link -> new LinkSummary(link.getCode(), shortUrl(link), link.getLongUrl(),
                        link.getCreatedAt(), link.getExpiresAt()))
                .toList();
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        service.delete(userId(jwt), code);
        return ResponseEntity.noContent().build();
    }

    private String shortUrl(Link link) {
        return baseUrl + "/" + link.getCode();
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}