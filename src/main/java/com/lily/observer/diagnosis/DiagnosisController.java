package com.lily.observer.diagnosis;

import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/apps/{app}/diagnosis")
public class DiagnosisController {
    static final String NAME = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Diagnosis.Result> diagnose(@PathVariable @Pattern(regexp = NAME) String app,
                                                    @RequestParam(defaultValue = "default") @Pattern(regexp = NAME) String namespace) {
        var result = service.diagnose(namespace, app);
        int status = switch (result.state()) {
            case "ready" -> 200;
            case "busy" -> 429;
            default -> 503;
        };
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(result);
    }
}
