package com.example.linkpulse.web;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Serves the landing page at "/". It is not in /static on purpose: RedirectController maps
// GET /{code}, which would also capture /index.html or /style.css. An exact "/" mapping is
// unambiguous, and the page inlines its CSS so it needs no other single-segment URLs.
@Hidden
@RestController
public class HomeController {

    private static final Resource HOME = new ClassPathResource("web/index.html");

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Resource home() {
        return HOME;
    }
}
