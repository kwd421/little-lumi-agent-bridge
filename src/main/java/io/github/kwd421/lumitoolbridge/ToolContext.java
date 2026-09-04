package io.github.kwd421.lumitoolbridge;

import java.time.Instant;

public record ToolContext(String authorization, Instant startedAt, RequestContext request) {}
