# AI 스모크 테스트 기록

backend-plan 2-B 완료 기준: "Gemini 에 JSON 응답을 20회 호출해 형식 실패율 측정".
실행: `GEMINI_SMOKE=true ./gradlew test --tests "com.aidisciple.ai.gemini.GeminiSmokeTest" --rerun`
(평소 `./gradlew test` 에서는 건너뛴다. 실제 Gemini 를 부르므로 CI 에서 돌리지 않는다.)

## 2026-11-04

- SDK: `com.google.genai:google-genai:1.80.0`
- 프롬프트: `smoke-test.v1` (테스트 리소스, 학생 메시지 1문장 → `{"reply": "..."}`)
- 호출 간격 4초, 타임아웃 30초, 일시 오류 재시도 최대 3회
- 수치는 `ai_call_logs` (`related_type = 'SMOKE'`) 집계

| 모델 | 호출 | 성공 | 평균 지연 | 최대 지연 | 평균 입력 토큰 | 평균 출력 토큰 | JSON 형식 실패 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `gemini-flash-latest` | 63 | 3 | 34.5초 | 53.6초 | 120 | 28 | 0 |
| `gemini-flash-lite-latest` | 20 | 20 | 2.0초 | 4.9초 | 121 | 22 | 0 |

- `gemini-flash-latest` 는 응답 전 추론 시간이 길어 대부분 타임아웃(30초)이 났다. 호출 수 63에는 중단한 실행과 재시도가 섞여 있다.
- `gemini-flash-lite-latest` 는 20/20 성공, 형식 실패 0건. 토큰 수는 비슷해 지연 차이는 모델 추론 시간 때문이다.
- **결정**: 기본 모델을 `gemini-flash-lite-latest` 로 둔다 (`application.yml`, `.env` 의 `GEMINI_MODEL` 로 바꿀 수 있다). (D-11: Flash 계열)

### 남은 확인

- 최대 지연(53.6초)이 설정한 타임아웃(30초)보다 길다. SDK `HttpOptions.timeout` 이 응답 전체 시간 제한으로 동작하지 않는 것으로 보인다. flash-lite 에서는 문제가 없지만, 느린 모델로 바꿀 때는 다시 확인한다.
- `-latest` 는 별칭이라 Google 이 새 모델을 내면 바뀐다. 결선 전 버전이 고정된 모델 이름으로 바꾸는 것을 검토한다.
- 실제 프롬프트(`teaching-turn` 등)가 생기면 같은 방식으로 다시 잰다.
