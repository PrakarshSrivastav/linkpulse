package com.example.linkpulse.click;

import java.time.Instant;

record Click(String code, Instant clickedAt, String referrer) {}