package com.example.freetotv.domain;

import java.time.OffsetDateTime;

/**
 * A specific broadcast of a title: when it airs, on which channel, and which episode.
 *
 * @param channel      the broadcast network/channel name (e.g. "BBC One")
 * @param start        the start time of the airing (may be null if TVmaze omits it)
 * @param episodeName  the episode title, if any
 * @param season       the season number, if applicable
 * @param episode      the episode number, if applicable
 * @param runtimeMins  the runtime in minutes, if known
 */
public record Airing(
        String channel,
        OffsetDateTime start,
        String episodeName,
        Integer season,
        Integer episode,
        Integer runtimeMins) {
}
