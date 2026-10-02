package com.lily.observer.remediate;

import com.lily.observer.logs.LogSource;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 런타임 로그로 코드 사고 초안을 만든다. {@code observer.remediate.enabled} 가 아니면
 * 로그를 읽지 않고 바로 꺼져 있음을 돌려준다. 주기 감시도 같은 {@link RemediateSender} 를 쓴다.
 */
@RestController
@RequestMapping("/api/apps/{app}")
public class RemediateController {

    private static final String NAME = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";

    private final RemediateSender sender;

    public RemediateController(RemediateSender sender) {
        this.sender = sender;
    }

    @PostMapping(value = "/remediate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> remediate(@PathVariable @Pattern(regexp = NAME) String app,
                                          @RequestParam(defaultValue = "default") @Pattern(regexp = NAME)
                                          String namespace) {
        return sender.send(namespace, app);
    }
}
