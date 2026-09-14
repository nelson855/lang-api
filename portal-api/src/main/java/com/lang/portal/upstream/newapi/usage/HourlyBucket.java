package com.lang.portal.upstream.newapi.usage;

import java.time.Instant;

public record HourlyBucket(
    Instant bucketStart, long requestCount, long tokenCount, long quota) {}
