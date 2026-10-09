# Day 07 QA Report

Ngày test: 2026-10-10 · Branch `main` (HEAD `a906f76` + thay đổi Day 7 chưa commit) · MySQL 8.0 local, schema test riêng `social_comment_collector_qa_test`

## Overall Status

**FAIL**

Bảng `comments` chỉ chứa được **tối đa 1 row**. Câu INSERT của `CommentIngestionRepository` không truyền cột `id`, nên MySQL gán `id = ''`. Từ comment thứ hai trở đi, mọi insert đều trùng primary key `''` và bị `INSERT IGNORE` bỏ qua âm thầm, rồi được đếm là "duplicate". Thêm vào đó, endpoint lấy reply bị đổi thành `/items`, nên với API thật mọi lần lấy reply đều trả 404.

## Production Code Modified

**NO.** `git diff HEAD --stat -- src/main` trước và sau phiên test giống hệt nhau (18 file, +343/−24, toàn bộ là thay đổi Day 7 của bạn). Không file nào trong `src/main` được sửa trong phiên này.

## Baseline

- `.\mvnw.cmd clean test`: **BUILD FAILURE**. Compile OK; 106 test, 29 failures, 2 errors, 5 skipped.
- `.\mvnw.cmd verify`: **BUILD FAILURE**, cùng kết quả 106 / 29F / 2E / 5S.
- Production compile được, không BLOCKED.

## Test Summary

- **Total:** 106
- **Passed:** 70
- **Failed:** 31 (29 failures + 2 errors)
  - 30 test fail do bug production.
  - 1 test fail do môi trường: `contextLoads` dùng `application.yaml` mặc định, cần `MYSQL_*` env mà shell test không có.
- **Skipped:** 5
  - 3 test `YoutubeRealApiTest` (Day 6) và 1 test `Day07RealApiPersistenceTest`: opt-in `-Dreal-api=true`, đã chạy riêng ở mục Real API Test.
  - 1 test platform `OTHER`: N/A, vì cột `platform` là `ENUM('YOUTUBE')`.

Môi trường test DB:
- Spring context thật, `ddl-auto=create` trên schema `social_comment_collector_qa_test` (schema được sinh từ entity production).
- `YoutubeClient` được mock; worker scheduler tắt.
- Credentials đọc từ `~/.day7-qa-db.properties.txt` (ngoài repo, không log). Kết nối qua `127.0.0.1:3306`.

Test mới / cập nhật (chỉ trong `src/test/**`):

| File | Nội dung |
|---|---|
| `support/QaTestDatabase.java`, `support/AbstractMySqlIntegrationTest.java` | Hạ tầng test DB, helper SQL (duplicate query, orphan reply, đếm top-level/reply) |
| `integration/Day07PersistenceIntegrationTest.java` | Schema, mapping, Unicode, text dài, idempotency, metric, rủi ro INSERT IGNORE (30 test) |
| `integration/Day07IdempotencyIntegrationTest.java` | Collector → handler → MySQL; vòng đời worker DONE / retry / SKIPPED (7 test) |
| `service/CommentServiceTest.java` | Dedup trong batch, id không hợp lệ, metric, `@Transactional` (6 test) |
| `collector/DatabaseCommentBatchHandlerTest.java` | Ủy quyền cho service và trả kết quả (1 test) |
| `processor/YoutubeCrawlJobProcessorTest.java` | Context đúng, lỗi được propagate (2 test) |
| `realapi/Day07RealApiPersistenceTest.java` | YouTube thật + MySQL, crawl 2 lần (opt-in) |
| `support/YoutubeFixtures.java`, test Day 6 | Cập nhật theo interface Day 7 (`CommentBatchContext`, `CommentBatchResult`) |

## Architecture Reviewed

```
CrawlWorker.run()                                    (không @Transactional)
 ├─ CrawlJobService.claimNextJob()                   @Transactional ngắn: PENDING→PROCESSING
 ├─ YoutubeCrawlJobProcessor.process(job)            tạo CommentBatchContext(contentId, platform, externalId)
 │   └─ YoutubeCommentCollector.collectAll(videoId, ctx, handler)
 │        mỗi thread page (≤100) → handler.handle(ctx, topLevels)
 │        mỗi thread có reply → embedded (nếu đủ) hoặc comments.list theo page → handler.handle(ctx, replies)
 │        handler = DatabaseCommentBatchHandler → CommentService.saveBatch()       @Transactional / batch
 │             dedup theo externalCommentId (LinkedHashMap), bỏ id null/blank
 │             → CommentIngestionRepository.insertIgnoreBatch()  JdbcTemplate.batchUpdate("INSERT IGNORE …")
 │             → CommentBatchResult(received, inserted = Σ updateCount>0, duplicates = received − inserted)
 └─ markDone (+ content.lastCrawledAt) / markSkipped / markFailedAttempt   mỗi cái 1 @Transactional ngắn
```

## Database Integrity

- **Unique constraint:** **PASS.** `uk_comment_platform_external_id (platform, external_comment_id)` tồn tại ở DB, `NON_UNIQUE = 0`. Một `INSERT` thường bị trùng sẽ ném `DataIntegrityViolationException`.
- **Duplicate query:** **PASS (0 rows)**, nhưng chỉ đúng một cách tầm thường: bảng không bao giờ có quá 1 row (BUG-01), nên không thể có duplicate.
- **FK/content relation:** **PASS.** FK `comments.content_id → contents` tồn tại, và comment thuộc đúng Content khi test từng row. Tuy vậy, comment của content không tồn tại bị **nuốt âm thầm** (BUG-03).
- **Unicode:** **PASS khi test từng row.** Cột `text` là `TEXT` charset `utf8mb4`; tiếng Việt, emoji, tiếng Nhật, tiếng Hàn đọc lại đúng nguyên bản. **FAIL khi ghi cả batch** (chỉ còn 1/5 row, do BUG-01).
- **Long text:** **PASS.** Hơn 6000 ký tự (có emoji) đọc lại đầy đủ, không bị truncate.

## Idempotency Tests

| Case | Kết quả | Ghi chú |
|---|---|---|
| same batch twice (100) | **FAIL** | Sau lần 1 chỉ có 1 row (kỳ vọng 100) |
| existing DB duplicates (A,B → A,B,C,D) | **FAIL** | Số row tăng +0 (kỳ vọng +2) |
| duplicate in same batch (A,A,B,C,B) | **FAIL** | DB chỉ có `[A]` (kỳ vọng A,B,C). Bước dedup trong Java đúng: `CommentServiceTest` PASS |
| sameExternalIdTwice qua repository | PASS | 1 row (đúng, nhưng mỗi lần chỉ 1 row) |
| unique constraint chặn insert trùng | PASS | |
| partial failure + retry (page 1–3 OK, page 4 lỗi) | **FAIL** | Sau lỗi chỉ còn 1 row (kỳ vọng 300); sau retry 1 row (kỳ vọng 350). Phần vòng đời job đều đúng |
| worker retry lifecycle | PASS | PROCESSING→PENDING, retryCount=1, nextRunAt ở tương lai, lastCrawledAt vẫn null |
| collectAll chạy 2 lần | **FAIL** | 1 row (kỳ vọng 292) |

## Persistence Tests

| Case | Kết quả |
|---|---|
| top-level (mọi field, publishedAt, sourceUpdatedAt, crawledAt, likeCount, platform, content_id) | PASS |
| primary key `id` được sinh | **FAIL**: `id = ''` |
| reply, `parentExternalCommentId` = top-level external id | PASS (1 row) |
| top-level + reply cùng lưu | **FAIL**: reply bị mất (BUG-01) |
| N comment khác nhau / batch 100 | **FAIL**: chỉ 1 row |
| author null | PASS |
| raw text giữ nguyên (`SP này OK ko mn!!! 😂`) | PASS |
| hasAbbreviation mặc định false (kể cả `sp này ok ko mn`) | PASS |
| id null/blank: bỏ qua, không insert | PASS (DB an toàn); metric **FAIL** (BUG-05) |
| content không tồn tại | **FAIL**: bị nuốt âm thầm (BUG-03) |
| text NULL | **FAIL**: lưu thành `''` (BUG-03) |
| external id dài hơn 150 ký tự | **FAIL**: bị truncate âm thầm (BUG-03) |
| cùng external id ở content khác | PASS, theo luật first-owner-wins |
| cùng external id ở platform khác | N/A (cột là `ENUM('YOUTUBE')`) |
| comment bị edit trên YouTube | PASS (limitation: không refresh) |

## Worker Tests

| Case | Kết quả |
|---|---|
| DONE (status, retryCount = 0, `Content.lastCrawledAt` được set) | PASS. Số row **FAIL** (1/292, BUG-01) |
| retry (PENDING, retryCount + 1, nextRunAt ở tương lai, lastCrawledAt null) → retry → DONE | PASS. Số row **FAIL** |
| SKIPPED (commentsDisabled / videoNotFound, 0 row, lastCrawledAt null, không retry) | PASS |
| Unit `CrawlWorkerTest` / `CrawlJobServiceTest` | PASS |

## Transaction Review

**PASS.**
- `CommentService.saveBatch` là `@Transactional` cho từng batch (≤100 comment); JdbcTemplate tham gia transaction JPA.
- `CrawlWorker`, `YoutubeCrawlJobProcessor`, `YoutubeCommentCollector`, `DatabaseCommentBatchHandler` không có `@Transactional`. Lời gọi YouTube nằm ngoài transaction.
- Batch đã commit vẫn được giữ khi page sau lỗi; test partial failure xác nhận row đã commit không bị rollback.
- Ghi chú: một thread page có thể sinh nhiều transaction (1 cho top-level, 1 cho mỗi batch reply). Như vậy là hợp lý.

Batch persistence: dùng `jdbcTemplate.batchUpdate`, không có `repository.save()` trong vòng lặp. **PASS** về thiết kế (xem thêm phần Production issues).

## Bugs Found

### BUG-01 — Native INSERT không gán `id`; cả bảng chỉ chứa được 1 comment

Severity: **Critical**

Location:
- `CommentIngestionRepository.insertIgnoreBatch`: danh sách cột không có `id`.
- `Comment.id`: `@GeneratedValue(strategy = UUID)`, Hibernate sinh `varchar(255) NOT NULL`, không có default ở DB.

Scenario: ghi bất kỳ batch nào có từ 2 comment khác nhau trở lên, hoặc 2 batch liên tiếp.

Expected: mỗi comment hợp lệ thành 1 row với id riêng.

Actual:
- MySQL cảnh báo `1364 Field 'id' doesn't have a default value`. `INSERT IGNORE` hạ lỗi này xuống warning và ghi `id = ''`.
- Row tiếp theo cũng có `id = ''`, trùng PRIMARY KEY, nên bị IGNORE. Kết quả: toàn bộ bảng chỉ có 1 comment.
- Các comment bị mất được báo là `duplicates`; job vẫn **DONE**. Mất dữ liệu gần như toàn bộ mà không có lỗi nào.

Evidence:
- `productionInsertStatement_raisesNoHiddenWarnings`: `["1364 Field 'id' doesn't have a default value"]`
- `primaryKey_isGeneratedForInsertedRow`: `Expecting not blank but was: ""`
- `distinctComments_allPersisted`: `["A"]` thay vì `["A","B","C"]`
- `batchOf100_allPersisted`: 1 thay vì 100
- `sameBatchTwice_100_staysAt100`, `duplicatesWithinBatch_*`, `existingRows_*`, `topLevelAndReply_bothPersisted`, `unicodeAndEmoji_roundTrip_oneBatch`
- `collectAll_*` (1 thay vì 292), `worker_*` (1 thay vì 292 / 300 / 350)
- Real API: rows = 1 sau page 1 (33 top-level)

Likely root cause: chuyển từ JPA `save` sang native INSERT nhưng quên sinh khóa chính, cộng với `INSERT IGNORE` che lỗi.

Suggested fix: truyền `id` (vd. `UUID.randomUUID().toString()`) cho từng row trong batch, hoặc dùng khóa chính có default ở DB (`AUTO_INCREMENT` / `DEFAULT (UUID())`). Nên thay `INSERT IGNORE` bằng cơ chế chỉ bỏ qua đúng lỗi duplicate key (xem BUG-03). **DO NOT IMPLEMENT THE FIX.**

### BUG-02 — Endpoint reply bị đổi thành `/items`, nên YouTube trả 404 (regression)

Severity: **High**

Location: `YoutubeClient.getReplies` (`.path("/items")`); `YoutubeCommentController.getComments` (`/{videoId}/items`).

Scenario: video có thread trên 5 reply (embedded không đủ), nên collector gọi `comments.list`.

Expected: gọi `GET /youtube/v3/comments?parentId=…`.

Actual: gọi `/youtube/v3/items?...` và nhận **404 Not Found**. `YoutubeApiException` không được map (BUG-06 Day 6), nên worker coi là "Unexpected failure", retry 5 lần rồi chuyển **FAILED**. Endpoint debug cũng bị đổi thành `/api/youtube/videos/{id}/items`.

Evidence:
- `YoutubeClientHttpTest.getReplies_sendsParentIdAndPageToken`, `getReplies_deserializesItemsArray`, `getReplies_pageInfoWithoutTotalResults_isParsed`: `was "https://yt.test/youtube/v3/items?part=snippet&parentId=top…"`
- Real API: `YoutubeApiException: 404 Not Found` (video `01yqAcaYsL0`)

Likely root cause: IDE rename `comments` → `items` (khi sửa DTO `YoutubeCommentListRepsonse`) đã thay luôn các string literal.

Suggested fix: trả path về `"/comments"` ở client, và `"/{videoId}/comments"` ở controller. **DO NOT IMPLEMENT THE FIX.**

### BUG-03 — `INSERT IGNORE` nuốt cả lỗi không phải duplicate

Severity: **High**

Location: `CommentIngestionRepository.insertIgnoreBatch`.

Scenario và Actual (đều đã kiểm chứng trên MySQL 8):

| Dữ liệu | Hành vi thực tế | Test |
|---|---|---|
| Thiếu `id` | lưu `id=''` | BUG-01 |
| `content_id` không tồn tại (FK) | không lỗi, 0 row, báo duplicate | `unknownContentId_failsLoudly` |
| `text = NULL` (cột NOT NULL) | lưu `''`, báo **inserted** | `nullText_isNotSilentlyCoercedToEmpty` |
| `external_comment_id` dài hơn 150 ký tự | bị truncate về 150; id thứ hai có cùng tiền tố bị coi là duplicate | `overlongExternalId_isNotSilentlyTruncated` |

Ngoài ra, `platform` là ENUM, nên một giá trị lạ sẽ bị lưu thành `''`.

Expected: chỉ duplicate key mới được bỏ qua; mọi lỗi khác phải ném exception để job retry hoặc FAILED và có log.

Likely root cause: `IGNORE` chuyển mọi lỗi có thể bỏ qua thành warning, và code không kiểm tra `SHOW WARNINGS`.

Suggested fix: dùng `INSERT … ON DUPLICATE KEY UPDATE id = id` (no-op) để các lỗi khác vẫn ném; hoặc kiểm tra warning sau batch; validate dữ liệu (text, độ dài id) trước khi insert. **DO NOT IMPLEMENT THE FIX.**

### BUG-04 — `insertedComments` / `duplicateComments` bỏ sót reply phải phân trang

Severity: **Medium**

Location: `YoutubeCommentCollector.collectAll`, nhánh `collectAllReplies(...)`: chỉ cộng `replyCount`, `pages`, `apiCalls`, bỏ qua `stats.inserted()` và `stats.duplicates()`.

Expected: `inserted + duplicates == totalComments`. Actual: thiếu toàn bộ reply lấy qua `comments.list`.

Evidence: `YoutubeCommentCollectorTest.insertedAndDuplicateCounts_includeEveryBatch`: `expected: 292L but was: 142L`.

Suggested fix: cộng `stats.inserted()` và `stats.duplicates()` vào tổng. **DO NOT IMPLEMENT THE FIX.**

### BUG-05 — `duplicates = received − inserted` gộp cả row không hợp lệ và row bị mất

Severity: **Medium**

Location: `CommentService.saveBatch`.

Expected: duplicate chỉ đếm comment đã tồn tại hoặc trùng trong batch. Comment có id null/blank là "invalid", không phải duplicate.

Actual:
- Batch `[valid, "", "  ", null]` cho kết quả `duplicates = 3`.
- Mọi row bị `INSERT IGNORE` bỏ qua vì lý do khác (BUG-01, BUG-03) cũng thành "duplicate". Metric che mất việc mất dữ liệu: job báo `duplicate=291` thay vì báo lỗi.

Evidence: `CommentServiceTest.invalidExternalIds_areNotReportedAsDuplicates` (expected 0, was 2); `nullOrBlankExternalId_isSkipped_andNotCountedAsDuplicate` (expected 0, was 3).

Suggested fix: tách `invalid` / `inBatchDuplicates` / `existingDuplicates`; duplicate từ DB chỉ tính khi đúng là lỗi duplicate key. **DO NOT IMPLEMENT THE FIX.**

### BUG-06 — `LogginCommentBatchHandler.handle` trả `null`

Severity: **Low** (class không còn là bean)

Location: `LogginCommentBatchHandler.handle`.

Actual: trả `null`. Nếu bật lại `@Component`, collector ném NPE tại `handle.inserted()`.

Suggested fix: trả `new CommentBatchResult(size, 0, 0)` và giữ log; hoặc xóa class. **DO NOT IMPLEMENT THE FIX.**

### BUG-07 — Key cấu hình worker không khớp yaml

Severity: **Low**

Location: `CrawlWorker` đọc `crawl.worker.enabled` và `crawl.worker.delay-ms`, còn `application.yaml` khai báo `crawler.worker.enabled` và `crawler.worker.delay-ms`.

Actual: không tắt được worker hay đổi delay qua yaml (mặc định `matchIfMissing = true`, 2000 ms).

Evidence: code review; test phải đặt `crawl.worker.enabled=false` mới tắt được.

Suggested fix: thống nhất một prefix. **DO NOT IMPLEMENT THE FIX.**

### Bug từ Day 6 vẫn còn mở

| Bug Day 6 | Trạng thái |
|---|---|
| BUG-01 `pageToken` | Đã fix |
| BUG-03 `items` trong DTO | Đã fix |
| BUG-02 `YoutubePageInfo.totalResults` kiểu `int` (`comments.list` không trả field này) | Còn: không đổi; đang bị BUG-02 Day 7 che |
| BUG-04 `fetch-replies` / `page-size` không tác dụng | Còn |
| BUG-05 loop token trên page rỗng | Còn |
| BUG-06 lỗi từ `comments.list` không được map | Còn |
| BUG-07 `collectPage.totalResults` luôn 0 | Còn |
| BUG-08 `totalReplyCount` null gây NPE | Còn |

## Production Code Issues

Chỉ mô tả, không sửa.

- **`likeCount` null:** `ps.setLong(7, comment.likeCount())` auto-unbox `Long` nên NPE nếu null. Mapper hiện luôn đặt giá trị, nên rủi ro thấp.
- **Batch thực chất là nhiều round-trip:** URL JDBC không có `rewriteBatchedStatements=true`, nên mỗi row vẫn là một statement riêng. Nếu sau này bật cờ đó, Connector/J trả `SUCCESS_NO_INFO (-2)` và `inserted` sẽ luôn là 0, vì code chỉ cộng giá trị `> 0`.
- **`CommentRepository`** (`existsBy…`, `findAllBy…In`, `countByContentId`) chưa được dùng ở luồng ingest.
- **`Content` ownership:** cùng external comment id ở hai Content thì row giữ content đầu tiên (first-wins). Hợp lệ với constraint global theo platform; chỉ ghi nhận.

## Known Limitations

Đúng với code hiện tại:

- Retry bắt đầu lại từ page 1 (không có checkpoint page/token). Dựa vào idempotency của DB, mà idempotency này hiện đang hỏng do BUG-01.
- Comment đã lưu không được cập nhật khi bị sửa trên YouTube (`editedComment_isNotRefreshed_knownLimitation`).
- Chưa có abbreviation detection; `has_abbreviation` luôn false.
- Chưa có lease/heartbeat cho job đang PROCESSING (từ Day 6).

## Real API Test

**FAIL.** Video `01yqAcaYsL0` (33 thread, có thread trên 5 reply). Ghi vào schema QA, chạy 2 lần:

| Lần | scanned | inserted | duplicates | DB rows |
|---|---|---|---|---|
| 1 | — (dừng giữa chừng: `YoutubeApiException 404` tại `/items`) | — | — | 1 |
| 2 | — (404 như trên) | — | — | 1 |

Page 1 có 33 top-level nhưng chỉ còn 1 row (BUG-01); crawl dừng ở thread đầu tiên cần `comments.list` (BUG-02).

Lệnh chạy:

```
.\mvnw.cmd test -Dtest=Day07RealApiPersistenceTest -Dreal-api=true "-DargLine=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Không ghi API key.

## Final Conclusion

```
DAY 07 FAIL
```

Phần còn tốt:
- Schema (unique constraint ở DB, FK, `utf8mb4`, TEXT).
- Mapping từng field, raw text, `hasAbbreviation`.
- Transaction ngắn theo batch.
- Vòng đời worker (DONE / PENDING / SKIPPED, `lastCrawledAt`).
- Dedup trong Java.

Nhưng mục tiêu cốt lõi của Day 7 ("persist toàn bộ comments/replies") **không đạt**: BUG-01 khiến chỉ 1 comment được lưu, và `INSERT IGNORE` (BUG-03) cộng với metric (BUG-05) che lỗi đó, nên job vẫn DONE. Với API thật, BUG-02 còn khiến mọi video có nhiều reply thất bại.

Điều kiện để chạy lại QA: sửa BUG-01, BUG-02, BUG-03 (tối thiểu không để IGNORE nuốt lỗi khác ngoài duplicate) và BUG-04. Sau đó chạy lại `Day07*IntegrationTest` và `Day07RealApiPersistenceTest`.
