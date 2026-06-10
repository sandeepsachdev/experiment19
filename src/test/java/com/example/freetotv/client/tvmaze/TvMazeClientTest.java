package com.example.freetotv.client.tvmaze;

import java.time.LocalDate;
import java.util.List;

import com.example.freetotv.client.tvmaze.dto.ScheduleEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class TvMazeClientTest {

    private static final String SCHEDULE_JSON = """
            [
              {
                "id": 1,
                "name": "The Pilot",
                "season": 1,
                "number": 1,
                "airstamp": "2026-06-10T20:00:00+01:00",
                "runtime": 60,
                "show": {
                  "id": 42,
                  "name": "Brilliant Drama",
                  "type": "Scripted",
                  "genres": ["Drama", "Thriller"],
                  "rating": { "average": 8.6 },
                  "network": { "id": 12, "name": "BBC One", "country": { "name": "United Kingdom", "code": "GB" } },
                  "summary": "<p>A <b>gripping</b> tale.</p>",
                  "image": { "medium": "http://img/m.jpg", "original": "http://img/o.jpg" },
                  "externals": { "imdb": "tt1234567" }
                }
              }
            ]
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TvMazeClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.tvmaze.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TvMazeClient(builder.build());
    }

    @Test
    void parsesScheduleResponse() {
        server.expect(requestTo(startsWith("https://api.tvmaze.com/schedule")))
                .andRespond(withSuccess(SCHEDULE_JSON, MediaType.APPLICATION_JSON));

        List<ScheduleEntry> entries = client.getSchedule("GB", LocalDate.of(2026, 6, 10));

        assertThat(entries).hasSize(1);
        ScheduleEntry entry = entries.get(0);
        assertThat(entry.show().name()).isEqualTo("Brilliant Drama");
        assertThat(entry.show().rating().average()).isEqualTo(8.6);
        assertThat(entry.show().network().name()).isEqualTo("BBC One");
        assertThat(entry.show().genres()).containsExactly("Drama", "Thriller");
        assertThat(entry.show().externals().imdb()).isEqualTo("tt1234567");
        assertThat(entry.airstamp()).isEqualTo("2026-06-10T20:00:00+01:00");
        server.verify();
    }

    @Test
    void returnsEmptyListOnUpstreamError() {
        server.expect(requestTo(startsWith("https://api.tvmaze.com/schedule")))
                .andRespond(withServerError());

        List<ScheduleEntry> entries = client.getSchedule("GB", LocalDate.of(2026, 6, 10));

        assertThat(entries).isEmpty();
    }
}
