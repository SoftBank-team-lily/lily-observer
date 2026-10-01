<h1 align="center">🌷 lily-observer</h1>

<p align="center">
  <b>배포가 잘 됐는지 지켜보고, 문제가 생기면 스스로 되돌리는 Lily 관측 모듈</b><br/>
  SoftBank Hackathon 2026 in Korea 예선 (Term1) · Team Lily
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot_3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java_21-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white" />
  <img src="https://img.shields.io/badge/Fluent_Bit-49BDA5?style=for-the-badge&logo=fluentbit&logoColor=white" />
  <br/>
  <img src="https://img.shields.io/badge/CloudWatch-FF4F8B?style=for-the-badge&logo=amazoncloudwatch&logoColor=white" />
  <img src="https://img.shields.io/badge/DynamoDB-4053D6?style=for-the-badge&logo=amazondynamodb&logoColor=white" />
  <img src="https://img.shields.io/badge/k3s-FFC61C?style=for-the-badge&logo=k3s&logoColor=black" />
</p>

> **작업 중 (WIP)** · 판정 규칙과 지표 조회까지 작성했고, 감시 루프 · 롤백 호출 · 대시보드 API는 개발 중이에요.

---

배포 파이프라인(lily-builder → lily-cicd)은 새 버전이 **뜨는지**까지 확인해요. lily-observer는 그다음, 새 버전이 **제대로 동작하는지**를 지켜봐요.
카나리로 일부 트래픽만 받은 새 버전의 에러율이 기존 버전보다 높아지면, 사람이 보기 전에 lily-cicd에 롤백을 요청해요.

```
lily-cicd 배포 완료 (DeployMonitor 호출)
→ lily-observer 감시 시작
→ 30초마다 canary · stable 에러율 · 응답 시간 비교
→ 위험도 0~3 판정 (같은 등급 연속 2번일 때만 조치)
→ 위험(3)이면 lily-cicd 롤백 API 호출
→ 판정 이력 저장 · 대시보드에 지표 · 로그 · 위험도 제공
```

## 앱이 갖춰야 할 것

| 수집 | 앱 조건 | 없으면 |
|---|---|---|
| 로그 | 없음 (표준 출력만 쓰면 Fluent Bit가 모아요) | - |
| 지표 · 위험도 판정 | `GET /actuator/prometheus` 제공 | 판정 **보류**, 자동 롤백 안 함, 대시보드에 "지표 없음" |

Spring Boot 앱은 두 가지만 있으면 돼요 ([lily-blog-sample](https://github.com/SoftBank-team-lily/lily-blog-sample) 참고).

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

```yaml
management.endpoints.web.exposure.include: health,info,prometheus
```

- 지표 이름은 Spring Boot 기본값 `http_server_requests_seconds`를 써요
- 노출 설정만 있고 라이브러리가 없으면 주소가 생기지 않아요 (Prometheus Targets에 DOWN)

## Architecture

```
[앱 컨테이너 로그]  → Fluent Bit (모든 노드) → CloudWatch Logs ─────┐
[서버 CPU · 메모리] → CloudWatch Agent      → CloudWatch Metrics ──┤
[앱 지표 /actuator/prometheus]                                    │
   파드 라벨 track · color → slot  → Prometheus ─────────────────┐ │
                                                                ▼ ▼
lily-cicd ──(DeployMonitor)──▶  lily-observer  ──(롤백 요청)──▶ lily-cicd
                                 ├ judge   위험도 판정
                                 ├ store   판정 이력 (DynamoDB)
                                 └ api     지표 · 로그 · 위험도 → lily-frontend 대시보드
```

| 구성 | 역할 |
|---|---|
| Prometheus | 파드마다 `/actuator/prometheus`를 수집하고, lily-cicd가 붙인 슬롯 라벨(`track`: stable/canary, `color`: blue/green)을 `slot`으로 합쳐요 |
| Fluent Bit | 모든 노드의 컨테이너 로그를 CloudWatch Logs로 보내요. 파드가 롤백으로 지워져도 로그는 남아요 |
| CloudWatch Agent | 서버 3대의 CPU · 메모리 · 디스크를 보내요 (EC2 기본 지표에는 메모리가 없어요) |
| lily-observer | 판정, 롤백 요청, 이력 저장, 대시보드 API |

## 위험도 판정

| 위험도 | 조건 (canary 기준, 최근 1분) | 조치 | 화면 |
|---|---|---|---|
| 보류 | 요청 20/분 미만 | 판정하지 않음 | 회색 |
| 0 정상 | 5xx 1% 미만 | 없음 | 초록 |
| 1 알림 | 5xx 1% 이상 | 기록 | 초록 |
| 2 주의 | 5xx 2% 이상, 또는 응답 시간이 stable의 2배 이상 | 카나리 중단 권고 | 노랑 |
| 3 위험 | 5xx 5% 이상 | **자동 롤백** | 빨강 |

- 같은 등급이 **연속 2번** 나와야 조치해요 (순간 스파이크 무시)
- 모든 기준은 환경변수로 조정할 수 있어요 (`JUDGE_*`)

## 다른 모듈과의 연결

| 모듈 | 연결 | 상태 |
|---|---|---|
| lily-cicd | 배포 후 `DeployMonitor`로 lily-observer 호출 → 감시 시작 | `HttpDeployMonitor` PR 예정 |
| lily-cicd | 위험 판정 시 `POST /api/deployments/{app}/rollback` 호출 | 개발 중 |
| lily-frontend | 배포 성공 후 꽃 클릭 → 대시보드(`DASHBOARD_URL`)가 아래 API 사용 | 응답 형식 합의 중 |

## API (예정)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/monitors` | lily-cicd가 배포 완료를 알림 → 감시 시작 |
| GET | `/api/apps/{app}/metrics` | 슬롯별 요청 수 · 에러율 · 응답 시간 |
| GET | `/api/apps/{app}/logs` | 최근 컨테이너 로그 (CloudWatch Logs) |
| GET | `/api/apps/{app}/risk` | 현재 위험도와 판정 이력 |
| GET | `/api/nodes` | 서버별 CPU · 메모리 |

## 진행 상황

| 항목 | 상태 |
|---|---|
| 위험도 판정 규칙 (`judge`) | 작성 |
| Prometheus 슬롯별 지표 조회 (`metrics`) | 작성 |
| 감시 대상 모델 (`watch`) | 작성 |
| 감시 루프 · 롤백 호출 | 개발 중 |
| 대시보드 API · 판정 이력 저장 | 개발 중 |
| Prometheus · Fluent Bit · CloudWatch Agent 배포 설정 | 개발 중 |
| 테스트 · Dockerfile · k3s 매니페스트 | 예정 |

## 폴더 구조

```
src/main/java/com/lily/observer/
├─ ObserverApplication.java     진입점 (스케줄링 켜짐)
├─ ObserverProperties.java      observer.* 설정
├─ judge/                       위험도 판정
│  ├─ RiskJudge.java            판정 인터페이스 (규칙 교체 가능)
│  ├─ RuleBasedRiskJudge.java   기본 규칙
│  ├─ RiskLevel.java            0~3 · 보류, 조치, 화면 색
│  └─ Judgment.java             판정 결과
├─ metrics/                     지표 조회
│  ├─ MetricsSource.java
│  ├─ PrometheusMetricsSource.java   PromQL로 슬롯별 요청 · 5xx · 응답 시간
│  └─ SlotMetrics.java
└─ watch/                       감시 대상 (배포 한 건)
   ├─ Watch.java
   └─ WatchState.java
```

## 실행

Java 21이 필요해요.

```bash
./gradlew bootRun
```

- 기본 포트 `8095` (lily-cicd 8090, lily-blog-sample 8080과 겹치지 않게)
- Prometheus 주소: `PROMETHEUS_URL` (기본 `http://prometheus.lily-system.svc:9090`)
- 롤백은 기본으로 꺼져 있어요 (`ROLLBACK_ENABLED=false`). 켜기 전에는 "롤백했을 것"만 기록해요

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `PROMETHEUS_URL` | `http://prometheus.lily-system.svc:9090` | Prometheus 주소 |
| `WATCH_INTERVAL` / `WATCH_WINDOW` | `30s` / `10m` | 판정 주기, 감시 기간 |
| `JUDGE_MIN_REQUESTS` | `20` | 이보다 적으면 판정 보류 |
| `JUDGE_WARNING_ERROR_RATE` / `JUDGE_CRITICAL_ERROR_RATE` | `0.02` / `0.05` | 주의 · 위험 기준 |
| `JUDGE_CONSECUTIVE` | `2` | 조치에 필요한 연속 판정 횟수 |
| `ROLLBACK_ENABLED` | `false` | 실제 롤백 호출 여부 |
| `CICD_URL` | `http://lily-cicd.lily-system.svc` | 롤백 API 주소 |
| `CLOUDWATCH_LOGS_ENABLED` | `false` | CloudWatch Logs 조회 |
| `EVENT_STORE` | `memory` | 판정 이력 저장소 (`memory` / `dynamodb`) |

## Team

| 이름 | 담당 |
|---|---|
| 최도일 | Logging · Monitoring (lily-observer) |
| 심형규 | Logging · Monitoring · Dashboard |
