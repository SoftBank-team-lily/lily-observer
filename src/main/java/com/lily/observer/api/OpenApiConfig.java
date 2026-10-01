package com.lily.observer.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** /swagger-ui.html 과 /v3/api-docs (OpenAPI 명세). 프론트는 명세로 타입을 만들 수 있다 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI observerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("lily-observer API")
                        .version("v1")
                        .description("배포된 앱의 지표 · 로그 · 상태 조회. 설명 페이지: / (연동 가이드), 시각은 모두 UTC."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("OBSERVABILITY_API_TOKEN. 서버에 토큰이 없으면 비워도 됩니다")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
