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

> **작업 중 (WIP)** · 수집(Prometheus · Fluent Bit)은 클러스터에 적용했고, 판정 규칙과 지표 조회 코드를 작성했어요. 감시 루프 · 롤백 호출은 lily-cicd와 역할 합의 후 진행해요.

---

lily-cicd는 배포 순간 30초 동안 검사 요청을 보내 새 버전이 바로 터지지 않는지 확인해요. lily-observer는 그다음, 전환된 새 버전이 **실제 사용자 요청을 제대로 처리하는지** 10분 동안 지켜봐요.
에러율이 기준을 넘으면, 사람이 보기 전에 lily-cicd에 롤백을 요청해요.

|         | lily-cicd 카나리 판정    | lily-observer                                      |
| ------- | ------------------------ | -------------------------------------------------- |
| 시점    | 배포 순간 (30초)         | 전환 후 10분                                       |
| 트래픽  | cicd가 보낸 검사 요청    | 실제 사용자 요청                                   |
| 잡는 것 | 바로 터지는 버전         | 시간이 지나야 드러나는 문제 (특정 API 에러, 느려짐) |

```
lily-cicd 배포 완료 (DeployMonitor 호출)
→ lily-observer 감시 시작
→ 30초마다 앱의 에러율 · 응답 시간 확인 (배포 전 같은 앱과 비교)
→ 위험도 0~3 판정 (같은 등급 연속 2번일 때만 조치)
→ 위험(3)이면 lily-cicd 롤백 API 호출
→ 판정 이력 저장 · 대시보드에 지표 · 로그 · 위험도 제공
```

## 앱이 갖춰야 할 것

**없어요.** lily-cicd로 배포한 앱이면 언어 · 프레임워크와 상관없이 로그와 지표가 모여요.

| 수집 | 어디서 | 앱 조건 |
|---|---|---|
| 로그 | 각 서버의 컨테이너 로그 파일 (Fluent Bit) | 표준 출력에 로그를 찍으면 돼요 (컨테이너 앱 기본 동작) |
| 지표 | ingress-nginx 입구 (Prometheus) | 없음. 앱에 닿기 전 입구에서 요청 수 · 응답 코드 · 응답 시간을 세요 |

- 지표를 앱(`/actuator/prometheus`)이 아니라 입구에서 세는 이유: Spring Boot가 아닌 앱(Node, Python 등)도 같은 방식으로 판정하려고요
- 입구에서는 블루그린의 blue · green을 구분하지 못해요. lily-observer는 **전환이 끝난 뒤**를 보기 때문에 앱 단위 숫자로 충분해요

### 클러스터 전제

ingress-nginx v1.12부터 지표 기능이 기본으로 꺼져 있어요. 한 번 켜 둬야 해요 (ingress-nginx가 몇 초 재시작돼요).

```bash
sudo kubectl -n ingress-nginx patch deploy ingress-nginx-controller --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-metrics=true"}]'
```

- 2026-10-01 적용 완료. ingress-nginx를 다시 설치하면 다시 켜야 해요

## Architecture

```
[앱 컨테이너 로그]  → Fluent Bit (모든 노드) → CloudWatch Logs ─────┐
[서버 CPU · 메모리] → CloudWatch Agent      → CloudWatch Metrics ──┤
[사용자 요청] → ingress-nginx (:10254/metrics) → Prometheus ─────┐ │
                                                                ▼ ▼
lily-cicd ──(DeployMonitor)──▶  lily-observer  ──(롤백 요청)──▶ lily-cicd
                                 ├ judge   위험도 판정
                                 ├ store   판정 이력 (DynamoDB)
                                 └ api     지표 · 로그 · 위험도 → lily-frontend 대시보드
```

| 구성 | 역할 |
|---|---|
| Prometheus | ingress-nginx가 센 앱별 요청 수 · 응답 코드 · 응답 시간을 15초마다 모아요 (1일 보관). 앱은 Ingress 이름 `{app}-ingress`로 구분해요 |
| Fluent Bit | 모든 노드의 컨테이너 로그를 CloudWatch Logs `/lily/apps`(7일 보관)로 보내요. 파드마다 로그 스트림 `{namespace}.{app}.{pod}`. 파드가 롤백으로 지워져도 로그는 남아요 |
| CloudWatch Agent | 서버 3대의 CPU · 메모리 · 디스크를 보내요 (EC2 기본 지표에는 메모리가 없어요) |
| lily-observer | 판정, 롤백 요청, 이력 저장, 대시보드 API |

## 위험도 판정

| 위험도 | 조건 (앱, 최근 1분) | 조치 | 화면 |
|---|---|---|---|
| 보류 | 요청 20/분 미만 | 판정하지 않음 | 회색 |
| 0 정상 | 5xx 1% 미만 | 없음 | 초록 |
| 1 알림 | 5xx 1% 이상 | 기록 | 초록 |
| 2 주의 | 5xx 2% 이상, 또는 p95 응답 시간이 기준(15분 전 ~ 5분 전)의 2배 이상 | 주의 알림 | 노랑 |
| 3 위험 | 5xx 5% 이상 | **자동 롤백** | 빨강 |

- 같은 등급이 **연속 2번** 나와야 조치해요 (순간 스파이크 무시)
- 모든 기준은 환경변수로 조정할 수 있어요 (`JUDGE_*`)

## 다른 모듈과의 연결

| 모듈 | 연결 | 상태 |
|---|---|---|
| lily-cicd | 배포 후 `DeployMonitor`로 lily-observer 호출 → 감시 시작 | 역할 합의 후 PR (보류) |
| lily-cicd | 위험 판정 시 `POST /api/deployments/{app}/rollback` 호출 | 역할 합의 후 (보류) |
| lily-frontend | 배포 성공 후 꽃 클릭 → 대시보드(`DASHBOARD_URL`)가 아래 API 사용 | 응답 형식 합의 중 |

## API

메인 주소(`/`)에 파라미터 · 지표 설명과 직접 호출 화면이 있어요.

모든 `/api/**`는 `Authorization: Bearer {OBSERVABILITY_API_TOKEN}`이 필요해요 (토큰이 비어 있으면 인증 꺼짐, 로컬 전용).

| Method | Path | 설명 | 상태 |
|---|---|---|---|
| GET | `/api/apps` | 배포된 앱 목록 (주소, 배포 방식, 활성 슬롯, 파드 수, 이미지) | 구현 |
| GET | `/api/apps/{app}/status` | 패널 색(gray · green · yellow · red) + 한 줄 문구 + 판정 근거 | 구현 |
| GET | `/api/apps/{app}/metrics` | 최근 1분 요청 수 · 에러율 · 평균 · p95 응답 시간 + 30초 간격 추이 (ingress 기준) | 구현 |
| GET | `/api/apps/{app}/pods` | 파드별 Ready · 재시작 · 버전 · 문제(CrashLoop · OOMKilled) · CPU · 메모리 | 구현 |
| GET | `/api/apps/{app}/logs` | 최근 로그. 줄마다 파드 · 슬롯 · 이미지 버전 (CloudWatch Logs) | 구현 |
| GET | `/api/nodes` | 서버별 CPU · 메모리 사용률, Ready, 파드 수 (metrics-server) | 구현 |
| GET | `/api/apps/{app}/risk` | 판정 이력 | 예정 |
| POST | `/api/monitors` | lily-cicd가 배포 완료를 알림 → 감시 시작 | 보류 |

- 전체 파라미터 · 응답 필드 · 연동 가이드: 메인 페이지 `/`
- 바로 호출: Swagger UI `/swagger-ui.html`, OpenAPI 명세 `/v3/api-docs` (프론트 타입 자동 생성 가능)

### `GET /api/apps/{app}/metrics`

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `namespace` | `default` | 앱 네임스페이스 |
| `window` | `10m` | 추이 구간 (`30s`, `10m`, `1h`). 1분 ~ 6시간 |

```json
{
  "app": "lily-test", "namespace": "default",
  "current": { "requestsPerMinute": 42.0, "errorRate": 0.024, "avgLatencyMs": 18.0 },
  "series": [
    { "at": "2026-10-01T06:22:03Z", "requestsPerMinute": 4.0, "errorRate": 1.0, "avgLatencyMs": 3.3 }
  ]
}
```

- `current`: 최근 1분 (카드), `series`: 각 시각 기준 최근 1분 값 (그래프)
- 요청이 없는 앱은 `current`가 모두 0, `series`가 비어 있어요

### `GET /api/apps/{app}/logs`

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `namespace` | `default` | 앱 네임스페이스 |
| `since` | `15m` | 얼마 전부터 (`15m`, `1h`). 최대 7일 |
| `level` | `all` | `error`면 `ERROR` · `Exception` · `panic` · `Traceback`이 들어간 줄만 |
| `limit` | `100` | 최근 것부터 최대 개수 (1 ~ 500). 결과는 오래된 순 |

```json
[
  { "at": "2026-10-01T06:22:49.953Z", "pod": "lily-test-green-7bfdd46749-7hm67", "slot": "green",
    "image": "lily-test:20261001-053104",
    "message": "java.lang.IllegalStateException: chaos: forced application error" }
]
```

- `image`로 어떤 배포에서 난 로그인지 바로 보여요
- 노드 IAM 역할에 `logs:FilterLogEvents` 권한이 필요해요

### 에러 응답

| 코드 | 언제 |
|---|---|
| 400 | 앱 이름 · 기간 형식이 잘못됨 |
| 401 | 토큰이 없거나 다름 |
| 503 | Prometheus · CloudWatch에 닿지 못함, 또는 로그 조회가 꺼져 있음 |

모든 에러 본문은 `{"message": "..."}`.

## 진행 상황

| 항목 | 상태 |
|---|---|
| 위험도 판정 규칙 (`judge`) | 작성 |
| 앱별 지표 조회, ingress 기준 (`metrics`) | 작성 |
| 감시 대상 모델 (`watch`) | 작성 |
| 감시 루프 · 롤백 호출 | 보류 (lily-cicd와 역할 합의) |
| 지표 · 로그 조회 API (`api`, `logs`) | 클러스터에서 동작 확인 |
| 패널 상태 · 파드 · 서버 · 앱 목록 API, p95, Swagger | 구현 (클러스터 반영 전) |
| 판정 이력 저장 | 예정 |
| Prometheus 배포 설정 (`deploy/k3s/prometheus.yaml`, ingress-nginx 수집) | 적용 완료 |
| Fluent Bit 배포 설정 (`deploy/k3s/fluent-bit.yaml`, CloudWatch Logs `/lily/apps`) | 적용 완료 |
| CloudWatch Agent 배포 설정 | 예정 |
| API 설명 페이지 (`/`, 파라미터 · 지표 설명 · 직접 호출) | 구현 |
| Dockerfile · `deploy/k3s/lily-observer.yaml` · `build-image.yaml` | 클러스터 배포 완료 (2026-10-01) |
| 판정 규칙 테스트 | 예정 |

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
├─ api/                         조회 API
│  ├─ AppMetricsController.java GET /api/apps/{app}/metrics
│  ├─ AppLogsController.java    GET /api/apps/{app}/logs
│  ├─ AppStatusController.java  GET /api/apps/{app}/status
│  ├─ ClusterController.java    GET /api/apps, /api/apps/{app}/pods, /api/nodes
│  ├─ OpenApiConfig.java        Swagger · OpenAPI 명세
│  ├─ ApiTokenFilter.java       Bearer 토큰 인증
│  └─ ApiExceptionHandler.java  에러 → {"message"}
├─ cluster/                     쿠버네티스 · metrics-server 조회 (앱 목록, 파드, 서버)
├─ status/                      패널 상태 (판정 규칙으로 색 · 문구)
├─ logs/                        로그 조회
│  ├─ LogSource.java
│  ├─ CloudWatchLogSource.java  /lily/apps 에서 앱 스트림만, 파드 · 슬롯 · 이미지 추출
│  └─ LogsConfig.java           CLOUDWATCH_LOGS_ENABLED=false 면 AWS 에 붙지 않음
├─ metrics/                     지표 조회
│  ├─ MetricsSource.java
│  ├─ PrometheusMetricsSource.java   PromQL로 앱별 요청 · 5xx · 응답 시간 (ingress-nginx 지표)
│  ├─ TrafficMetrics.java       구간 요약
│  └─ TrafficPoint.java         추이 한 점
└─ watch/                       감시 대상 (배포 한 건)
   ├─ Watch.java
   └─ WatchState.java

src/main/resources/static/index.html   API 설명 페이지 (/)

deploy/k3s/
├─ prometheus.yaml              ingress-nginx 지표 수집 (lily-system, worker 1개)
├─ fluent-bit.yaml              앱 로그 → CloudWatch Logs (노드마다 1개)
├─ lily-observer.yaml           조회 API 서버 (lily-system, server 노드, ClusterIP)
└─ build-image.yaml             이미지 빌드 일회성 Job (Kaniko → ECR)
```

적용은 서버에서 `sudo kubectl apply -f deploy/k3s/{파일}.yaml`.

## 배포

1. ECR에 `lily-observer` 저장소 (한 번만)
2. 이미지 빌드: GitHub 토큰 Secret → `build-image.yaml` (Kaniko가 main을 빌드해 `lily-observer:latest`로 push) → Secret 삭제
3. `lily-server-role`에 `logs:FilterLogEvents` 인라인 정책 (`/lily/apps`만, 한 번만)
4. `sudo kubectl apply -f deploy/k3s/lily-observer.yaml`

새 코드를 반영할 때는 2번 후 `sudo kubectl -n lily-system rollout restart deploy/lily-observer` (`imagePullPolicy: Always`).
매니페스트의 `<ACCOUNT_ID>`는 적용 전에 계정 번호로 바꾼다.

확인:

```bash
ssh -i ~/.ssh/lily-key.pem -L 8095:localhost:8095 ubuntu@43.200.152.53
sudo kubectl -n lily-system port-forward svc/lily-observer 8095:80
# 브라우저 http://localhost:8095
```

## 실행

Java 21이 필요해요.

```bash
./gradlew bootRun
```

- 기본 포트 `8095` (lily-cicd 8090, lily-blog-sample 8080과 겹치지 않게)
- Prometheus 주소: `PROMETHEUS_URL` (기본 `http://prometheus.lily-system.svc:9090`)
- 롤백은 기본으로 꺼져 있어요 (`ROLLBACK_ENABLED=false`). 켜기 전에는 "롤백했을 것"만 기록해요
- 로컬에서 실제 지표를 보려면 Prometheus를 터널로 연결해요 (`ssh -L 9090:localhost:9090` + 서버에서 `port-forward svc/prometheus 9090:9090`)

```bash
PROMETHEUS_URL=http://localhost:9090 ./gradlew bootRun
curl 'localhost:8095/api/apps/lily-test/metrics?window=30m'
```

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `PROMETHEUS_URL` | `http://prometheus.lily-system.svc:9090` | Prometheus 주소 |
| `INGRESS_NAME` | `%s-ingress` | lily-cicd의 Ingress 이름 규칙 (`%s` = 앱 이름) |
| `WATCH_INTERVAL` / `WATCH_WINDOW` | `30s` / `10m` | 판정 주기, 감시 기간 |
| `JUDGE_MIN_REQUESTS` | `20` | 이보다 적으면 판정 보류 |
| `JUDGE_WARNING_ERROR_RATE` / `JUDGE_CRITICAL_ERROR_RATE` | `0.02` / `0.05` | 주의 · 위험 기준 |
| `JUDGE_CONSECUTIVE` | `2` | 조치에 필요한 연속 판정 횟수 |
| `ROLLBACK_ENABLED` | `false` | 실제 롤백 호출 여부 |
| `CICD_URL` | `http://lily-cicd.lily-system.svc` | 롤백 API 주소 |
| `OBSERVABILITY_API_TOKEN` | (비어 있음) | `/api/**` Bearer 토큰. 비어 있으면 인증 꺼짐 (로컬 전용) |
| `CLOUDWATCH_LOGS_ENABLED` | `false` | CloudWatch Logs 조회. false면 로그 API가 503 |
| `LOG_GROUP` | `/lily/apps` | Fluent Bit이 보내는 로그 그룹 |
| `EVENT_STORE` | `memory` | 판정 이력 저장소 (`memory` / `dynamodb`) |

## Team

| 이름 | 담당 |
|---|---|
| 최도일 | Logging · Monitoring (lily-observer) |
| 심형규 | Logging · Monitoring · Dashboard |
