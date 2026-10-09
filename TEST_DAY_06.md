# Day 06 Test Report

Ngày test: 2026-10-09 · Branch: `main` (HEAD `a906f76`) · Production code: **KHÔNG sửa**

## Overall Result

**FAIL**

Phần lõi của collector (đếm, batch, nhánh reply, mapper, vòng đời worker, transaction) chạy đúng khi `YoutubeClient` được mock. Nhưng khi đi qua HTTP thật thì pipeline bị hỏng ở 2 điểm Critical:

1. Thread page 2+ không bao giờ được lấy (lỗi typo `pgaeToken`). Với API thật, lỗi này thành **vòng lặp vô hạn**.
2. Mọi lời gọi `comments.list` đều lỗi deserialize, nên video có thread trên 5 reply không bao giờ DONE.

## Baseline Build

- **clean test** (`.\mvnw.cmd clean test`): BUILD FAILURE
  - Trước khi thêm test: compile OK, 1 test, 1 error (`contextLoads`, cần MySQL).
  - Sau khi thêm test: 57 test, 9 failures, 3 errors, 3 skipped.
- **verify** (`.\mvnw.cmd verify`): BUILD FAILURE, cùng kết quả (57 / 9F / 3E / 3S), dừng ở surefire.
- Compile error: **không có**. Production code compile được.
- `contextLoads` lỗi vì **môi trường**: shell test không có `MYSQL_URL`/`MYSQL_USERNAME`/`MYSQL_PASSWORD`, nên Hibernate báo `Unable to determine Dialect without JDBC metadata`. Đây không phải bug code.

## Test Summary

- Total: 57
- Passed: 42
- Failed: 12 (9 failures + 3 errors)
  - 11 test fail do production bug
  - 1 test fail do môi trường (`contextLoads`)
- Skipped: 3 (`YoutubeRealApiTest`, opt-in; được chạy riêng bên dưới)

Các test mới (chỉ trong `src/test/**`):

| File | Nội dung |
|---|---|
| `support/YoutubeFixtures.java` | Builder cho DTO + `CapturingHandler` ghi lại từng batch |
| `collector/youtube/YoutubeCommentCollectorTest.java` | Pagination, loop guard, replies A/B/C, reply pagination, stats, batch, config, phân loại lỗi (25 test) |
| `collector/youtube/YoutubeCommentMapperTest.java` | Mapper top-level / reply (2 test) |
| `client/YoutubeClientHttpTest.java` | `YoutubeClient` thật + `MockRestServiceServer`: URL, JSON thật, lỗi HTTP (13 test) |
| `worker/CrawlWorkerTest.java` | Vòng đời worker, lỗi giữa pagination (7 test) |
| `service/CrawlJobServiceTest.java` | Chuyển trạng thái job, ranh giới transaction (6 test) |
| `realapi/YoutubeRealApiTest.java` | YouTube API thật, opt-in `-Dreal-api=true` (3 test) |

Test vòng lặp đều có guard (tối đa 20 lời gọi rồi `AssertionError`), nên bug infinite loop làm test FAIL chứ không treo build.

## Architecture Review

```
CrawlWorker.run()  (@Scheduled, fixedDelay 2s, 1 thread)
  ├─ CrawlJobService.claimNextJob()        @Transactional: SELECT … FOR UPDATE SKIP LOCKED, PENDING→PROCESSING, COMMIT
  ├─ YoutubeCrawlJobProcessor.process()    KHÔNG transaction
  │    └─ YoutubeCommentCollector.collectAll(videoId, handler)
  │         loop commentThreads.list (PAGE_SIZE=100, theo nextPageToken, guard token lặp)
  │           ├─ map top-level → handler.handle(batch ≤100)
  │           └─ mỗi thread có totalReplyCount>0:
  │                 embedded.size()==total → handler.handle(embedded)
  │                 ngược lại → collectAllReplies(parentId): loop comments.list → handler.handle(page)
  │         → CommentCollectionResult(topLevel, replies, total, threadPages, replyPages, apiCalls)
  │    handler = LogginCommentBatchHandler (chỉ log, chưa persist)
  └─ markDone / markSkipped / markFailedAttempt   mỗi cái là 1 @Transactional ngắn
StuckJobRecoveryScheduler: PROCESSING quá 30 phút → PENDING
YoutubeClient: RestClient → /commentThreads, /comments; lỗi HTTP → YoutubeApiException(status, reason), lỗi I/O → RetryableCrawlException
YoutubeCommentCollector.mapException: commentsDisabled/videoNotFound → Skip; quotaExceeded/5xx/429 → Retryable
```

## Functional Tests

| Case | Kết quả | Test |
|---|---|---|
| top-level one page | PASS | `onePage_collectsAllTopLevelComments` |
| multiple pages (100/100/50 → 250, 3 pages, 3 calls) | PASS (mock client) / **FAIL (HTTP)** | `threePages_100_100_50…` PASS; `collectAll_overHttp_twoPages`, `getCommentThreads_sendsPageTokenQueryParam`, `collectAll_overHttp_largeVideo_terminates` FAIL (BUG-01) |
| không dựa vào totalResults để dừng | PASS | code review: dừng theo `nextPageToken` |
| no replies (A) | PASS | `caseA_zeroReplies_getRepliesNotCalled` |
| complete embedded replies (B) | PASS | `caseB_completeEmbeddedReplies_usedWithoutCommentsList` |
| incomplete embedded replies (C: 20, không phải 25) | PASS (mock) / **FAIL (HTTP)** | `caseC_…` PASS; qua HTTP thì BUG-02 / BUG-03 |
| reply pagination (230 replies, 3 pages, 4 apiCalls) | PASS (mock) / **FAIL (HTTP)** | `replyPagination_230replies_3pages_4apiCalls` |
| repeated thread token | PASS (token lặp trên page có item) / **FAIL (token lặp trên page rỗng)** | `repeatedThreadPageToken_failsFast` PASS; `repeatedThreadPageToken_onEmptyPages_failsFast` FAIL (BUG-05) |
| repeated reply token | PASS | `repeatedReplyPageToken_failsFast` |
| mapper (top-level/reply, comment ID ≠ thread ID) | PASS | `YoutubeCommentMapperTest`, `replies_haveParentIdOfTopLevelComment_notThreadId` |
| fetchReplies=false | **FAIL** | `collectorConsumesYoutubeCrawlerProperties` (BUG-04) |
| statistics (total = top + replies, pages, apiCalls) | PASS | `statistics_areConsistent` |
| worker success | PASS | `success_claimThenProcessThenMarkDone`, `claim_pendingToProcessing`, `markDone_processingToDone` |
| worker retry | PASS | `retryableFailure_marksFailedAttempt_notDone`, `markFailedAttempt_processingToPending…`, `…atMaxRetries_becomesFailed` |
| worker skip | PASS | `skip_marksSkipped_noRetry`, `markSkipped_processingToSkipped` |
| error during pagination (page 3 lỗi) | PASS | `failureOnThirdPage_…`, `failureInMiddleOfPagination_jobIsRetriedNotDone` |
| batch size ≤ 100 | PASS | `batches_neverExceedPageSize` |
| bounded-memory review | PASS | `eachPageIsHandedToHandlerBeforeNextPageIsFetched` + code review |
| error: commentsDisabled / videoNotFound → Skip | PASS | `commentsDisabled_isSkip`, `videoNotFound_isSkip`, `commentsDisabled_overHttp_isSkip` |
| error: 429 / 5xx / timeout / quotaExceeded → Retryable | PASS (commentThreads) / **FAIL (comments.list)** | `http429_…`, `http5xx_…`, `networkTimeout_isRetryable`, `quotaExceeded_isRetryable_notSkip` PASS; `replies_http429_isRetryable`, `replies_quotaExceeded_isRetryable` FAIL (BUG-06) |
| transaction boundaries | PASS | `transactionBoundaries_shortClaimAndFinish_noTransactionAroundCrawl` + code review |
| collectPage totalResults | **FAIL** | `collectPage_reportsTotalResultsFromPageInfo` (BUG-07) |
| totalReplyCount null | **FAIL** | `nullTotalReplyCount_isTreatedAsZero` (BUG-08) |

## Bugs Found

### BUG-01 — Thread page token gửi sai tên tham số (`pgaeToken`), dẫn đến lặp page 1 vô hạn

Severity: **Critical**

Location: `YoutubeClient.getCommentThreads`, `src/main/java/com/social_comment_abbreviation_collector/client/YoutubeClient.java:50`

Expected: page 2+ gửi `pageToken=<token>`. Video trên 100 thread lấy đủ comment rồi dừng.

Actual:
- Tham số được gửi là `pgaeToken`. YouTube bỏ qua tham số lạ nên luôn trả page 1.
- Đã kiểm chứng trên API thật: YouTube trả về **`nextPageToken` khác nhau mỗi lần** gọi page 1. Vì vậy guard `verifyNewPageToken` không bao giờ phát hiện token lặp.
- Hệ quả: `collectAll` lặp page 1 vô hạn. Nó đốt quota (≥1 unit/lần, chưa kể replies), đẩy cùng 100 comment vào handler lặp đi lặp lại, và job kẹt ở PROCESSING.
- Sau 30 phút `StuckJobRecoveryScheduler` đặt job về PENDING. Lúc đó worker vẫn đang lặp vì worker chỉ có 1 thread.

How to reproduce:
- `YoutubeClientHttpTest.getCommentThreads_sendsPageTokenQueryParam`
- `YoutubeClientHttpTest.collectAll_overHttp_twoPages`
- `YoutubeClientHttpTest.collectAll_overHttp_largeVideo_terminates`
- `YoutubeRealApiTest.largeVideo_moreThanOneThreadPage_andPage2DiffersFromPage1` (video `dQw4w9WgXcQ`)

Evidence:
- `java.lang.AssertionError: Expected query param <pageToken> to exist but was null`
- `AssertionError: INFINITE LOOP: > 20 commentThreads calls`
- Real API: `[page 2 must not repeat page 1] Expecting no elements of: ["Ugzge340dBgB75hWBm54AaABAg", …`

Likely root cause: typo `"pgaeToken"` trong `queryParam`.

Suggested fix: đổi thành `"pageToken"`. Nên thêm giới hạn cứng số page/API call cho mỗi job làm lớp bảo vệ thứ hai, vì không thể dựa vào token lặp. **Do not implement the suggested fix.**

### BUG-02 — `comments.list` luôn lỗi deserialize vì `pageInfo.totalResults` là `int`

Severity: **Critical**

Location: `YoutubePageInfo` (`int totalResults`), dùng trong `YoutubeCommentListRepsonse`. Đường gọi: `YoutubeClient.getReplies`.

Expected: parse được response của `comments.list` và lấy hết reply.

Actual:
- Response thật của `comments.list` là `"pageInfo": {"resultsPerPage": 100}`, không có `totalResults` (đã kiểm chứng live).
- Jackson 3 mặc định `FAIL_ON_NULL_FOR_PRIMITIVES`, nên ném `MismatchedInputException: Cannot map 'null' into type 'int'`. Lỗi này bị bọc thành `RetryableCrawlException("Failed to retrieve YouTube replies")`.
- Mọi video có ít nhất 1 thread trên 5 reply (embedded không đủ) sẽ retry 5 lần (1m/5m/15m/1h) rồi **FAILED**. Mỗi lần retry crawl lại từ đầu.

How to reproduce:
- `YoutubeClientHttpTest.getReplies_pageInfoWithoutTotalResults_isParsed`
- `YoutubeRealApiTest.smallVideo_onePage` và `videoWithReplies_collectsEveryReportedReply` (video `01yqAcaYsL0`)

Evidence: `HttpMessageNotReadableException: JSON parse error: Cannot map 'null' into type 'int'`

Likely root cause: field primitive cho một giá trị API không đảm bảo luôn có.

Suggested fix: dùng `Integer` (nullable) cho `totalResults`/`resultsPerPage`, hoặc bỏ `pageInfo` khỏi DTO reply vì không dùng tới. **Do not implement the suggested fix.**

### BUG-03 — Reply bị mất âm thầm: DTO đọc field `comments`, API trả `items`

Severity: **High** (đang bị BUG-02 che; sẽ lộ ra ngay khi BUG-02 được sửa)

Location: `YoutubeCommentListRepsonse.comments`, dùng trong `YoutubeCommentCollector.collectAllReplies`.

Expected: các reply trong `items` của `comments.list` được map và gửi vào handler.

Actual: JSON key là `items`, nên `comments()` luôn là `null`. Collector coi như 0 reply, job vẫn **DONE**, `replies` thiếu và không có lỗi nào.

How to reproduce: `YoutubeClientHttpTest.getReplies_deserializesItemsArray`

Evidence: `[replies parsed from JSON 'items'] Expecting actual not to be null`

Likely root cause: tên field record không khớp JSON.

Suggested fix: đổi tên field thành `items`, hoặc thêm `@JsonProperty("items")`. **Do not implement the suggested fix.**

### BUG-04 — `crawler.youtube.fetch-replies` và `page-size` không có tác dụng

Severity: **Medium**

Location: `YoutubeCommentCollector` (constructor, `PAGE_SIZE = 100`), `YoutubeCrawlerProperties`, `YoutubeClient.getReplies` (`maxResults` hard-code 100).

Expected: `fetch-replies=false` thì top-level vẫn được collect, replies = 0, replyPages = 0, và không gọi `getReplies()`. `page-size` quyết định `maxResults`.

Actual: `YoutubeCrawlerProperties` được bind nhưng không class nào inject. Collector luôn lấy replies với page size 100.

How to reproduce: `YoutubeCommentCollectorTest.collectorConsumesYoutubeCrawlerProperties`

Evidence: `Expecting [YoutubeClient, YoutubeCommentMapper] to contain [YoutubeCrawlerProperties]`

Suggested fix: inject `YoutubeCrawlerProperties` vào collector; bỏ qua nhánh reply khi `!fetchReplies()`; dùng `pageSize()` (giới hạn 1..100). **Do not implement the suggested fix.**

### BUG-05 — Guard token lặp bị bỏ qua trên page rỗng

Severity: **Medium**

Location: `YoutubeCommentCollector.collectAll`, nhánh `if (response == null || response.items() == null || response.items().isEmpty()) { … continue; }`

Expected: page không có item nhưng `nextPageToken` lặp lại vẫn phải fail-fast.

Actual: `continue` nhảy thẳng tới điều kiện `while`, bỏ qua `verifyNewPageToken`, nên lặp vô hạn.

How to reproduce: `YoutubeCommentCollectorTest.repeatedThreadPageToken_onEmptyPages_failsFast`

Evidence: `AssertionError: INFINITE LOOP: > 20 thread calls`

Suggested fix: kiểm tra token ở mọi nhánh, ví dụ dùng `resolveNextToken` (giống `collectAllReplies`) trước `continue`. **Do not implement the suggested fix.**

### BUG-06 — Lỗi từ `comments.list` không được phân loại

Severity: **Low** (đã có catch-all trong worker che bớt)

Location: `YoutubeCommentCollector.collectAllReplies`: gọi `youtubeClient.getReplies` mà không qua `mapException`.

Expected: 429 / 5xx / quotaExceeded từ `comments.list` thì ném `RetryableCrawlException`, giống `commentThreads.list`.

Actual: ném nguyên `YoutubeApiException`. Worker rơi vào `catch (Exception)`, log `ERROR Unexpected failure`, rồi vẫn `markFailedAttempt`. Kết quả cuối vẫn retry (`CrawlWorkerTest.replies429_isStillRetried` PASS) nhưng log và phân loại sai.

How to reproduce: `YoutubeCommentCollectorTest.replies_http429_isRetryable`, `replies_quotaExceeded_isRetryable`

Evidence: `Expecting … RetryableCrawlException but was: YoutubeApiException: boom at YoutubeClient.getReplies`

Suggested fix: bọc `getReplies` trong try/catch `YoutubeApiException` và áp dụng `mapException`. **Do not implement the suggested fix.**

### BUG-07 — `collectPage` luôn trả `totalResults = 0`

Severity: **Low** (chỉ ảnh hưởng `GET /api/youtube/videos/{id}/comments`; `collectAll` không dùng)

Location: `YoutubeCommentThreadResponse.youtubePageInfo` (JSON key là `pageInfo`), `YoutubeCommentCollector.collectPage`.

Expected: `totalResults` = `pageInfo.totalResults`. Actual: luôn 0.

How to reproduce: `YoutubeClientHttpTest.collectPage_reportsTotalResultsFromPageInfo`

Evidence: `expected: 3 but was: 0`

Suggested fix: đổi tên field thành `pageInfo` hoặc dùng `@JsonProperty("pageInfo")`. **Do not implement the suggested fix.**

### BUG-08 — `totalReplyCount` null gây NPE

Severity: **Low** (YouTube hiện luôn trả field này)

Location: `YoutubeCommentCollector.collectAll`: `long totalReplies = thread.snippet().totalReplyCount();` (auto-unboxing từ `Long`).

Expected: coi null là 0. Actual: `NullPointerException`, job retry.

How to reproduce: `YoutubeCommentCollectorTest.nullTotalReplyCount_isTreatedAsZero`

Suggested fix: null-check trước khi unbox. **Do not implement the suggested fix.**

## Production Code Issues

Chỉ mô tả, không sửa.

- **Không có connect/read timeout cho `RestClient`** (`HttpClientConfig.youtubeRestClient`). Worker chỉ có 1 scheduled thread, nên một connection treo sẽ chặn toàn bộ crawl vô thời hạn. Mức Medium.
- **Catch-all `Exception` thì retry.** Mọi lỗi không phải Skip đều retry tối đa 5 lần, kể cả lỗi xác định như API key sai (400/403 `keyInvalid`/`forbidden`) hay `IllegalStateException` do token lặp. Nên có nhánh lỗi permanent, chuyển thẳng sang FAILED.
- **`quotaExceeded` dùng backoff 1m / 5m / 15m / 1h.** Quota YouTube reset theo ngày, nên 5 lần retry hết trong khoảng 1h21m rồi job thành FAILED. Nên defer tới lúc reset quota. Đúng spec "retryable, không SKIPPED" nhưng không hiệu quả.
- **`StuckJobRecoveryScheduler` hard-code `Duration.ofMinutes(30)`.** Key `crawler.recovery.processing-timeout-minutes` trong yaml không được dùng.
- **Không có lease/owner khi recover job.** Nếu crawl kéo dài trên 30 phút, job bị đặt về PENDING trong khi vẫn đang chạy. Khi chạy xong, `markDone` ném `IllegalArgumentException` (status không còn là PROCESSING), exception đó rơi vào `markFailedAttempt` (no-op). Kết quả của lần crawl bị bỏ và job chạy lại.
- **`markSkipped` ném `IllegalStateException` bên trong khối `catch` của worker** nếu job không còn PROCESSING. Exception này thoát khỏi `run()`.
- Unused import `java.security.PrivateKey` trong `YoutubeCommentCollector`.

Đã review và **đạt**:

- **Bounded memory.** Không có `allComments`/`addAll` qua nhiều page. Mỗi page thread (≤100) và mỗi page reply (≤100) được gửi vào handler ngay rồi hết scope. Test `InOrder` xác nhận handler được gọi trước khi fetch page tiếp theo.
- **Transaction.** `claimNextJob` và `mark*` là các transaction ngắn riêng. `CrawlWorker`, `YoutubeCrawlJobProcessor`, `YoutubeCommentCollector` không có `@Transactional`, nên crawl chạy ngoài transaction.
- **Mapper.** Dùng `topLevelComment.id`, không dùng thread id. Reply có `parentExternalCommentId` = top-level comment id.

## Limitations

Đúng với code hiện tại:

- Retry bắt đầu lại từ page đầu (chưa checkpoint page/token). Đây là limitation được chấp nhận cho Day 6.
- Chưa persist comment: `LogginCommentBatchHandler` chỉ log.
- Chưa có lease heartbeat cho job đang PROCESSING (xem mục trên).
- Chưa dedup comment giữa các lần retry, vì chưa persist.
- `contextLoads` cần MySQL thật; chưa có profile test (H2/Testcontainers).

## Real API Test

**FAIL** (đã chạy; key lấy từ environment `YOUTUBE_API_KEY`, không in ra, không ghi vào file).

Lệnh chạy:

```
.\mvnw.cmd test -Dtest=YoutubeRealApiTest -Dreal-api=true "-DargLine=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Lần chạy đầu không có `trustStoreType` thì lỗi `PKIX path building failed`. Đây là vấn đề truststore của JVM trên máy này, không phải bug code. Chỉ cần thêm cờ đó cho JVM test.

| Test | Video | Kết quả |
|---|---|---|
| Video < 100 comments → threadPages = 1 | `01yqAcaYsL0` (33 thread) | **ERROR**: BUG-02 (`Cannot map null into type int` khi gọi `comments.list`) |
| Video có replies → replies > 0, bằng Σ`totalReplyCount` | `01yqAcaYsL0` | **ERROR**: BUG-02 |
| Video > 100 comments → threadPages > 1, page 2 ≠ page 1 | `dQw4w9WgXcQ` (dừng sau 2 page, không lấy reply) | **FAIL**: page 2 trùng page 1 (BUG-01) |

Quota đã dùng rất ít (khoảng 20 unit, gồm cả các lệnh `curl` kiểm chứng).

## Final Conclusion

Day 6 hiện tại: **FAIL**.

Thiết kế (streaming theo batch, nhánh embedded/full reply, đếm thống kê, vòng đời worker, ranh giới transaction) là đúng và đã được test bao phủ. Nhưng với YouTube API thật, mục tiêu "lấy toàn bộ top-level comments và replies bằng pagination" **chưa đạt**:

- **BUG-01**: video trên 100 thread rơi vào vòng lặp vô hạn trên page 1, đốt quota.
- **BUG-02**: video có thread trên 5 reply không bao giờ hoàn thành (retry rồi FAILED).
- **BUG-03**: khi sửa BUG-02, reply sẽ mất âm thầm nếu không sửa luôn BUG-03.

**Điều kiện sang Day 7 (persist comment):** sửa BUG-01, BUG-02, BUG-03 (và nên sửa BUG-04, BUG-05). Sau đó chạy lại `YoutubeClientHttpTest` và `YoutubeRealApiTest` cho xanh. Nếu persist trên pipeline hiện tại, DB sẽ đầy comment trùng của page 1 và thiếu reply.
