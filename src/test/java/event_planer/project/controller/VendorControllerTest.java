package event_planer.project.controller;

import event_planer.project.dto.VendorResponse;
import event_planer.project.service.VendorService;
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
class VendorControllerTest {

    @Mock private VendorService vendorService;
    @InjectMocks private VendorController vendorController;

    @Test
    void singleOptionDelegatesToGetVendors() {
        VendorResponse vendor = new VendorResponse(1L, "Caterer A", "Berlin", 52.5, 13.4,
                "Caterer", "Catering", null, null, null);
        when(vendorService.getVendors("Berlin", 5000, "Catering"))
                .thenReturn(List.of(vendor));

        ResponseEntity<List<VendorResponse>> response =
                vendorController.getVendors("Berlin", List.of("Catering"), 5000);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(vendorService).getVendors("Berlin", 5000, "Catering");
        verify(vendorService, never()).getVendorsForMultipleOptions(any(), anyInt(), any());
    }

    @Test
    void multipleOptionsDelegatesToGetVendorsForMultipleOptions() {
        List<String> options = List.of("Catering", "Photography");
        when(vendorService.getVendorsForMultipleOptions("Berlin", 5000, options))
                .thenReturn(List.of());

        ResponseEntity<List<VendorResponse>> response =
                vendorController.getVendors("Berlin", options, 5000);

        verify(vendorService).getVendorsForMultipleOptions("Berlin", 5000, options);
        verify(vendorService, never()).getVendors(any(), anyInt(), any());
    }
}
