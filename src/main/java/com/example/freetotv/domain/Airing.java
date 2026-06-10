package com.example.freetotv.domain;

import java.time.OffsetDateTime;

/**
 * A specific broadcast of a title: when it airs, on which channel, and which episode.
 *
 * <p>{@code dayLabel} and {@code time} are pre-formatted for easy scanning (e.g. "Today" / "21:00"),
 * while {@code start} is retained for ordering and machine consumers.
 *
 * @param channel      the broadcast network/channel name (e.g. "ABC")
 * @param start        the start time of the airing, in the country's local zone (may be null)
 * @param dayLabel     scannable day label: "Today", "Tomorrow", or e.g. "Wed 11 Jun"
 * @param time         scannable start time, e.g. "21:00" (null if unknown)
 * @param episodeName  the episode title, if any
 * @param season       the season number, if applicable
 * @param episode      the episode number, if applicable
 * @param runtimeMins  the runtime in minutes, if known
 */
public record Airing(
        String channel,
        OffsetDateTime start,
        String dayLabel,
        String time,
        String episodeName,
        Integer season,
        Integer episode,
        Integer runtimeMins) {
}
