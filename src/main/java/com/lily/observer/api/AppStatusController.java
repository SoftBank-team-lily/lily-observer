package com.lily.observer.api;

import com.lily.observer.status.AppStatus;
import com.lily.observer.status.AppStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드 패널 색 · 문구. 판정 규칙과 같은 기준을 백엔드에서 계산한다 */
@Tag(name = "상태", description = "패널 색(Green / Yellow / Red / Gray)과 한 줄 문구")
@Validated
@RestController
@RequestMapping("/api/apps/{app}")
public class AppStatusController {

    private final AppStatusService statuses;

    public AppStatusController(AppStatusService statuses) {
        this.statuses = statuses;
    }

    @Operation(summary = "앱 패널 상태",
            description = "최근 1분 지표로 위험도를 판정해 색과 문구를 돌려줍니다. 화면은 color 와 message 를 그대로 쓰면 됩니다. "
                    + "요청 20회/분 미만이면 gray(보류), 5xx 5% 이상이면 red.")
    @GetMapping("/status")
    public AppStatus status(
            @Parameter(description = "배포할 때 쓴 앱 이름", example = "lily-test")
            @PathVariable @Pattern(regexp = AppMetricsController.NAME) String app,
            @Parameter(description = "앱 네임스페이스")
            @RequestParam(defaultValue = "default") @Pattern(regexp = AppMetricsController.NAME) String namespace) {
        return statuses.status(namespace, app);
    }
}
