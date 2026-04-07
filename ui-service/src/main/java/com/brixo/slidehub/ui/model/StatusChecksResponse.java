package com.codebymike.slidehub.ui.model;

import java.time.Instant;
import java.util.List;

public record StatusChecksResponse(
        Instant generatedAt,
        List<StatusCheckItem> checks) {
}
