package com.example.linkpulse.link;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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
    public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = service.create(request.url(), request.alias(), request.expiresAt());
        String shortUrl = baseUrl + "/" + link.getCode();
        return ResponseEntity
                .created(URI.create(shortUrl))
                .body(new CreateLinkResponse(link.getCode(), shortUrl));
    }
}