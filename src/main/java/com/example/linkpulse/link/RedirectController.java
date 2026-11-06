package com.example.linkpulse.link;

import com.example.linkpulse.click.ClickRecorder;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Redirect", description = "Public short-link redirect (open it in a browser tab; Swagger cannot follow a 302)")
@RestController
public class RedirectController {

    private final LinkService service;
    private final ClickRecorder clicks;

    public RedirectController(LinkService service, ClickRecorder clicks) {
        this.service = service;
        this.clicks = clicks;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code,
                                         @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer) {
        String longUrl = service.resolveLongUrl(code);
        // Only reached for a real redirect: 404 and 410 throw above, so they are not counted.
        clicks.record(code, referrer);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}