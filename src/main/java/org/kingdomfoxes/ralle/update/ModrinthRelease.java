package org.kingdomfoxes.ralle.update;

import java.net.URI;

/** Public release metadata; links are built locally, never taken from API-supplied URLs. */
public record ModrinthRelease(String id, String name, String versionNumber) {
    public URI changelogUrl() {
        return URI.create("https://modrinth.com/project/ralle/version/" + id);
    }
}
