# satmis.com

Sahibinden tarzı araç ilanı platformu. Üniversite bitirme projesi, ama sektöre hazırlık amacıyla gerçek proje standartlarında yazılır.

## Yapı
- Bu repo sadece backend'dir; Maven projesi repo kökündedir (`backend/` alt klasörü yok). Frontend (React + Vite) ayrı tutulacak, henüz yok.
- Ana paket `com.satmis`, feature-based: `auth`, `user`, `listing`, `vehicle`, `catalog`, `favorite`, `security`, `mail`, `storage`, `common`, `config`, `enums`
- Feature içinde: entity, repository, service, controller, mapper paketin kökünde; istek/cevap record'ları `dto/` alt paketinde.
- Durum: auth, user, catalog (salt okunur uçlar + seed) listing CRUD + arama, favoriler, e-posta doğrulama, şifre sıfırlama ve auth uçlarında rate limiting tamam. Görseller dosya olarak object storage'a yüklenir (dış URL yok). İlan silme hard delete.
- Listing yazma işlemleri `ListingService`, okuma işlemleri `ListingQueryService`'te. Liste uçları iki adımlı sorgu kullanır: önce Specification ile sayfanın id'leri, sonra join fetch ile satırlar. Sayfa başına sorgu sayısı sabittir (`ListingIT` bunu ölçer). Yeni liste ucu eklerken bu kalıbı koru.
- Anahtar kelime araması (`q`): PostgreSQL FTS, `satmis_search` yapılandırması (`simple` + `unaccent`, stemmer yok, önek eşleşmesi). Aranan metin `listings.search_text`'te (marka, model, seri, başlık, açıklama); `ListingService` her yazmada doldurur, ilanın aranan alanı değişirse orası da güncellenir. Kullanıcı girdisi `KeywordQuery` ile harf/rakam token'larına ayrılır, ham girdi `to_tsquery`'ye gitmez. SQL ifadesi `ListingSearchFunctions` (Hibernate `FunctionContributor`) ile V8'deki GIN index ifadesi birebir aynı olmalı; `ListingIT` index kullanımını `EXPLAIN` ile kontrol eder.
- Başka bir feature ilan listesi dönecekse kendi sorgusuyla sadece id sayfasını üretir ve `ListingQueryService.toSummaryPage` ile özetlere çevirir (favoriler böyle).
- Görsel yükleme (`ListingImageService`, `/api/listing-images`): `POST` satırı `PENDING` açar ve imzalı PUT URL'si döner; tarayıcı dosyayı depoya yükler; `POST /{id}/complete` dosyayı HEAD + ilk byte'larla (`ImageTypeDetector`, beyan edilen türe güvenilmez) kontrol edip `UPLOADED` yapar, uymazsa dosyayı silip `DELETED` yapar (`422 IMAGE_INVALID`). Durumlar `PENDING → UPLOADED → ATTACHED → DELETED`. Limitler `app.listing-images.*`'ta. Yüklemeden önce de `ListingOwnerPolicy` (doğrulanmış e-posta + telefon) çalışır. Başkasının görseli `404` döner.
- İlan isteği `imageIds` taşır (sıra = gösterim sırası, ilki kapak); `ListingImageService.replaceListingImages` her id'nin sahibin `UPLOADED` görseli ya da bu ilanın görseli olduğunu kontrol eder (değilse `422 IMAGE_NOT_AVAILABLE`), listeden düşenleri `DELETED` yapar. İlan silinirken `releaseImages` görselleri `DELETED` yapar. `Listing.images`'ta bilerek cascade/orphanRemoval yok: satır silinirse depodaki dosya sahipsiz kalır. URL'ler DB'de tutulmaz, `ObjectStorage.publicUrl(objectKey)` ile üretilir (mapper'a `@Context` ile verilir).
- Görsel silme: `ListingImageService` bir görseli `DELETED` yaptığında `ListingImagesDeletedEvent` yayınlar; `ListingImageDeletionListener` commit'ten sonra (`@Async`) `ListingImageCleanupService.purge` ile önce dosyayı, sonra satırı siler. DB tek doğruluk kaynağıdır: dosya silinemezse satır `DELETED` kalır ve `ListingImageCleanupJob` (saatlik, `app.listing-images.cleanup-cron`) tekrar dener. Aynı iş `app.listing-images.abandoned-after` (24 saat) içinde ilana eklenmeyen `PENDING`/`UPLOADED` görselleri de `DELETED` yapar. Bu toplu UPDATE `ListingImage.version`'ı elle artırır; aynı anda o görseli ilana ekleyen kayıt optimistic lock ile `409` alır (dosyası silinecek bir görsel ilana bağlanmaz).
- İlan testleri depo açmaz: `TestImages.uploaded` görseli JDBC ile `UPLOADED` olarak ekler.
- İlan detayı `ListingDetailAssembler` üzerinden kurulur (favori sayısı / favorilerimde mi bilgisi için).
- İlan güncellemede optimistic locking: istek `version` taşır; detay/görsel değişiklikleri de version'ı artırsın diye `forceVersionIncrement` kullanılır.
- Admin onayı: yeni ilan `PENDING_APPROVAL` açılır. `ListingService.update` başlık/açıklama/fiyat/para birimi/görsel seti değişirse (ya da ilan `REJECTED` ise) `submitForApproval` çağırır; km gibi diğer alanlar ve görsel sırası onayı bozmaz. Admin uçları `ListingModerationService`'te (`/api/admin/listings`): onay sadece `PENDING_APPROVAL`'da, red `PENDING_APPROVAL`/`ACTIVE`'de; ikisi de incelenen `version`'ı ister (sahibi arada düzenlediyse 409). Durum + `rejection_reason` sadece `Listing.approve/reject/submitForApproval` ile değişir (`ck_listings_rejection_reason`: neden sadece `REJECTED`'ta dolu). Testler yayındaki ilan için `TestListings.approve` (JDBC) kullanır.
- Katalog seed'i `src/main/resources/seed/*.json` dosyalarından açılışta `CatalogSeedRunner` ile yüklenir. Sadece eksik satırları ekler, mevcutları değiştirmez ya da silmez. Kapatmak için `app.seed.catalog-enabled=false`.
- Admin katalog yönetimi (`CatalogAdminService`, `/api/admin/catalog`) bilerek sadece ekleme yapar: seed satırları isimle eklendiği için adı değişen/silinen seed kaydı bir sonraki açılışta geri gelirdi. Adlandırma/silme istenirse önce seed mantığı değişmeli. Tekrar kontrolü Türkçe `Collator` (SECONDARY, harf büyüklüğü yok sayılır) ile Java'da; DB unique constraint'leri büyük/küçük harf duyarlıdır ve eşzamanlı aynı ismi yakalar (409).
- Sıralama Türkçe `Collator` ile Java tarafında yapılır (DB collation'ına güvenilmez).

## Teknoloji
Java 21, Spring Boot 4.1.x (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 `tools.jackson.*`), Maven, PostgreSQL, Spring Data JPA, Spring Security + JWT (jjwt 0.12.x), Lombok, MapStruct, Bean Validation, springdoc-openapi (Swagger UI), AWS SDK v2 S3 (JDK HTTP client'ı ile; Netty/Apache hariç), Testcontainers

Boot 4 notları:
- Jackson 3 paketleri `tools.jackson.*`; `@JsonInclude` gibi annotation'lar hâlâ `com.fasterxml.jackson.annotation`. JSON yazmak için Spring'in `JsonMapper` bean'i enjekte edilir.
- `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`. `@MockBean` yok, `@MockitoBean` kullanılır.
- Testcontainers 2.x: `org.testcontainers.postgresql.PostgreSQLContainer` (generic değil).

## Kodlama kuralları
- Katmanlar: Controller → Service → Repository. İş mantığı sadece Service'te.
- Entity API'den asla dönülmez. İstek/cevap için `record` DTO kullanılır.
- Entity ↔ DTO dönüşümü MapStruct ile (`@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`). İstemciden alınmaması gereken alanlar (rol, şifre vb.) mapper'da açıkça `ignore` edilir.
- Entity'lerde `@Data` ve `@ToString` yok. Tüm ilişkiler LAZY. Enum'lar `EnumType.STRING`. Para `BigDecimal`. Zaman `Instant`.
- Validation annotation'ları DTO'da durur, entity'de durmaz. Ortak regex'ler `common.ValidationPatterns`'ta.
- Record'larda eleman kısıtı (`List<@Pattern String>`) kullanma: STS/Eclipse derleyicisi (ECJ) record alanlarının generic imzasını bozuyor ve kısıt sessizce kayboluyor. Liste seviyesinde özel constraint yaz (örn. `@HttpsUrlList`).
- STS açıkken `target/classes`'a ECJ de yazar; Maven'ın ona güvenmemesi için testleri `./mvnw clean verify` ile çalıştır.
- `spring.jpa.open-in-view=false`: lazy veriye Service içinde (`@Transactional`) erişilir ve orada DTO'ya çevrilir. Okuma metotları `readOnly = true`.
- Hatalar `ApiException` + `GlobalExceptionHandler` ile standart `ErrorResponse` JSON'u olarak döner.
- Frontend'in durum kodundan ayırt edemeyeceği hatalar `ErrorCode` enum'una değer ekleyip `new ApiException(status, code, message)` ile atılır (örn. `PHONE_REQUIRED`); `code` diğer hatalarda JSON'a yazılmaz.
- Tipli konfigürasyon `@ConfigurationProperties` record'ları ile (`JwtProperties`, `CorsProperties`); dağınık `@Value` yok.
- Büyük/küçük harf dönüşümünde `Locale.ROOT` kullanılır (Türkçe locale'de `I` → `ı` sorunu). E-posta için `EmailNormalizer`.
- Kullanıcıya dönen mesajlar Türkçe, kod ve değişken isimleri İngilizce.
- Yorumlar kısa olur ve sadece "neden"i açıklar.

## Veritabanı
- Lokal geliştirme makinedeki PostgreSQL'e bağlanır (`application-dev.properties` varsayılanları, port 5432; şifre `.env`'deki `DB_PASSWORD`'den). `docker-compose.yml`'deki Postgres 16 alternatiftir ve 5432 dolu olduğu için host'ta 5433'te açılır.
- Şema Flyway ile yönetilir: `src/main/resources/db/migration/V<n>__<aciklama>.sql`. Hibernate `ddl-auto=validate` ile sadece entity–şema uyumunu kontrol eder; uyuşmazlıkta uygulama açılmaz.
- Entity değişikliği = yeni migration (`V2__add_xxx.sql` …). Uygulanmış bir migration dosyası asla değiştirilmez (Flyway checksum hatası verir); düzeltme de yeni migration'la yapılır.
- Enum kolonlarında CHECK constraint var: enum'a yeni değer eklemek migration gerektirir.
- Migration SQL'ini elle yazdıktan sonra `./mvnw clean verify` çalıştır; tüm IT'ler boş DB'de migration + validate ile açılır, `FlywayMigrationIT` migration durumunu kontrol eder.
- Lokal DB'yi sıfırlamak gerekirse veritabanını drop/create et; açılışta Flyway şemayı, `CatalogSeedRunner` katalog verisini kurar.

## Güvenlik
- Sır (JWT secret, şifre, token) koda, loga ya da Git'e yazılmaz. Ortam değişkeni kullanılır (`JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; örnek: `.env.example`).
- Lokalde bu değerler proje kökündeki git-ignored `.env` dosyasından okunur (`spring.config.import=optional:file:.env[.properties]`); STS/terminal/Maven aynı dosyayı görür. Sunucuda `.env` yoktur, gerçek ortam değişkeni/secret manager kullanılır ve ortam değişkeni `.env`'i ezer. Her ortamın kendi JWT secret'ı olur.

## Profiller
- `application.properties` ortak ve güvenli varsayılanları tutar (SQL logu ve Swagger kapalı, datasource yok). Profil seçilmezse uygulama açılmaz.
- `dev`: lokal makine. `.env`'de `SPRING_PROFILES_ACTIVE=dev`; SQL logu ve Swagger açık, DB adresi/kullanıcısı varsayılanlı, `DB_PASSWORD`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` zorunlu (değerleri `.env.example`'da).
- `prod`: sunucu. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, mail (`MAIL_*`, `FRONTEND_BASE_URL`) ve depolama (`STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_PUBLIC_BASE_URL`) zorunlu ve varsayılansız; Swagger kapalı (`SWAGGER_ENABLED=true` ile açılır, açıkken herkese açıktır).
- `test`: tüm `@SpringBootTest` sınıfları `@ActiveProfiles("test")` taşır; yoksa lokaldeki `.env` testleri `dev`'e sokar. DB'yi Testcontainers verir.
- `.env` içindeki `SPRING_PROFILES_ACTIVE` anahtarını Spring kendisi okumaz; `application.properties`'teki `spring.profiles.active=${SPRING_PROFILES_ACTIVE:}` eşlemesi bunu sağlar.
- Docker imajı (`Dockerfile`, çok aşamalı, root olmayan `spring` kullanıcısı) varsayılan olarak `prod` profilinde açılır; prod değişkenleri `docker run -e ...` ile verilir. İmaj build'inde testler çalışmaz (Testcontainers build içinde DB açamaz), testler CI'da koşar. `.env` `.dockerignore` ile imaja girmez.
- Eksik ortam değişkeni `RequiredEnvironmentInitializer` ile açılışta adıyla raporlanır (Spring çözülemeyen `${VAR}`'ı düz metin olarak bırakır). Profile yeni zorunlu değişken eklenirse oraya da eklenir.
- Şifre BCrypt ile hash'lenir. Refresh token DB'de SHA-256 hash'i olarak tutulur; rotasyon ve reuse algılama var. İptal edilen token'lar reuse algılama için süreleri dolana kadar tutulur; `RefreshTokenCleanupJob` süresi dolanları her gün siler (`app.jwt.refresh-token-cleanup-cron`).
- Refresh token httpOnly cookie'de de taşınır (`RefreshTokenCookies`: `satmis_refresh`, `Path=/api/auth`, `SameSite=Strict`, dev'de `Secure` kapalı). Refresh token cevap gövdesinde yoktur (`AuthSession` onu sadece controller'a taşır). Login/register/refresh cookie yazar, logout siler; refresh/logout token'ı sadece cookie'den okur ve `Origin` CORS listesinde olmalıdır (yoksa 403, CSRF koruması). İzinsiz origin'i CORS katmanı da standart hata JSON'uyla reddeder (`JsonCorsProcessor`).
- Rotasyon koşullu tek bir UPDATE'tir (`RefreshTokenRepository.rotate`, `revoked = false` şartıyla): aynı token'la eşzamanlı gelen isteklerden sadece biri kazanır, diğerleri `REFRESH_TOKEN_ROTATED` alır. Rotasyonla iptal edilen token `rotated_at` taşır; `app.jwt.refresh-reuse-grace` (30 sn) içinde tekrar gelirse başka sekme sayılır: `401 REFRESH_TOKEN_ROTATED`, oturumlar kapanmaz. Daha geç gelirse reuse algılama tüm oturumları kapatır.
- Rol hiçbir zaman istemciden alınmaz. İlk admin `APP_ADMIN_EMAILS` (virgüllü, opsiyonel; lokalde `.env`, git'e e-posta yazılmaz) ile atanır: `AdminBootstrapRunner` açılışta listedeki kayıtlı kullanıcıları ADMIN yapar, kimseyi düşürmez.
- Admin uçları `/api/admin/**` (SecurityConfig) + controller'da `@PreAuthorize("hasRole('ADMIN')")`. Kullanıcı engelleme (`AdminUserService`): `enabled = false` + refresh token'ları iptal; access token her istekte `enabled` kontrolüne takılır. Admin kendini/başka admini engelleyemez (422).
- Herkese görünür ilan = `ACTIVE` ve sahibi `enabled` (`Listing.isPubliclyVisible`, `ListingSpecifications.publiclyVisibleMatching`, favori sorgusu). Engelli kullanıcının ilanlarının durumu değişmez, engel kalkınca geri gelir. Yeni bir herkese açık ilan sorgusu bu kuralı uygulamalı.
- Rate limiting: `RateLimitFilter` (Bucket4j, bellek içi, IP başına) `POST /api/auth/login|register|refresh|forgot-password|reset-password`, `POST /api/users/me/email-verification` ve `POST /api/listing-images` (depoyu doldurmaya karşı) uçlarını sınırlar; aşılınca `429` + `Retry-After` + standart `ErrorResponse`. Limitler `app.rate-limit.*`'ta. Testlerde `application-test.properties` ile kapalı, `RateLimitIT` açıp dener. Yeni bir e-posta/SMS gönderen uç eklenirse oraya da limit eklenir. Loglara IP yazılmaz. Reverse proxy arkasında (deploy) gerçek IP için `server.forward-headers-strategy` ayarlanmalı.
- Gönderilmiş ama geçersiz/süresi dolmuş Bearer token açık uçlarda da 401 döner (sessizce anonim sayılmaz, yoksa istemci token yenilemez). Token hiç yoksa istek anonimdir. `/api/auth/**` token'a bakmaz.
- E-posta doğrulama: kayıtta `EmailVerificationService.issueFor` token üretir (DB'de SHA-256, 24 saat, kullanıcı başına sadece en yenisi geçerli). Mail commit'ten sonra `EmailVerificationMailListener` ile gider; mail hatası isteği bozmaz, kullanıcı `POST /api/users/me/email-verification` ile yeniden ister. Doğrulanmamış kullanıcı ilan veremez (`422 EMAIL_NOT_VERIFIED`). Token üretme/hash'leme `SecureTokens`'ta.
- Şifre sıfırlama: `PasswordResetService`. `forgot-password` kayıtlı olmayan e-postada da aynı `204`'ü döner (hesap varlığı sızmaz); token 1 saat, tek kullanımlık. Başarılı sıfırlama tüm refresh token'ları iptal eder ve e-postayı doğrulanmış sayar.
- Mail listener'ları `@Async` + `@TransactionalEventListener`: commit'ten sonra, istek thread'i dışında gönderilir (SMTP gecikmesi cevaba yansımaz; forgot-password'da zamanlamadan hesap varlığı anlaşılmasın diye de gerekli).
- Dosya depolama sadece `storage.ObjectStorage` üzerinden (adapter; SDK tipleri/hataları dışarı sızmaz, hata `StorageException`). S3 API'si: prod'da Cloudflare R2, dev'de docker-compose'daki SeaweedFS (`docker compose up -d seaweedfs seaweedfs-init`, okuma `http://localhost:8333/satmis-images/<key>`). MinIO kullanılmıyor: hazır imajı artık yayınlanmıyor. Yükleme tarayıcıdan imzalı PUT URL'si ile yapılır; tür, boyut ve `Cache-Control` imzadadır, farklı dosyayı depolama `403` ile reddeder. Bucket herkese okunur, yazma ve listeleme anahtar ister (`docker/seaweedfs/s3.json`, sadece lokal anahtarlar). Zaman aşımı ve retry `StorageConfig`'te; client tembel bağlanır, depolama kapalıyken uygulama açılır.
- Mail gönderimi sadece `mail.MailService` üzerinden (adapter; alıcı adresi loglanmaz). Dev'de Mailpit (`docker compose up -d mailpit`, UI `http://localhost:8025`), prod'da SMTP sağlayıcı (`MAIL_*` değişkenleri). SMTP zaman aşımları `application.properties`'te.
- Telefon kayıt ve profilde zorunlu. Satıcı telefonu sadece ilan detayında ve sadece giriş yapmış kullanıcıya döner (anonimde `null`); liste uçları telefon içermez, e-posta hiç dönmez.
- Kullanıcı yalnızca kendi kaydını değiştirebilir (sahiplik kontrolü Service'te yapılır; kullanıcı id'si URL'den değil token'dan alınır).
- Yetki kuralları `SecurityConfig`'te: `/api/auth/**`, `GET /api/listings/**`, `GET /api/catalog/**` ve Swagger yolları açık; `/api/admin/**` sadece ADMIN; geri kalanı kimlik ister.

## Test
- Birim testleri `*Test(s)` → surefire; entegrasyon testleri `*IT` → failsafe (`./mvnw verify`). Docker açık olmalı.
- Entegrasyon testleri Testcontainers + `@ServiceConnection` ile gerçek PostgreSQL 16 kullanır; JWT secret `TestJwtSecret.random()` ile `@DynamicPropertySource` üzerinden verilir.
- `application-test.properties` (test kaynaklarında) rate limiting, mail gönderimini ve zamanlanmış görsel temizliğini kapatır (testler `ListingImageCleanupService.cleanUp`'ı kendisi çağırır). İlan oluşturan testler kullanıcıyı JDBC ile doğrulanmış yapar. `EmailVerificationIT` ve `PasswordResetIT` gerçek Mailpit container'ı açıp maili HTTP API'sinden okur (`MailpitInbox` yardımcı sınıfı). Depolamaya dokunan IT'ler `SeaweedFsContainer` açar (compose ile aynı imaj ve `s3.json`, bucket'ı kendisi oluşturur) ve `registerStorageProperties` ile bağlanır. Diğer testlerde depo adresi kapalı bir porttur: silinen görselin dosya silme denemesi başarısız olur ve satır `DELETED` kalır (`ListingIT` durumu buna göre kontrol eder).
- Frontend sözleşmesindeki değişiklikler (yeni uç, hata kodu, alan) `docs/frontend-api.md`'ye de yazılır. Frontend'e iş gerekiyorsa `docs/frontend-gorev-*.md` görev belgesi hazırlanır.
- STS Boot Dashboard'da uygulama açıkken devtools her kayıtta yeniden başlar ve Flyway yeni migration'ı lokal DB'ye hemen uygular; migration dosyasını bitmeden kaydetme ya da STS'deki uygulamayı durdur.
- Test adı: `method_condition_expectedResult`. Testi geçirmek için test zayıflatılmaz.
- CI: `.github/workflows/ci.yml`, `main`'e push ve PR'larda `./mvnw clean verify` ve ardından Docker imaj build'i (push etmez). Hata olursa test raporları artifact olarak yüklenir.
- `mvnw` git'te çalıştırılabilir (`100755`) olmalı; Windows bunu göstermez ama Linux CI/Docker ister.

## Çalışma kuralları
- Her adımdan sonra derle (`./mvnw -q -DskipTests compile`, Windows PowerShell'de `.\mvnw.cmd`), hatayı düzelt, sonra devam et. İş bitince `./mvnw verify` çalıştır ve sonucu raporla.
- Listede olmayan bağımlılık ekleme. Gerekirse nedenini yazıp ekle.
- `git commit` ve `git push` yapma, bunu kullanıcı yapar.
- Var olan dosyaları sormadan silme veya ezme. `application.properties`'i sormadan değiştirme.
- Swagger UI (sadece `dev`): `http://localhost:8080/swagger-ui.html`
