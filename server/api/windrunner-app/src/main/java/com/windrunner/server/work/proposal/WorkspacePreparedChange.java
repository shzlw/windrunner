package com.windrunner.server.work.proposal;

public record WorkspacePreparedChange(
        String payloadJson,
        String previousJson,
        String baseVersionJson) {
}
