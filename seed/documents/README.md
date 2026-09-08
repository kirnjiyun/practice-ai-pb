# RAG 시드 문서

AI 상담의 근거가 되는 문서 6종입니다. `admin@aipb.demo` 계정으로 업로드 → 승인하면
검색 가능 상태가 됩니다.

> ⚠️ **모든 문서 첫 줄에 아래 문장을 반드시 넣습니다.**
>
> ```
> ※ 본 문서는 AI PB 포트폴리오 데모를 위해 작성된 가상의 참고 자료입니다.
>    실제 금융상품 설명서나 공식 안내문이 아니며, 투자 판단의 근거로 사용할 수 없습니다.
> ```

| 파일 | category | visibility | roleAccess | 내용 |
|---|---|---|---|---|
| `01-financial-glossary.md` | `GLOSSARY` | PUBLIC | USER, PB, ADMIN | 금융 기초 용어 약 40개 |
| `02-asset-class-risks.md` | `ASSET_CLASS` | USER | USER, PB, ADMIN | 자산군 9종의 특징과 위험 |
| `03-risk-type-principles.md` | `PRINCIPLE` | USER | USER, PB, ADMIN | 투자성향 5단계별 일반적 자산관리 원칙 |
| `04-service-terms-ai-policy.md` | `POLICY` | PUBLIC | USER, PB, ADMIN | 서비스 이용 약관 및 AI 답변 정책 |
| `05-investment-disclaimer.md` | `DISCLAIMER` | PUBLIC | USER, PB, ADMIN | 투자 유의사항 · 면책 고지 |
| `06-sample-product-sheet.md` | `PRODUCT_SAMPLE` | USER | USER, PB, ADMIN | **가상** 금융상품 설명서 3종 |

## 작성 원칙

1. **특정 종목·실존 상품을 언급하지 않습니다.** 06번의 상품은 전부 가상이며 문서 안에
   "가상 상품"임을 반복 명시합니다.
2. **단정적 수익 표현을 쓰지 않습니다.** "~하면 수익이 납니다" 대신 "일반적으로 ~로
   설명됩니다".
3. **지시문처럼 보이는 문장을 넣지 않습니다.** ingestion 단계의 인젝션 스캐너가
   `injection_suspected`로 표시하면 승인이 차단됩니다.
4. 청크 분할(500토큰/오버랩 80)을 고려해 **한 소제목당 2~4문단**으로 끊어 씁니다.

## 인젝션 스캐너 테스트용

`_injection-test.md`는 **일부러 인젝션 문장을 넣은 문서**입니다.
업로드 시 승인 버튼이 비활성화되는지 확인하는 용도이며, 절대 승인하지 않습니다.
