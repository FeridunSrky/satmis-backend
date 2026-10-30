# satmis.com

Sahibinden tarzı araç ilanı platformu. Üniversite bitirme projesi, ama sektöre hazırlık amacıyla gerçek proje standartlarında yazılır.

## Yapı
- Bu repo sadece backend'dir; Maven projesi repo kökündedir (`backend/` alt klasörü yok). Frontend (React + Vite) ayrı tutulacak, henüz yok.
- Ana paket `com.satmis`, feature-based: `auth`, `user`, `listing`, `vehicle`, `catalog`, `favorite`, `security`, `common`, `config`, `enums`
- Feature içinde: entity, repository, service, controller, mapper paketin kökünde; istek/cevap record'ları `dto/` alt paketinde.
- Durum: auth, user, catalog (salt okunur uçlar + seed) listing CRUD + arama ve favoriler tamam. Görseller şimdilik sadece https URL listesi (dosya yükleme yok). İlan silme hard delete.
- Listing yazma işlemleri `ListingService`, okuma işlemleri `ListingQueryService`'te. Liste uçları iki adımlı sorgu kullanır: önce Specification ile sayfanın id'leri, sonra join fetch ile satırlar. Sayfa başına sorgu sayısı sabittir (`ListingIT` bunu ölçer). Yeni liste ucu eklerken bu kalıbı koru.
- Başka bir feature ilan listesi dönecekse kendi sorgusuyla sadece id sayfasını üretir ve `ListingQueryService.toSummaryPage` ile özetlere çevirir (favoriler böyle).
- İlan detayı `ListingDetailAssembler` üzerinden kurulur (favori sayısı / favorilerimde mi bilgisi için).
- İlan güncellemede optimistic locking: istek `version` taşır; detay/görsel değişiklikleri de version'ı artırsın diye `forceVersionIncrement` kullanılır.
- Katalog seed'i `src/main/resources/seed/*.json` dosyalarından açılışta `CatalogSeedRunner` ile yüklenir. Sadece eksik satırları ekler, mevcutları değiştirmez ya da silmez. Kapatmak için `app.seed.catalog-enabled=false`.
- Sıralama Türkçe `Collator` ile Java tarafında yapılır (DB collation'ına güvenilmez).

## Teknoloji
Java 21, Spring Boot 4.1.x (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 `tools.jackson.*`), Maven, PostgreSQL, Spring Data JPA, Spring Security + JWT (jjwt 0.12.x), Lombok, MapStruct, Bean Validation, springdoc-openapi (Swagger UI), Testcontainers

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
- `dev`: lokal makine. `.env`'de `SPRING_PROFILES_ACTIVE=dev`; SQL logu ve Swagger açık, DB adresi/kullanıcısı varsayılanlı, `DB_PASSWORD` zorunlu.
- `prod`: sunucu. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` zorunlu ve varsayılansız; Swagger kapalı (`SWAGGER_ENABLED=true` ile açılır, açıkken herkese açıktır).
- `test`: tüm `@SpringBootTest` sınıfları `@ActiveProfiles("test")` taşır; yoksa lokaldeki `.env` testleri `dev`'e sokar. DB'yi Testcontainers verir.
- `.env` içindeki `SPRING_PROFILES_ACTIVE` anahtarını Spring kendisi okumaz; `application.properties`'teki `spring.profiles.active=${SPRING_PROFILES_ACTIVE:}` eşlemesi bunu sağlar.
- Docker imajı (`Dockerfile`, çok aşamalı, root olmayan `spring` kullanıcısı) varsayılan olarak `prod` profilinde açılır; prod değişkenleri `docker run -e ...` ile verilir. İmaj build'inde testler çalışmaz (Testcontainers build içinde DB açamaz), testler CI'da koşar. `.env` `.dockerignore` ile imaja girmez.
- Eksik ortam değişkeni `RequiredEnvironmentInitializer` ile açılışta adıyla raporlanır (Spring çözülemeyen `${VAR}`'ı düz metin olarak bırakır). Profile yeni zorunlu değişken eklenirse oraya da eklenir.
- Şifre BCrypt ile hash'lenir. Refresh token DB'de SHA-256 hash'i olarak tutulur; rotasyon ve reuse algılama var.
- Rol hiçbir zaman istemciden alınmaz.
- Gönderilmiş ama geçersiz/süresi dolmuş Bearer token açık uçlarda da 401 döner (sessizce anonim sayılmaz, yoksa istemci token yenilemez). Token hiç yoksa istek anonimdir. `/api/auth/**` token'a bakmaz.
- Telefon kayıt ve profilde zorunlu. Satıcı telefonu sadece ilan detayında ve sadece giriş yapmış kullanıcıya döner (anonimde `null`); liste uçları telefon içermez, e-posta hiç dönmez.
- Kullanıcı yalnızca kendi kaydını değiştirebilir (sahiplik kontrolü Service'te yapılır; kullanıcı id'si URL'den değil token'dan alınır).
- Yetki kuralları `SecurityConfig`'te: `/api/auth/**`, `GET /api/listings/**`, `GET /api/catalog/**` ve Swagger yolları açık; `/api/admin/**` sadece ADMIN; geri kalanı kimlik ister.

## Test
- Birim testleri `*Test(s)` → surefire; entegrasyon testleri `*IT` → failsafe (`./mvnw verify`). Docker açık olmalı.
- Entegrasyon testleri Testcontainers + `@ServiceConnection` ile gerçek PostgreSQL 16 kullanır; JWT secret `TestJwtSecret.random()` ile `@DynamicPropertySource` üzerinden verilir.
- Test adı: `method_condition_expectedResult`. Testi geçirmek için test zayıflatılmaz.

## Çalışma kuralları
- Her adımdan sonra derle (`./mvnw -q -DskipTests compile`, Windows PowerShell'de `.\mvnw.cmd`), hatayı düzelt, sonra devam et. İş bitince `./mvnw verify` çalıştır ve sonucu raporla.
- Listede olmayan bağımlılık ekleme. Gerekirse nedenini yazıp ekle.
- `git commit` ve `git push` yapma, bunu kullanıcı yapar.
- Var olan dosyaları sormadan silme veya ezme. `application.properties`'i sormadan değiştirme.
- Swagger UI (sadece `dev`): `http://localhost:8080/swagger-ui.html`
