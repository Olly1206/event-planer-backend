package event_planer.project.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EventVendorRequest {

    @NotNull(message = "Vendor osmId is required")
    private Long osmId;

    @NotBlank(message = "Vendor name is required")
    @Size(max = 200, message = "Vendor name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Vendor address must not exceed 1000 characters")
    private String address;

    @Size(max = 120, message = "Vendor category must not exceed 120 characters")
    private String category;

    @Size(max = 120, message = "Vendor option name must not exceed 120 characters")
    private String optionName;

    @Size(max = 255, message = "Website must not exceed 255 characters")
    private String website;

    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 80, message = "Phone must not exceed 80 characters")
    private String phone;
}
