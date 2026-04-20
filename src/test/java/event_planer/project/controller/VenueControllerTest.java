package event_planer.project.controller;

import event_planer.project.dto.VenueResponse;
import event_planer.project.service.VenueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueControllerTest {

    @Mock private VenueService venueService;
    @InjectMocks private VenueController venueController;

    @Test
    void delegatesToServiceWithAllParams() {
        VenueResponse venue = new VenueResponse(1L, "Test Venue", "Berlin", 52.5, 13.4,
                "Conference Centre", null, null, null);
        when(venueService.getVenues("Berlin", 3000, "INDOOR", "conference"))
                .thenReturn(List.of(venue));

        ResponseEntity<List<VenueResponse>> response =
                venueController.getVenues("Berlin", 3000, "INDOOR", "conference");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Test Venue");
    }

    @Test
    void passesNullForOptionalParams() {
        when(venueService.getVenues("Berlin", 5000, null, null))
                .thenReturn(List.of());

        ResponseEntity<List<VenueResponse>> response =
                venueController.getVenues("Berlin", 5000, null, null);

        assertThat(response.getBody()).isEmpty();
    }
}
