<h1 align="center">🌷 lily-observer</h1>

<p align="center">
  <b>배포된 앱을 지켜보고, 상태 · 지표 · 로그를 한 곳에서 보여주는 Lily 관측 모듈</b><br/>
  SoftBank Hackathon 2026 in Korea 예선 (Term1) · Team Lily
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot_3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java_21-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white" />
  <img src="https://img.shields.io/badge/Fluent_Bit-49BDA5?style=for-the-badge&logo=fluentbit&logoColor=white" />
  <img src="https://img.shields.io/badge/ingress--nginx-009639?style=for-the-badge&logo=nginx&logoColor=white" />
  <br/>
  <img src="https://img.shields.io/badge/CloudWatch_Logs-FF4F8B?style=for-the-badge&logo=amazoncloudwatch&logoColor=white" />
  <img src="https://img.shields.io/badge/k3s-FFC61C?style=for-the-badge&logo=k3s&logoColor=black" />
  <img src="https://img.shields.io/badge/Amazon_ECR-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white" />
  <img src="https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black" />
</p>

> **상태** · 수집과 조회 API는 클러스터에서 동작해요 (2026-10-01). `RemediateWatch`가 `WATCH_INTERVAL`마다 판정하고, CRITICAL이 연속이면 로그 사고를 프론트로 보내요. 롤백 호출은 아직 없어요.

---

## 소개

Lily에서 앱을 배포하면 lily-cicd가 새 버전을 띄우고, 30초 동안 검사 요청을 보내 바로 터지지 않는지 확인한 뒤 트래픽을 넘겨요. 그런데 어떤 문제는 실제 사용자 요청이 들어와야 드러나요. 특정 API에서만 나는 에러, 시간이 지나며 느려지는 응답 같은 것들이에요.

lily-observer는 그 이후를 맡아요. 모든 앱의 요청 지표와 로그를 **앱 코드를 고치지 않고** 플랫폼 바깥에서 모으고, 앱별로 "지금 괜찮은지"를 색과 한 줄 문구로 알려줘요. 대시보드와 AI 진단은 이 모듈의 API 하나로 앱 상태, 지표, 파드, 로그, 서버 정보를 가져가요.

CloudWatch만 쓰면 숫자와 로그를 보여주고 알람을 울리는 데서 끝나요. lily-observer는 배포를 아는 플랫폼 안에 있어서, **어떤 배포 버전에서 어떤 에러가 났는지**를 묶어서 보여주고 같은 기준으로 위험도를 판정해요.

## 핵심 기능

| 기능 | 설명 |
|---|---|
| 언어 무관 요청 지표 | 앱이 아니라 입구(ingress-nginx)에서 요청 수 · 5xx 비율 · 평균 · p95 응답 시간을 세요. Spring, Node, Python 모두 같은 방식이에요 |
| 배포 버전이 붙은 로그 | 로그마다 파드 · 슬롯(blue/green) · 이미지 버전이 붙어요. 롤백으로 파드가 지워져도 7일간 남아요 |
| 패널 상태 | 위험도 규칙으로 gray · green · yellow · red와 화면에 그대로 쓸 문구를 백엔드가 계산해요 |
| 파드 · 서버 상태 | Ready, 재시작 수, CrashLoopBackOff · OOMKilled, 파드 · 서버별 CPU · 메모리 |
| 앱 목록 | 배포된 앱, 주소, 배포 방식, 지금 트래픽을 받는 슬롯과 이미지 |
| 바로 쓰는 문서 | 설명 페이지(`/`), Swagger UI, OpenAPI 명세 (프론트 타입 자동 생성) |

## 동작 방식

```
[사용자 요청] ─▶ ingress-nginx (:10254/metrics) ─▶ Prometheus ─────────────┐
[앱 로그]     ─▶ Fluent Bit (서버마다 1개)       ─▶ CloudWatch Logs ────────┤
[파드 · 서버] ─▶ 쿠버네티스 API + metrics-server ───────────────────────────┤
                                                                           ▼
                                                       lily-observer (lily-system)
                                                       ├ metrics  요청 지표 (PromQL)
                                                       ├ logs     앱 로그 (CloudWatch)
                                                       ├ cluster  앱 · 파드 · 서버
                                                       ├ judge    위험도 판정
                                                       └ api      REST API · Swagger
                                                                           │
                                     대시보드 (lily-frontend 연동) · AI 진단 ◀─┘
```

lily-observer는 데이터를 직접 쌓지 않아요. API 요청이 오면 그때 Prometheus, CloudWatch Logs, 쿠버네티스에 물어보고 결과를 앱 단위로 정리해서 돌려줘요. 수집은 클러스터에 따로 띄운 Prometheus와 Fluent Bit이 맡고, 이 서버는 조회와 판정만 해요. 그래서 이 서버가 재시작돼도 지표와 로그는 사라지지 않아요.

## 기술 스택

| 영역 | 기술 | 버전 | 용도 |
|---|---|---|---|
| 서버 | Spring Boot | 3.4.4 | REST API, 설정, 스케줄링 |
| 언어 | Java | 21 | |
| 빌드 | Gradle | 8.12 (Docker) | `bootJar` → `app.jar` |
| 지표 수집 | Prometheus | v2.55.1 | ingress-nginx 지표 15초마다 수집, 1일 보관 |
| 지표 출처 | ingress-nginx | v1.12.1 | 앱별 요청 수 · 응답 코드 · 응답 시간 (`--enable-metrics=true`) |
| 로그 수집 | Fluent Bit | 3.2.10 | 컨테이너 로그 → CloudWatch Logs (DaemonSet) |
| 로그 저장 | Amazon CloudWatch Logs | | 로그 그룹 `/lily/apps`, 7일 보관 |
| 클러스터 조회 | fabric8 kubernetes-client | 6.13.5 | 앱 · 파드 · 서버, metrics-server CPU · 메모리 |
| AWS SDK | AWS SDK for Java v2 | 2.29.0 | CloudWatch Logs 조회, DynamoDB (판정 이력, 예정) |
| API 문서 | springdoc-openapi | 2.8.6 | Swagger UI, OpenAPI 3 명세 |
| 런타임 | k3s | | lily-system 네임스페이스, server 노드 |
| 이미지 | Kaniko → Amazon ECR | v1.23.2 | 클러스터 안에서 빌드 (`build-image.yaml`) |
| 테스트 | JUnit 5, AssertJ | | 판정 규칙, PromQL 조회, 로그 · 쿠버네티스 변환 |

## 모듈별 기능

### 수집 (클러스터 설정, `deploy/k3s/`)

| 파일 | 하는 일 |
|---|---|
| `prometheus.yaml` | ingress-nginx 컨트롤러의 `:10254/metrics`를 15초마다 수집해요. 판정 · 대시보드에 쓰는 지표(`requests`, `request_duration_seconds`)만 저장하고, worker에 1개 떠요 |
| `fluent-bit.yaml` | 서버 3대에 하나씩 떠서 `/var/log/containers`를 읽어요. 슬롯 라벨(`track` · `color`)이 있는 앱 파드 로그만 골라 파드 정보를 붙이고, 스트림 `{namespace}.{app}.{pod}`로 보내요 |
| `lily-observer.yaml` | 조회 서버, 조회 전용 ClusterRole, Service(ClusterIP) |
| `build-image.yaml` | GitHub main을 Kaniko로 빌드해 ECR에 올리는 일회성 Job |

### 서버 (`src/main/java/com/lily/observer/`)

| 패키지 | 하는 일 |
|---|---|
| `api` | REST 컨트롤러, Bearer 토큰 필터, 에러 응답(`{"message"}`), OpenAPI 설정 |
| `metrics` | Prometheus에 PromQL로 앱별 요청 수 · 5xx 비율 · 평균 · p95를 물어봐요. 앱은 Ingress 이름 `{app}-ingress`로 구분하고, 과거 시점 조회(배포 전 비교)와 30초 간격 추이를 지원해요 |
| `logs` | CloudWatch Logs에서 앱 스트림만 골라 최근 로그를 읽어요. Fluent Bit 레코드에서 파드 · 슬롯 · 이미지 버전을 꺼내고, JSON 로그는 `message` · `msg` 필드를 메시지로 써요 |
| `cluster` | 쿠버네티스 API로 앱 목록 · 파드 상태 · 서버 상태를 만들고, metrics-server에서 CPU · 메모리를 붙여요 |
| `judge` | 위험도 규칙(`RiskJudge`). 요청 수 · 에러율 · p95로 보류 · 정상 · 알림 · 주의 · 위험을 정해요. 규칙은 인터페이스라 교체할 수 있어요 |
| `status` | 최근 1분 지표와 기준(15분 전 ~ 5분 전)을 판정 규칙에 넣어 패널 색과 문구를 만들어요 |
| `watch` | 배포 한 건의 감시 모델. 자동 판정 루프용이에요 (보류) |

## API

메인 주소 `/`에 연동 가이드, 파라미터, 응답 필드, 직접 호출이 있어요. Swagger UI는 `/swagger-ui.html`, OpenAPI 명세는 `/v3/api-docs`예요.

| Method | Path | 설명 | 출처 |
|---|---|---|---|
| GET | `/api/apps` | 배포된 앱 목록 (주소, 배포 방식, 활성 슬롯, 파드 수, 이미지) | 쿠버네티스 |
| GET | `/api/apps/{app}/status` | 패널 색 · 한 줄 문구 · 판정 근거 | Prometheus + 판정 규칙 |
| GET | `/api/apps/{app}/metrics` | 최근 1분 요청 수 · 에러율 · 평균 · p95 + 30초 간격 추이 | Prometheus |
| GET | `/api/apps/{app}/pods` | 파드별 Ready · 재시작 · 버전 · 문제 · CPU · 메모리 | 쿠버네티스 + metrics-server |
| GET | `/api/apps/{app}/logs` | 최근 로그 (파드 · 슬롯 · 이미지 버전) | CloudWatch Logs |
| GET | `/api/nodes` | 서버별 CPU · 메모리 사용률, Ready, 파드 수 | 쿠버네티스 + metrics-server |
| GET | `/api/apps/{app}/risk` | 판정 이력 | 예정 |
| POST | `/api/monitors` | lily-cicd 배포 완료 알림 → 자동 감시 | 보류 |

- `{app}`은 배포할 때 쓴 앱 이름이에요 (예: `lily-test`). 공통 쿼리 `namespace`는 기본 `default`
- 토큰이 설정되면 `/api/**`에 `Authorization: Bearer {OBSERVABILITY_API_TOKEN}`이 필요해요
- 에러: 400 잘못된 값, 401 토큰, 503 Prometheus · CloudWatch · 쿠버네티스에 닿지 못함
- 대시보드는 브라우저가 아니라 서버에서 `http://lily-observer.lily-system.svc`로 불러요 (ClusterIP, CORS 없음)

`GET /api/apps/lily-test/status` 응답 예시:

```json
{
  "app": "lily-test", "namespace": "default",
  "level": "CRITICAL", "score": 3, "color": "red", "action": "롤백",
  "message": "위험: 에러율 6.2%. 이전 버전으로 되돌리는 것을 권장해요",
  "reason": "5xx 6.2% ≥ 5.0%",
  "current":  { "requestsPerMinute": 60.0, "errorRate": 0.062, "avgLatencyMs": 10.0, "p95LatencyMs": 20.0 },
  "baseline": { "requestsPerMinute": 100.0, "errorRate": 0.0, "avgLatencyMs": 10.0, "p95LatencyMs": 20.0 },
  "judgedAt": "2026-10-01T07:04:08Z"
}
```

## 위험도 판정

| 위험도 | 조건 (최근 1분) | 색 | 조치 |
|---|---|---|---|
| 보류 (-1) | 요청 20회/분 미만 | gray | 판정하지 않음 |
| 정상 (0) | 5xx 1% 미만 | green | 없음 |
| 알림 (1) | 5xx 1% 이상 | green | 기록 |
| 주의 (2) | 5xx 2% 이상, 또는 p95가 기준(15분 전 ~ 5분 전)의 2배 이상 | yellow | 주의 알림 |
| 위험 (3) | 5xx 5% 이상 | red | 롤백 권장 (자동 롤백은 역할 합의 후) |

- 요청이 적으면 비율이 크게 튀어서 판정을 보류해요
- 4xx는 사용자 쪽 문제(잘못된 주소, 권한 없음)라 에러로 세지 않아요
- 느린 요청은 평균에 묻혀서 응답 시간은 p95로 비교해요
- `RemediateWatch`는 CRITICAL이 연속 2번이고 `observer.remediate.enabled`일 때 로그 사고를 한 번 보내요. 롤백은 하지 않아요
- 기준값은 환경변수(`JUDGE_*`)로 바꿀 수 있어요

## 다른 모듈과의 관계

| 모듈 | 관계 | 상태 |
|---|---|---|
| lily-cicd | 배포 순간 30초 카나리 판정은 cicd, 전환 후 실제 트래픽 감시는 lily-observer | 역할 합의 중 |
| lily-cicd | 위험 판정 시 `POST /api/deployments/{app}/rollback` 호출 | 보류 |
| lily-frontend · 대시보드 | 꽃 클릭 → 대시보드(`DASHBOARD_URL`)가 이 API로 앱 상태를 보여줌 | 응답 형식 합의 중 |
| lily-builder | 같은 Kaniko 방식으로 이미지 빌드, 같은 ECR 인증(`ecr-pull`) 사용 | 사용 중 |

## 앱 조건과 클러스터 전제

**앱 조건은 없어요.** lily-cicd로 배포하고 표준 출력에 로그를 찍으면 지표와 로그가 모여요.

| 수집 | 조건 | 안 되는 경우 |
|---|---|---|
| 요청 지표 · 상태 | lily-cicd가 만든 `{app}-ingress`를 지나는 요청 | lily-cicd 밖에서 만든 앱 |
| 앱 목록 · 파드 | 파드 라벨 `app` + `track` 또는 `color` | 라벨 규칙이 다른 앱 |
| 로그 | 앱이 stdout · stderr에 로그를 찍음 | 컨테이너 안 파일에만 쓰는 앱 |

클러스터에는 한 번만 해 두면 되는 설정이 있어요.

| 전제 | 이유 | 상태 |
|---|---|---|
| ingress-nginx `--enable-metrics=true` | v1.12부터 기본으로 꺼져 있음. 다시 설치하면 다시 켜야 함 | 적용 (2026-10-01) |
| CloudWatch 로그 그룹 `/lily/apps` (보존 7일) | Fluent Bit은 그룹을 만들지 않음 | 생성 |
| 노드 IAM 역할에 `CloudWatchAgentServerPolicy` | Fluent Bit 로그 전송 | 연결됨 |
| `lily-server-role`에 `logs:FilterLogEvents` (`/lily/apps`만) | 로그 조회 API | 인라인 정책 `lily-observer-logs-read` |
| ECR 저장소 `lily-observer` | 이미지 저장 | 생성 |

ingress-nginx 지표 켜기:

```bash
sudo kubectl -n ingress-nginx patch deploy ingress-nginx-controller --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-metrics=true"}]'
```

## 실행 방법

### 로컬

Java 21이 필요해요.

```bash
./gradlew bootRun          # http://localhost:8095
./gradlew test             # 테스트 24개
```

로컬에는 Prometheus · CloudWatch · 쿠버네티스가 없어서 조회 API는 503이 나와요. 설명 페이지와 Swagger는 열려요.

실제 지표를 보려면 클러스터의 Prometheus를 터널로 연결해요.

```bash
# 내 노트북
ssh -i ~/.ssh/lily-key.pem -L 9090:localhost:9090 ubuntu@43.200.152.53
# 서버
sudo kubectl -n lily-system port-forward svc/prometheus 9090:9090
# 내 노트북 (다른 탭)
PROMETHEUS_URL=http://localhost:9090 ./gradlew bootRun
curl 'localhost:8095/api/apps/lily-test/status'
```

### 클러스터에 처음 배포

1. 위 "클러스터 전제"를 준비해요
2. 수집 도구를 올려요
   ```bash
   sudo kubectl apply -f deploy/k3s/prometheus.yaml
   sudo kubectl apply -f deploy/k3s/fluent-bit.yaml
   ```
3. 이미지를 빌드해요 (private 레포라 GitHub 토큰이 필요해요)
   ```bash
   sed -i 's/<ACCOUNT_ID>/{계정 번호}/' deploy/k3s/build-image.yaml deploy/k3s/lily-observer.yaml
   sudo kubectl -n lily-builds create secret generic lily-observer-git \
     --from-literal=GIT_USERNAME=x-access-token --from-literal=GIT_PASSWORD={GitHub 토큰}
   sudo kubectl apply -f deploy/k3s/build-image.yaml
   sudo kubectl -n lily-builds logs -f job/lily-observer-build      # "Pushed" 확인
   sudo kubectl -n lily-builds delete secret lily-observer-git
   ```
4. 서버를 올려요
   ```bash
   sudo kubectl apply -f deploy/k3s/lily-observer.yaml
   sudo kubectl -n lily-system rollout status deploy/lily-observer
   ```

### 새 코드 반영

```bash
sudo kubectl -n lily-builds delete job lily-observer-build
# 3번의 토큰 Secret → build-image.yaml → "Pushed" 확인 → Secret 삭제
sudo kubectl -n lily-system rollout restart deploy/lily-observer      # imagePullPolicy: Always
```

### 확인

```bash
# 내 노트북
ssh -i ~/.ssh/lily-key.pem -L 8095:localhost:8095 ubuntu@43.200.152.53
# 서버 (이전 연결이 남아 있으면 먼저 끄기: sudo pkill -f "port-forward svc/lily-observer")
sudo kubectl -n lily-system port-forward svc/lily-observer 8095:80
# 브라우저 http://localhost:8095 , http://localhost:8095/swagger-ui.html
```

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SERVER_PORT` | `8095` | 서버 포트 |
| `PROMETHEUS_URL` | `http://prometheus.lily-system.svc:9090` | Prometheus 주소 |
| `INGRESS_NAME` | `%s-ingress` | lily-cicd의 Ingress 이름 규칙 (`%s` = 앱 이름) |
| `CLOUDWATCH_LOGS_ENABLED` | `false` | 로그 조회. 클러스터에서는 `true` |
| `LOG_GROUP` | `/lily/apps` | Fluent Bit이 보내는 로그 그룹 |
| `AWS_REGION` | `ap-northeast-2` | CloudWatch · DynamoDB 리전 |
| `OBSERVABILITY_API_TOKEN` | (비어 있음) | `/api/**` Bearer 토큰. 비어 있으면 인증 꺼짐 |
| `JUDGE_MIN_REQUESTS` | `20` | 이보다 적으면 판정 보류 |
| `JUDGE_NOTICE_ERROR_RATE` · `JUDGE_WARNING_ERROR_RATE` · `JUDGE_CRITICAL_ERROR_RATE` | `0.01` · `0.02` · `0.05` | 알림 · 주의 · 위험 기준 |
| `JUDGE_LATENCY_RATIO` | `2.0` | p95가 기준의 몇 배면 주의 |
| `JUDGE_CONSECUTIVE` | `2` | 자동 조치에 필요한 연속 판정 횟수 |
| `WATCH_INTERVAL` · `WATCH_WINDOW` | `30s` · `10m` | `RemediateWatch` 주기. `WATCH_WINDOW`는 아직 안 씀 |
| `ROLLBACK_ENABLED` · `CICD_URL` | `false` · `http://lily-cicd.lily-system.svc` | 자동 롤백 (보류) |
| `EVENT_STORE` | `memory` | 판정 이력 저장소 `memory` · `dynamodb` (예정) |

## 폴더 구조

```
lily-observer/
├─ src/main/java/com/lily/observer/
│  ├─ ObserverApplication.java      진입점
│  ├─ ObserverProperties.java       observer.* 설정
│  ├─ api/                          컨트롤러 · 토큰 필터 · 에러 응답 · OpenAPI
│  ├─ metrics/                      Prometheus 조회 (TrafficMetrics, TrafficPoint)
│  ├─ logs/                         CloudWatch Logs 조회 (LogEntry)
│  ├─ cluster/                      쿠버네티스 · metrics-server 조회 (AppSummary, PodStatus, NodeStatus)
│  ├─ judge/                        위험도 규칙 (RiskJudge, RiskLevel, Judgment)
│  ├─ status/                       패널 상태 (AppStatus)
│  └─ watch/                        감시 대상 모델 (보류)
├─ src/main/resources/
│  ├─ application.yml               설정 (환경변수로 덮어씀)
│  └─ static/index.html             설명 페이지 (/)
├─ src/test/java/                   단위 테스트
├─ deploy/k3s/
│  ├─ prometheus.yaml               지표 수집
│  ├─ fluent-bit.yaml               로그 수집
│  ├─ lily-observer.yaml            조회 서버 + 조회 권한
│  └─ build-image.yaml              이미지 빌드 Job
└─ Dockerfile
```

## 진행 상황

| 항목 | 상태 |
|---|---|
| 지표 수집 (Prometheus, ingress-nginx) | 클러스터 적용 |
| 로그 수집 (Fluent Bit → CloudWatch Logs) | 클러스터 적용 |
| 조회 API (앱 · 상태 · 지표 · 파드 · 로그 · 서버) | 클러스터에서 동작 확인 |
| 설명 페이지 · Swagger · OpenAPI | 클러스터에서 동작 확인 |
| 위험도 규칙 · 테스트 | 완료 |
| 자동 판정 루프 (`RemediateWatch` → 로그 사고) | 코드에 있음. 기본 `observer.remediate.enabled=false` |
| 롤백 호출 · 배포 이벤트 수신 | 보류 (lily-cicd와 역할 합의) |
| 판정 이력 저장 (DynamoDB) | 예정 |
| 로그 민감정보 마스킹 (토큰 · 비밀번호) | 예정 |
| API 토큰 설정 | 예정 (지금은 클러스터 안에서만 열려 있음) |

## 알려진 제한

- **스택 트레이스 분리**: Java 에러의 `at ...` 줄이 줄마다 따로 저장돼요. Fluent Bit multiline 규칙을 넣었지만 적용되지 않아 보류 중이에요. 같은 시각 앞뒤를 `level=all`로 보면 에러 위치가 보여요
- **블루그린 구분**: 지표는 입구 기준이라 blue · green을 나누지 못해요. 전환 후 감시에는 앱 단위로 충분해요
- **디스크 사용량**: 서버 API에 아직 없어요. 필요하면 kubelet 통계로 추가할 수 있어요
- **`level=error` 필터**: 대문자 `ERROR` · `Exception` 등 글자로 걸러요. 숫자 레벨만 쓰는 JSON 로그(`"level":50`)는 걸리지 않아요

## Team

| 이름 | 담당 |
|---|---|
| 최도일 | Logging · Monitoring (lily-observer) |
| 심형규 | Logging · Monitoring · Dashboard |
