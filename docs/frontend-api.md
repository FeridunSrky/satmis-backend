# satmis.com — Frontend API devir notu

Bu not, frontend'in henüz kullanmadığı backend uçlarını (katalog, ilan, favori) anlatır. Auth ve profil uçlarındaki değişiklikler: telefon numarası (bkz. "Telefon numarası ve satıcı iletişimi"), e-posta doğrulama, şifre sıfırlama, istek sınırı ve refresh token cookie'si (bkz. "E-posta doğrulama", "Şifre sıfırlama", "İstek sınırı (429)", "Refresh token cookie'si"). İlan görselleri artık dosya olarak yüklenir (bkz. "İlan görselleri (dosya yükleme)"); ilan gövdesindeki `imageUrls` kaldırıldı, yerine `imageIds` geldi.

- **Tek doğruluk kaynağı OpenAPI'dir:** backend çalışırken `http://localhost:8080/v3/api-docs` (Swagger UI: `/swagger-ui.html`). TypeScript tiplerini oradan üretmek önerilir (openapi-typescript / orval). Bu nottaki tipler elle yazılmıştır ve kuralları açıklamak içindir.
- Geliştirmede istekler Vite proxy üzerinden gider (`/api` → `http://localhost:8080`), CORS gerekmez.
- Backend'i başlatmak için proje kökünde `.env` olmalı (`.env.example`'dan kopyala: `SPRING_PROFILES_ACTIVE=dev`, `JWT_SECRET`, `DB_PASSWORD`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`): `.\mvnw.cmd spring-boot:run`.
- E-postalar geliştirmede Mailpit'e gider: `docker compose up -d mailpit`, gelen kutusu `http://localhost:8025`.
- Görsel dosyaları geliştirmede SeaweedFS'e gider (S3 uyumlu depo, prod'da Cloudflare R2): `docker compose up -d seaweedfs seaweedfs-init`. Görseller `http://localhost:8333/satmis-images/...` adresinden herkese açık okunur.

## Genel kurallar

| Konu | Kural |
|---|---|
| Hata gövdesi | Her hatada `{ timestamp, status, code?, message, path, errors? }`. `message` Türkçe ve kullanıcıya gösterilebilir. `code` sadece frontend'in özel davranması gereken hatalarda gelir (şu an: `PHONE_REQUIRED`, `EMAIL_NOT_VERIFIED`, `REFRESH_TOKEN_ROTATED`, `IMAGE_LIMIT_REACHED`, `IMAGE_NOT_UPLOADED`, `IMAGE_INVALID`, `IMAGE_NOT_AVAILABLE`, `SALE_PRICE_OUT_OF_RANGE`); diğer hatalarda alan hiç yoktur. |
| Alan hataları | `errors` alan → mesaj haritası. İç içe alanlar noktalı (`vehicle.modelYear`). Görsel listesi hataları tek anahtar altında döner (`imageIds`). Bilinmeyen enum / sayı olmayan değer: `"Geçersiz değer"`. |
| Tarih | ISO-8601 UTC metin (`2026-10-26T11:35:00.750999Z`). Gösterirken yerel saate çevrilmeli. |
| Para | Cevapta JSON sayısı (`650000.50`). İstekte sayı veya metin (`"650000.50"`) kabul edilir. Kur çevirisi yok; her ilanın kendi `currency`'si var. |
| Enum'lar | Büyük harf İngilizce değerler. Türkçe etiketler frontend'in işi (öneri tablosu aşağıda). |
| Id'ler | Sayı (`number`). Path'te sayı olmayan id → 400. |
| Kimlik | Korumalı uçlarda `Authorization: Bearer <accessToken>`. Token yoksa/geçersizse 401, yetki yoksa 403. Açık uçlarda (`GET /api/listings/**`, `/api/catalog/**`) token göndermek isteğe bağlıdır: token **hiç yoksa** misafir olarak 200; token **gönderilmiş ama süresi dolmuş/geçersizse 401** döner (mevcut refresh interceptor'ı yenileyip isteği tekrarlar). `/api/auth/**` uçları gönderilen token'a hiç bakmaz. |
| Oluşturma | `201 Created` + `Location` header'ı + oluşan kaydın gövdesi. |
| İstek sınırı | Bazı uçlar IP başına sınırlıdır; aşılınca `429` (bkz. "İstek sınırı (429)"). |

## Telefon numarası ve satıcı iletişimi

Alıcılar satıcıya telefonla ulaşır; bu yüzden telefon zorunludur.

| Uç | Kural |
|---|---|
| `POST /api/auth/register` | `phone` **zorunlu**, desen `^\+?[0-9]{10,15}$` (örn. `+905321234567`, `05321234567`). Eksik/geçersiz → 400, `errors.phone`. |
| `PUT /api/users/me` | `phone` **zorunlu**; değiştirilebilir ama boşaltılamaz. Eksik/boş/geçersiz → 400, `errors.phone`, kayıtlı numara değişmez. |
| `POST /api/listings` | Kullanıcının profilinde telefon yoksa → 422, `code: "PHONE_REQUIRED"`, mesaj "İlan vermek için profilinize telefon numarası ekleyin". Frontend bu durumda kullanıcıyı profil sayfasına yönlendirmeli. Kayıtta telefon zorunlu olduğu için normalde oluşmaz; eski/bozuk kayıtlara karşı korumadır. |
| `GET /api/listings/{id}` | `seller.phone`: istek geçerli access token ile geliyorsa satıcının numarası, anonim istekte `null` (botların numara toplamasını zorlaştırmak için). `seller` içinde e-posta hiç dönmez. |
| Liste uçları | `GET /api/listings`, `/api/users/me/listings`, `/api/users/me/favorites` telefon **döndürmez**. |

422'yi ayırt etmek için durum koduna değil `code` alanına bakın: ilan oluştururken 422 başka sebeplerle de döner (seri/ilçe bulunamadı) ve onlarda `code` yoktur.

```ts
if (error.status === 422 && error.code === 'PHONE_REQUIRED') navigate('/profil');
```

## E-posta doğrulama

Kayıttan sonra kullanıcıya doğrulama linki e-postayla gider. Doğrulanmamış kullanıcı giriş yapabilir, gezebilir, favori ekleyebilir; **ilan veremez**.

| Uç / alan | Kural |
|---|---|
| `UserResponse.emailVerified` | `boolean`. Kayıt, giriş, `GET /api/users/me` cevaplarında gelir. Yeni kullanıcıda `false`. |
| E-postadaki link | `<frontend>/verify-email?token=<token>`. Frontend'de **`/verify-email` sayfası** gerekir: `token`'ı okuyup aşağıdaki ucu çağırır. Link 24 saat geçerli. |
| `POST /api/auth/verify-email` | Gövde `{ "token": "..." }`, token gerekmez. Başarılı → `204`. Geçersiz, kullanılmış veya süresi dolmuş link → `400`, mesaj "Doğrulama linki geçersiz veya süresi dolmuş, yeni bir link isteyin" (üç durum ayırt edilmez). |
| `POST /api/users/me/email-verification` | Giriş gerekir, gövde yok. Yeni link gönderir → `204`; **önceki linkler geçersiz olur**. Zaten doğrulanmışsa → `409`. 10 dakikada 3 istek sınırı var (`429`). |
| `POST /api/listings` | Doğrulanmamış kullanıcı → `422`, `code: "EMAIL_NOT_VERIFIED"`, mesaj "İlan vermek için e-posta adresinizi doğrulayın". Bu kontrol telefon kontrolünden önce yapılır. |

Önerilen davranış: `emailVerified === false` iken üstte "E-postanı doğrula · Tekrar gönder" bandı; ilan verme sayfasında `EMAIL_NOT_VERIFIED` gelirse aynı bandı/uyarıyı göster. Doğrulama sonrası `GET /api/users/me` ile kullanıcıyı yenile.

```ts
if (error.status === 422 && error.code === 'EMAIL_NOT_VERIFIED') showVerifyEmailBanner();
```

## Şifre sıfırlama

"Şifremi unuttum" akışı. İki uç da token gerektirmez.

| Uç | Kural |
|---|---|
| `POST /api/auth/forgot-password` | Gövde `{ "email": "..." }`. **Her zaman `204`**: e-posta kayıtlı olsun olmasın aynı cevap (hangi adreslerin kayıtlı olduğu dışarı sızmasın diye). Kayıtlıysa sıfırlama linki gider. Geçersiz e-posta biçimi → `400`. Ekranda "E-posta adresin kayıtlıysa sıfırlama linki gönderdik" gibi tarafsız bir mesaj göster. |
| E-postadaki link | `<frontend>/reset-password?token=<token>`. Frontend'de **`/reset-password` sayfası** gerekir: yeni şifre formu, `token`'ı URL'den alır. Link **1 saat** geçerli, tek kullanımlık; yeni istek eski linkleri geçersiz kılar. |
| `POST /api/auth/reset-password` | Gövde `{ "token": "...", "newPassword": "..." }`. Başarılı → `204`. Şifre 8–64 karakter değilse → `400`, `errors.newPassword` (link geçerliliğini korur, kullanıcı tekrar deneyebilir). Geçersiz/kullanılmış/süresi dolmuş link → `400`, mesaj "Şifre sıfırlama linki geçersiz veya süresi dolmuş, yeni bir link isteyin" (`errors` yok). |

Başarılı sıfırlamadan sonra: kullanıcının **tüm oturumları kapanır** (refresh token'lar iptal; elindeki access token en fazla 15 dk daha geçerli kalır). E-posta da doğrulanmış sayılır (`emailVerified: true`). Frontend kullanıcıyı giriş sayfasına yönlendirip "Şifren değişti, yeni şifrenle giriş yap" demeli; tarayıcıda o kullanıcıya ait token varsa silmeli.

## Refresh token cookie'si

Refresh token **sadece httpOnly cookie** ile taşınır; cevap gövdesinde yoktur ve gövdeyle gönderilen token dikkate alınmaz. JavaScript bu cookie'yi okuyamaz; XSS olsa bile refresh token çalınamaz.

| Konu | Kural |
|---|---|
| Cookie | `satmis_refresh`; `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, `Max-Age` 7 gün; prod'da `Secure`. Login, register ve refresh cevaplarında `Set-Cookie` ile gelir. Sadece `/api/auth/**` isteklerine eklenir. |
| Access token | Değişmedi: gövdede `accessToken` gelir, `Authorization: Bearer` ile gönderilir. **Sadece bellekte** tutulmalı (localStorage/sessionStorage değil). Sayfa yenilenince kaybolur; uygulama açılışında `POST /api/auth/refresh` ile yenisi alınır. |
| `POST /api/auth/refresh` | Gövdesiz. Token cookie'den okunur. Başarılı → `200` + yeni `accessToken` + yeni cookie (rotasyon). Cookie yoksa → `401` "Refresh token bulunamadı" (gövdede token gönderilse bile). |
| `POST /api/auth/logout` | Gövdesiz. Cookie'deki token iptal edilir; cevap her zaman `204` ve cookie silinir (`Max-Age=0`). |
| Origin kontrolü | Cookie'li istekte `Origin` header'ı izinli frontend adreslerinden biri olmalı, yoksa `403` "İstek kaynağı doğrulanamadı" (CSRF koruması). İzinli olmayan bir origin'den gelen **her** istek CORS katmanında aynı `403` ve standart hata gövdesiyle reddedilir. Tarayıcı `fetch`/axios POST'ta `Origin`'i kendisi ekler. Vite proxy'de `Origin` `http://localhost:5173` olarak gider ve izinlidir. |
| Çok sekme | Aynı refresh token iki sekmeden aynı anda ya da birkaç saniye arayla gelirse sadece biri yeniler; diğeri `401` + `code: "REFRESH_TOKEN_ROTATED"` alır ve oturum **kapanmaz**: cookie o sırada diğer sekmenin aldığı yeni token'la güncellenmiştir. Frontend bu kodda refresh'i **bir kez** tekrarlamalı. Kodsuz `401` → oturum bitti, çıkış. (Tolerans 30 sn; daha eski bir token'ın tekrar gelmesi çalınma sayılır ve kullanıcının tüm oturumları kapanır.) |
| Rotasyonun atomikliği | Eski token'ın iptali ve yeni token'ın kaydı **tek DB transaction'ında** yapılır; yeni cookie cevaba ancak commit'ten sonra yazılır. Arada herhangi bir hata olursa transaction geri alınır: hata cevabında `Set-Cookie` yoktur ve **eski cookie geçerli kalır** (`RefreshRotationFailureIT` bunu doğrular). |
| `500` | Uygulamanın standart hata gövdesiyle gelir (`message: "Beklenmeyen bir hata oluştu"`). Commit'ten sonra 500 oluşmaz (commit'ten sonra sadece cookie ve JSON yazılır); rotasyon geri alınmıştır. Aynı cookie ile tekrar denemek güvenlidir. |
| `502` / `503` / `504` | Backend uygulamasından gelmez. Refresh 503 üretmez (uygulamanın tek 503'ü görsel deposu hatasıdır). `503`: Tomcat kapanırken (graceful shutdown) açık bağlantıdaki isteğe verdiği gövdesiz cevap. `502` `text/plain`: Vite proxy backend'e bağlanamadı. İkisinde de istek refresh koduna ulaşmamıştır, rotasyon olmamıştır; aynı cookie ile tekrar denemek güvenlidir. Dev'de bunlar DevTools'un kod değişince uygulamayı yeniden başlatmasından gelir (birkaç saniye). Prod'da da deploy/yeniden başlatma sırasında kısa süre görülebilir. |
| Bağlantı cevap yolda koparsa | Tek istisna: sunucu rotasyonu commit edip cevabı yazarken bağlantı koparsa (ör. süreç tam o anda öldürülürse) tarayıcı ağ hatası/502 görür ve cookie güncellenmemiştir. Aynı cookie ile tekrar deneme 30 sn içinde `401` + `REFRESH_TOKEN_ROTATED` alır; cookie değişmediği için "bir kez" tekrarı da aynı cevabı alır. Frontend bunu oturum bitti sayıp kullanıcıyı girişe yönlendirmeli ve **daha fazla denememeli**: 30 sn'den sonra aynı token'la gelen istek çalınma sayılır ve kullanıcının tüm oturumları kapanır. Hata gövdeleri RFC 7807 `ProblemDetail` değil, yukarıdaki standart `{ timestamp, status, code?, message, path, errors? }` biçimindedir; 502/503/504 bu biçimde gelmez. |
| İstek ayarı | Vite proxy ile (aynı origin) cookie otomatik gider. Frontend API'yi başka origin'den çağırırsa auth isteklerinde `withCredentials: true` gerekir; backend CORS'ta `Access-Control-Allow-Credentials: true` döner. |
| Deploy | `SameSite=Strict` için frontend ve API aynı siteden olmalı (ör. `satmis.com` + `api.satmis.com`, ya da aynı origin + reverse proxy). Farklı sitelerde (ör. `*.vercel.app` + `*.onrender.com`) cookie gönderilmez. |

```ts
// refresh interceptor özeti
try { await refresh(); }
catch (error) {
  if (error.status === 401 && error.code === 'REFRESH_TOKEN_ROTATED') await refresh(); // bir kez
  else throw error; // 401: çıkış; 429: oturumu silme
}
```

## İstek sınırı (429)

Aşağıdaki uçlar IP başına sınırlıdır. Aşılınca `429 Too Many Requests`, standart hata gövdesi ve `Retry-After` header'ı (saniye) döner; mesaj kullanıcıya gösterilebilir ("Çok fazla deneme yaptınız, lütfen 60 saniye sonra tekrar deneyin").

| Uç | Sınır |
|---|---|
| `POST /api/auth/login` | dakikada 10 |
| `POST /api/auth/register` | 10 dakikada 5 |
| `POST /api/auth/refresh` | dakikada 30 |
| `POST /api/users/me/email-verification` | 10 dakikada 3 |
| `POST /api/auth/forgot-password` | 15 dakikada 5 |
| `POST /api/auth/reset-password` | 15 dakikada 10 |
| `POST /api/listing-images` | 10 dakikada 60 (yükleme URL'si isteği; dosyanın depoya PUT'u sayılmaz) |
| `POST /api/listings/{id}/reports` | saatte 10 (tüm ilanlar için toplam) |

Refresh interceptor'ı `429`'u oturum düşmesi gibi ele almamalı (kullanıcıyı çıkışa atmamalı); `Retry-After` kadar bekleyip tekrar denenebilir.

## Sayfalı cevap

Liste uçlarının hepsi aynı sayfa yapısını döner (Spring'in `Page` JSON'u değil, sabit bir sözleşme):

```ts
interface PageResponse<T> {
  content: T[];
  page: number;          // 0'dan başlar
  size: number;
  totalElements: number;
  totalPages: number;
}
```

Sayfa parametreleri: `page` (≥ 0, varsayılan 0), `size` (1–50, varsayılan 20). Sınır dışı değer → 400 ve `errors.page` / `errors.size`.

## Katalog (herkese açık, token gerekmez)

| Metot | URL | Cevap |
|---|---|---|
| GET | `/api/catalog/brands` | `CatalogItem[]` |
| GET | `/api/catalog/brands/{brandId}/models` | `CatalogItem[]` — marka yoksa 404 "Marka bulunamadı" |
| GET | `/api/catalog/models/{modelId}/series` | `CatalogItem[]` — model yoksa 404 "Model bulunamadı" |
| GET | `/api/catalog/cities` | `CatalogItem[]` (81 il) |
| GET | `/api/catalog/cities/{cityId}/districts` | `CatalogItem[]` — il yoksa 404 "İl bulunamadı" |

```ts
interface CatalogItem { id: number; name: string; }
```

- Listeler sayfalı değildir (dropdown verisi, küçük) ve **Türkçe alfabeye göre sıralı** gelir; frontend yeniden sıralamamalı.
- Hiyerarşi: marka → model → seri (seri = motor seçeneği, örn. Clio → `1.0 TCe`); il → ilçe.
- Veri nadiren değişir; TanStack Query'de uzun `staleTime` uygundur.

## İlan görselleri (dosya yükleme)

Dosya backend'den geçmez: tarayıcı onu **doğrudan depoya** (dev'de SeaweedFS, prod'da Cloudflare R2) backend'in verdiği kısa ömürlü, imzalı bir URL ile yükler. Her görsel için akış:

```
1. POST /api/listing-images  { contentType, sizeBytes }   → 201 { id, uploadUrl, uploadHeaders, expiresAt }
2. PUT uploadUrl  (gövde: dosyanın kendisi, header: uploadHeaders)   → depodan 200
3. POST /api/listing-images/{id}/complete                  → 200 { id, url }
4. İlanı kaydederken id'leri imageIds içinde gönder
```

| Metot | URL | Kimlik | Başarılı cevap |
|---|---|---|---|
| POST | `/api/listing-images` | giriş | 201 `ImageUpload` |
| POST | `/api/listing-images/{id}/complete` | sahibi | 200 `UploadedImage` (tekrar çağrılabilir, aynı cevabı döner) |
| DELETE | `/api/listing-images/{id}` | sahibi | 204. Tekrar çağrılabilir, ama dosya birkaç saniye içinde silinince görsel tamamen kalkar ve tekrar çağrı `404` döner: `404`'ü de "silindi" sayın. |

```ts
interface ImageUploadRequest {
  contentType: 'image/jpeg' | 'image/png' | 'image/webp'; // yüklenecek dosyanın türü (Blob.type)
  sizeBytes: number;                                     // yüklenecek dosyanın tam boyutu (Blob.size), en fazla 10 MB
}

interface ImageUpload {
  id: number;
  uploadUrl: string;                        // depo adresi; API'nin değil (dev: http://localhost:8333/...)
  uploadHeaders: Record<string, string>;    // PUT isteğine AYNEN eklenmeli (şu an content-type ve cache-control)
  expiresAt: string;                        // ISO-8601; URL 10 dakika geçerli
}

interface UploadedImage {
  id: number;
  url: string;                              // herkese açık görsel adresi; önizlemede kullanılabilir
}
```

**PUT isteği için kurallar (önemli):**

- `uploadUrl`'e `PUT` atılır; gövde dosyanın kendisidir (`Blob`/`File`), **`FormData` değil**.
- `uploadHeaders` içindeki header'lar **değiştirilmeden** eklenir. Başka header ekleme: özellikle **`Authorization` gönderilmez**. API'nin axios örneği ve interceptor'ları bu istek için kullanılmamalı; düz `fetch` (ya da interceptor'sız ayrı bir istemci) kullanın, `credentials` göndermeyin.
- İmza tür ve **boyutu** kapsar: `sizeBytes` olarak bildirilen sayı gönderilen dosyanın boyutuyla birebir aynı olmalı. Dosyayı küçültecekseniz **önce küçültün**, sonra küçültülmüş dosyanın `size` ve `type` değeriyle 1. adımı çağırın.
- Boyut, tür ya da header farklıysa depo `403` döner (gövdesi XML'dir, API hata formatında değildir). URL süresi dolduysa da `403` döner: 1. adımdan yeniden başlayın.
- Geliştirmede depo farklı bir origin'dedir (`localhost:8333`); CORS izinlidir. Prod'da bucket CORS'u frontend domain'ine göre ayarlanacak.

**Hata durumları:**

| Durum | Uç | Ne zaman | Önerilen davranış |
|---|---|---|---|
| 400 | POST | Desteklenmeyen tür (sadece JPEG, PNG, WEBP) ya da 10 MB üstü; alan eksikse `errors` dolu | `message`'ı göster |
| 422 + `EMAIL_NOT_VERIFIED` / `PHONE_REQUIRED` | POST | İlan veremeyen kullanıcı (ilan oluşturmayla aynı kural) | İlan formundaki davranışın aynısı |
| 422 + `IMAGE_LIMIT_REACHED` | POST | Kullanıcının henüz bir ilana eklenmemiş 40 görseli var | `message`'ı göster; kullanılmayan görselleri `DELETE` ile silmeyi öner |
| 422 + `IMAGE_NOT_UPLOADED` | complete | Dosya depoda yok (PUT yapılmamış ya da başarısız) | PUT'u tekrar deneyin (URL hâlâ geçerliyse), sonra complete'i tekrar çağırın |
| 422 + `IMAGE_INVALID` | complete | Dosyanın içeriği bildirilen türde değil ya da boyutu farklı. Dosya silinir, bu id artık kullanılamaz | "Dosya geçerli bir görsel değil" göster; kullanıcı başka dosya seçsin |
| 404 | complete / DELETE | Görsel yok, silinmiş ya da başkasının | Görseli formdan çıkar (DELETE'te başarı say) |
| 429 | POST | 10 dakikada 60'tan fazla yükleme URL'si istendi | Genel 429 davranışı; `Retry-After` kadar bekleyip sıradaki görsellere devam |
| 409 | DELETE | Görsel bir ilana eklenmiş | İlanı PUT ile güncelleyerek listeden çıkarın |
| 503 | herhangi | Depoya ulaşılamıyor | "Tekrar deneyin" göster |

**Davranış notları:**

- Kullanıcı formdan bir görseli kaldırırsa (ilan henüz kaydedilmediyse) `DELETE /api/listing-images/{id}` çağırın. Çağrılmasa da sorun olmaz: 24 saat içinde bir ilana eklenmeyen görseller saatlik temizlikte otomatik silinir.
- Silinen görselin (DELETE, ilandan çıkarma, ilan silme) dosyası birkaç saniye içinde depodan da silinir; eski URL'si `404` döner. Önbellekteki eski URL'leri göstermeye çalışmayın.
- Bu yüzden form uzun süre açık kalıp ertesi gün kaydedilirse `IMAGE_NOT_AVAILABLE` gelebilir; kullanıcıdan görselleri yeniden yüklemesini isteyin.
- **Küçültme ve gizlilik (frontend'in sorumluluğu):** telefon fotoğrafları büyüktür ve içlerinde EXIF bilgisi (çoğu zaman **GPS konumu**) bulunur. Yüklemeden önce görseli tarayıcıda canvas ile uzun kenarı en fazla 2048 px olacak şekilde küçültüp `image/webp` (desteklenmiyorsa `image/jpeg`, kalite ~0.85) olarak yeniden kodlayın. Yeniden kodlanan dosyada EXIF kalmaz. Sunucu görseli işlemez.

## İlanlar

### Uçlar

| Metot | URL | Kimlik | Başarılı cevap |
|---|---|---|---|
| GET | `/api/listings` | herkes | `PageResponse<ListingSummary>` — sadece ACTIVE ilanlar |
| GET | `/api/listings/{id}` | herkes (token varsa gönderin) | `ListingDetail` |
| POST | `/api/listings` | giriş | 201 `ListingDetail` |
| PUT | `/api/listings/{id}` | sahibi | 200 `ListingDetail` |
| PATCH | `/api/listings/{id}/status` | sahibi | 200 `ListingDetail` |
| POST | `/api/listings/{id}/sale` | sahibi | 200 `ListingDetail` — satıldı olarak işaretler (aşağıda "Satış") |
| DELETE | `/api/listings/{id}/sale` | sahibi | 200 `ListingDetail` — satışı geri alır, ilan tekrar yayına girer |
| DELETE | `/api/listings/{id}` | sahibi | 204 |
| GET | `/api/users/me/listings` | giriş | `PageResponse<ListingSummary>` — her durumdaki kendi ilanları |

### Arama parametreleri (`GET /api/listings`)

Hepsi opsiyonel query parametresidir; verilmeyen filtre uygulanmaz.

| Parametre | Tip | Not |
|---|---|---|
| `q` | string | Anahtar kelime, en fazla 100 karakter (fazlası `400`, `errors.q`). Ayrıntılar aşağıda |
| `brandId`, `modelId`, `seriesId` | number | Katalogdan gelen id'ler |
| `cityId`, `districtId` | number | |
| `minPrice`, `maxPrice` | number | Ham tutar karşılaştırır; anlamlı olması için `currency` ile birlikte gönderin |
| `currency` | `TRY \| USD \| EUR` | |
| `minYear`, `maxYear` | number | 1950 ile gelecek yıl arası |
| `maxKm` | number | |
| `fuelType`, `gearType`, `bodyType`, `vehicleCondition`, `fromWho` | enum | Tek değer |
| `hasDamageRecord` | boolean | |
| `sort` | `ListingSort` | Varsayılan `NEWEST` |
| `page`, `size` | number | Yukarıdaki sayfa kuralları |

```ts
type ListingSort =
  | 'NEWEST' | 'OLDEST'
  | 'PRICE_ASC' | 'PRICE_DESC'
  | 'YEAR_DESC' | 'YEAR_ASC'
  | 'KM_ASC' | 'KM_DESC';
```

**Anahtar kelime (`q`) nasıl eşleşir:**

- Marka, model, seri adı, başlık ve açıklamada aranır.
- Büyük/küçük harf ve Türkçe karakter farkı yoktur: `ışıklı`, `IŞIKLI`, `isikli` aynıdır.
- Kelime başı eşleşir: `volks` → "Volkswagen", `cor` → "Corolla". Kelimenin ortası eşleşmez.
- Birden fazla kelime verilirse **hepsi** geçmelidir (`golf dizel`). İlk 5 farklı kelime dikkate alınır.
- Harf ve rakam dışındaki karakterler ayraç sayılır (`1.6` → `1` ve `6`); özel karakter hata üretmez. Aranabilir bir şey kalmazsa (`q=!!!`) filtre uygulanmaz.
- Diğer filtreler ve `sort` ile birlikte kullanılır. Sonuçlar alaka düzeyine göre değil, `sort`'a göre sıralanır.

Frontend'deki `?kasa=sedan` gibi Türkçe URL slug'ları backend'e enum değeri olarak çevrilip gönderilmeli (`bodyType=SEDAN`); `src/shared/config/bodyTypes.ts` bu eşlemeyi zaten içeriyor.

### Cevap tipleri

```ts
interface ListingSummary {
  id: number;
  title: string;
  price: number;
  currency: CurrencyType;
  status: ListingStatus;
  createdAt: string;
  cityName: string;
  districtName: string;
  brandName: string;
  modelName: string;
  seriesName: string;
  modelYear: number;
  kilometer: number;
  fuelType: FuelType;
  gearType: GearType;
  coverImageUrl: string | null;
}

interface ListingDetail {
  id: number;
  version: number;          // güncellemede geri gönderilir
  title: string;
  description: string | null;
  price: number;
  currency: CurrencyType;
  status: ListingStatus;
  rejectionReason: string | null; // sadece REJECTED iken dolu; reddedilen ilanı sadece sahibi ve admin görür
  sale: ListingSale | null;  // sadece SOLD iken dolu; satılmış ilanı sadece sahibi ve admin görür
  viewCount: number;        // bu görüntülemeden önceki sayı; sahibin bakışları sayılmaz
  favoriteCount: number;
  favoritedByMe: boolean;   // token yoksa her zaman false
  createdAt: string;
  updatedAt: string;
  seller: { id: number; firstName: string; lastName: string; phone: string | null }; // phone: anonimde null
  location: { cityId: number; cityName: string; districtId: number; districtName: string };
  vehicle: {
    brandId: number; brandName: string;
    modelId: number; modelName: string;
    seriesId: number; seriesName: string;
    modelYear: number;
    kilometer: number;
    fuelType: FuelType;
    gearType: GearType;
    bodyType: BodyType | null;
    color: string | null;
    enginePower: number | null;     // hp
    engineCapacity: number | null;  // cc
    traction: Traction | null;
    vehicleCondition: VehicleCondition;
    fromWho: FromWho;
    hasDamageRecord: boolean;
    exchangeable: boolean;
  };
  images: { id: number; url: string; displayOrder: number; cover: boolean }[]; // displayOrder'a göre sıralı
}
```

```ts
interface ListingSale {
  salePrice: number;      // ilanın para biriminde
  note: string | null;    // satış açıklaması
  soldAt: string;         // satıldı olarak işaretlendiği an (sunucu saati)
  hidden: boolean;        // true: admin bu satışı satılmış araçlar sayfasından gizledi
}
```

Satıcının e-postası bilinçli olarak dönmüyor; telefon sadece giriş yapmış kullanıcılara döner (anonimde `null` → "Numarayı görmek için giriş yapın" gösterilebilir). "Bu ilan benim mi?" kontrolü için `seller.id === currentUser.id` kullanın.

### Oluşturma ve güncelleme gövdesi

POST ve PUT aynı gövdeyi kullanır; PUT'ta ayrıca `version` zorunludur.

```ts
interface ListingRequest {
  version?: number;               // sadece PUT, zorunlu
  title: string;                  // 5–150 karakter
  description?: string;           // en fazla 5000 karakter
  price: number | string;         // > 0, en fazla 13 tam + 2 ondalık hane
  currency: CurrencyType;
  districtId: number;
  vehicle: {
    seriesId: number;
    modelYear: number;            // 1950 – (bu yıl + 1)
    kilometer: number;            // 0 – 2.000.000
    fuelType: FuelType;
    gearType: GearType;
    bodyType?: BodyType;
    color?: string;               // en fazla 30 karakter
    enginePower?: number;         // 1 – 2000
    engineCapacity?: number;      // 1 – 10000
    traction?: Traction;
    vehicleCondition: VehicleCondition;
    fromWho: FromWho;
    hasDamageRecord: boolean;
    exchangeable: boolean;
  };
  imageIds: number[];             // 1–20 adet, tekrarsız; "İlan görselleri" akışından gelen id'ler
}
```

- **Görseller:** Önce her dosya yüklenir (bkz. "İlan görselleri (dosya yükleme)"), sonra id'leri `imageIds` ile gönderilir. Listedeki sıra gösterim sırasıdır, **ilk görsel kapak** olur. Her id ya kullanıcının yüklemesi tamamlanmış ve henüz ilana eklenmemiş bir görseli ya da (PUT'ta) **bu ilanın** mevcut bir görseli olmalı.
- **PUT görsel listesini tamamen değiştirir:** mevcut görselleri korumak için detaydaki `images[].id` değerlerini de listeye koyun. Listede olmayan mevcut görseller ilandan **kalıcı olarak** çıkar ve bir daha kullanılamaz. Sıralamayı ve kapağı değiştirmek için sadece listedeki sırayı değiştirin.
- Eski `imageUrls` alanı **kaldırıldı**: gönderilirse yok sayılır ve `imageIds` eksik olduğu için `400` (`errors.imageIds`) döner.
- **Marka/model** gönderilmez; sadece `seriesId` gönderilir, marka ve model ondan türetilir. Formda marka → model → seri seçimi kademeli dropdown olarak kurulabilir.
- PUT tam güncellemedir (PATCH değil): formdaki tüm alanlar gönderilmeli.

### İlan hata durumları

| Durum | Ne zaman | Önerilen davranış |
|---|---|---|
| 400 | Doğrulama hatası (`errors` dolu), PUT'ta `version` eksik (`errors.version`) | Alan hatalarını forma yaz |
| 401 | Token yok/geçersiz (yazma uçları) | Mevcut refresh akışı |
| 403 | Başkasının ilanını güncelleme/silme/durum değiştirme | "Yetkiniz yok" göster |
| 404 | İlan yok **veya** ACTIVE değil ve bakan kişi sahibi değil | "İlan bulunamadı" sayfası |
| 409 | `version` eski: ilan arada başka yerden güncellenmiş | Uyarı göster, detayı yeniden çek, kullanıcı tekrar denesin |
| 422 | Seri veya ilçe bulunamadı; SOLD ilanı güncelleme veya silme; izin verilmeyen durum geçişi (onay bekleyen/reddedilen ilan dahil); satılamaz durumdaki ilanı satma; satılmamış ilanın satışını geri alma | `message`'ı göster |
| 422 + `code: "SALE_PRICE_OUT_OF_RANGE"` | Satış fiyatı ilan fiyatının %80–%110'u dışında | `message`'ı satış fiyatı alanının altında göster (izin verilen aralığı içerir) |
| 422 + `code: "PHONE_REQUIRED"` | Profilde telefon yokken ilan verme | Profil sayfasına yönlendir |
| 422 + `code: "EMAIL_NOT_VERIFIED"` | E-posta doğrulanmamışken ilan verme | "E-postanı doğrula" uyarısı + tekrar gönder butonu |
| 422 + `code: "IMAGE_NOT_AVAILABLE"` | `imageIds` içinde başkasının, başka bir ilanın, yüklemesi tamamlanmamış ya da silinmiş bir görsel var | `message`'ı göster; görselleri yeniden yükletin (form durumu bayatlamış olabilir) |

### Durum geçişleri (`PATCH /api/listings/{id}/status`, gövde `{ "status": "PASSIVE" }`)

| Mevcut | İzin verilen hedef |
|---|---|
| ACTIVE | PASSIVE |
| PASSIVE | ACTIVE |

Diğer her geçiş 422 döner. **`SOLD` bu uçla verilemez** (`PATCH` ile `{ "status": "SOLD" }` artık 422): satış bilgisi taşıdığı için sadece `POST /api/listings/{id}/sale` ile girilir ve `DELETE /api/listings/{id}/sale` ile çıkılır. `PENDING_APPROVAL` ve `REJECTED` durumlarından sahibi bu uçla çıkamaz (aşağıya bakın).

### Satış

**Satıldı olarak işaretleme:** `POST /api/listings/{id}/sale`

```ts
interface MarkSoldRequest {
  salePrice: number;      // zorunlu, > 0, en fazla 13 tam + 2 ondalık; ilanın para biriminde
  note?: string | null;   // en fazla 1000 karakter; boş/sadece boşluk null sayılır, baş/son boşluk kırpılır
  version: number;        // detaydaki version (eskiyse 409)
}
```

- Sadece `ACTIVE` veya `PASSIVE` ilan satılabilir; diğerleri 422.
- Satış fiyatı ilan fiyatının **%80'i ile %110'u arasında** olmalı (sınırlar dahil; ör. 750.000 TL'lik ilan için 600.000–825.000). Dışındaysa `422` + `code: "SALE_PRICE_OUT_OF_RANGE"`; `message` aralığı söyler. Formda bu aralığı önceden göstermek iyi olur (hesap: `ceil(fiyat × 0.80)`, `floor(fiyat × 1.10)`, kuruş hassasiyetinde).
- Satış zamanı sunucunun kaydettiği andır; istemci tarih göndermez.
- Başarılı cevap `ListingDetail`: `status: "SOLD"`, `sale` dolu. İlan aramadan ve herkese açık detaydan çıkar, satılmış araçlar sayfasına girer.
- Satılmış ilan **düzenlenemez ve silinemez** (422). Silmek isteyen önce satışı geri almalıdır.

**Tekrar yayına alma (satışı geri alma):** `DELETE /api/listings/{id}/sale`, gövde yok.

- Sadece `SOLD` ilanda; diğerlerinde 422.
- Satış kaydı (fiyat, açıklama, tarih) silinir, ilan satılmış araçlar sayfasından çıkar ve **doğrudan `ACTIVE`** olur (daha önce onaylandığı için tekrar onaya gitmez).
- Admin'in gizlediği satışta da çalışır.

### Admin onayı

- **Yeni ilan `PENDING_APPROVAL` olarak açılır** (`POST` cevabında `status: "PENDING_APPROVAL"`). Admin onaylayana kadar aramada görünmez; detayını sadece sahibi görür.
- Admin onaylarsa `ACTIVE` olur. Reddederse `REJECTED` olur ve `rejectionReason` dolar; sahibi nedeni detayda görür.
- **Yayındaki ilanın düzenlenmesi:** `ACTIVE` veya `PASSIVE` bir ilanın **başlığı, açıklaması, fiyatı/para birimi veya görsel seti** değişirse ilan tekrar `PENDING_APPROVAL` olur ve onaylanana kadar yayından kalkar. Diğer alanlar (km, renk, yıl, ilçe …) ve sadece görsel **sırası** değişirse durum değişmez.
  - `PUT` cevabındaki `status` güncel durumu söyler. Frontend, düzenleme sonrası `PENDING_APPROVAL` dönerse kullanıcıya "İlanınız tekrar onaya gönderildi" demeli. Kaydetmeden önce de uyarmak iyi olur: "Başlık, açıklama, fiyat veya fotoğraf değişikliği ilanı onay sürecine alır."
  - Onaylanan `PASSIVE` ilan `ACTIVE` olarak yayına girer; sahibi isterse tekrar pasife alır.
- **Reddedilen ilan:** sahibi düzenleyip kaydedince (hangi alan olursa olsun) tekrar `PENDING_APPROVAL` olur ve `rejectionReason` silinir. Silmek de mümkündür.
- Admin yayındaki bir ilanı da reddedebilir (yayından kaldırma). Bu durumda ilan `REJECTED` olur.
- Onay bekleyen ilan da düzenlenebilir, sırada kalır.

### Görünürlük

- Arama (`GET /api/listings`) sadece ACTIVE ilanları döner.
- Sahibi admin tarafından engellenmiş ilanlar her yerde (arama, detay, favoriler) ACTIVE olmayan ilan gibi gizlenir. Engel kalkınca ilanlar olduğu gibi geri gelir.
- Satılmış ilanlar herkese açık olarak sadece satılmış araçlar sayfasında (`/api/sold-vehicles`) görünür.
- Detay: ACTIVE olmayan ilanı sadece sahibi görür; diğerlerine 404 (403 değil, ilanın varlığı sızmasın diye). Sahibinin kendi PASSIVE/SOLD ilanını açabilmesi için detay isteğinde token gönderilmeli; mevcut `http` istemcisi bunu yapıyor.
- "İlanlarım" (`/api/users/me/listings`) her durumu döner; `status` alanına göre rozet gösterilebilir.

## Satılmış araçlar (herkese açık, token gerekmez)

Satılmış ilanların gerçek satış fiyatlarıyla listelendiği ayrı sayfa için. Satıcı bilgisi (ad, telefon) **hiçbir uçta dönmez**.

Bir ilan bu sayfada görünür: `SOLD` durumunda **ve** admin satışı gizlememiş **ve** sahibi engelli değil. Satış geri alınınca (tekrar yayına alma) ya da admin gizleyince sayfadan çıkar.

| Metot | URL | Cevap |
|---|---|---|
| GET | `/api/sold-vehicles` | `PageResponse<SoldVehicleSummary>` |
| GET | `/api/sold-vehicles/summary` | `SalePriceSummary` |
| GET | `/api/sold-vehicles/{id}` | `SoldVehicleDetail`; görünmeyen/olmayan kayıtta 404. `id` ilan id'sidir |

**Filtreler:** liste ve özet, `GET /api/listings` ile **aynı** filtre parametrelerini alır (`q`, `brandId`, `modelId`, `seriesId`, `cityId`, `districtId`, `minPrice`, `maxPrice`, `currency`, `minYear`, `maxYear`, `maxKm`, `fuelType`, `gearType`, `bodyType`, `vehicleCondition`, `fromWho`, `hasDamageRecord`) ve aynı doğrulama kurallarına uyar. Fark: `minPrice`/`maxPrice` **satış fiyatına** uygulanır. Arama formu bileşenleri yeniden kullanılabilir.

**Sıralama ve sayfa (sadece liste):** `sort` (`SoldVehicleSort`, varsayılan `NEWEST` = en yeni satış), `page`, `size` (en fazla 50). Bilinmeyen `sort` → 400.

```ts
type SoldVehicleSort =
  | 'NEWEST' | 'OLDEST'          // satış tarihine göre
  | 'PRICE_ASC' | 'PRICE_DESC'   // satış fiyatına göre
  | 'YEAR_DESC' | 'YEAR_ASC'
  | 'KM_ASC' | 'KM_DESC';

interface SoldVehicleSummary {
  id: number;
  title: string;
  listingPrice: number;   // ilandaki istenen fiyat
  salePrice: number;      // gerçekleşen satış fiyatı
  currency: CurrencyType;
  soldAt: string;
  cityName: string;
  districtName: string;
  brandName: string;
  modelName: string;
  seriesName: string;
  modelYear: number;
  kilometer: number;
  fuelType: FuelType;
  gearType: GearType;
  coverImageUrl: string | null;
}

interface SoldVehicleDetail {
  id: number;
  title: string;
  description: string | null;
  listingPrice: number;
  salePrice: number;
  currency: CurrencyType;
  saleNote: string | null;
  soldAt: string;
  createdAt: string;       // ilanın açıldığı tarih
  location: ListingDetail['location'];
  vehicle: ListingDetail['vehicle'];
  images: ListingDetail['images'];
}

// Para birimi başına ayrı; en çok satışı olan önce. Eşleşme yoksa currencies boş dizi.
interface SalePriceSummary {
  currencies: {
    currency: CurrencyType;
    count: number;
    average: number;   // 2 ondalığa yuvarlanmış
    min: number;
    max: number;
  }[];
}
```

Özet, listeyle aynı filtrelerle ayrı bir istektir (sayfalama/sıralama almaz). Örnek gösterim: "Bu aramada 23 araç satıldı · ortalama 845.000 ₺ (en düşük 790.000 ₺, en yüksek 910.000 ₺)". Birden fazla para birimi varsa her biri ayrı satır.

## Favoriler

| Metot | URL | Kimlik | Cevap |
|---|---|---|---|
| PUT | `/api/listings/{id}/favorite` | giriş | 204 — tekrar çağrılabilir; ilan yoksa, ACTIVE değilse veya sahibi engelliyse 404 |
| DELETE | `/api/listings/{id}/favorite` | giriş | 204 — tekrar çağrılabilir; favoride değilse de 204 |
| GET | `/api/users/me/favorites` | giriş | `PageResponse<ListingSummary>` — son eklenen başta; sadece `page`/`size` (sıralama parametresi yok) |

- İki uç da idempotent olduğu için favori butonunda optimistic update güvenle yapılabilir; hata olursa geri alınır.
- Favorilerim listesi sadece şu an görünür ilanları gösterir (ACTIVE ve sahibi engelli değil). Pasife alınan ya da sahibi engellenen ilanın favorisi silinmez; ilan tekrar görünür olunca listede geri gelir.
- İlan silinirse favorisi de silinir.
- Favori değişince ilgili detayın (`favoriteCount`, `favoritedByMe`) ve favoriler listesinin cache'i geçersiz kılınmalı.

## İlan şikayeti

| Metot | URL | Kimlik | Gövde | Cevap |
|---|---|---|---|---|
| POST | `/api/listings/{id}/reports` | giriş | `{ "reason": ReportReason, "description"?: string }` | 201 `ListingReport` |

- Sadece giriş yapmış kullanıcı şikayet edebilir (token yoksa `401`). E-posta doğrulaması gerekmez.
- `reason` zorunlu (`400`, `errors.reason`; bilinmeyen değer de `400`). `description` en fazla 1000 karakter, kırpılarak kaydedilir; `reason: "OTHER"` ise zorunlu (boşsa `400`, mesaj "\"Diğer\" seçildiğinde açıklama zorunludur").
- Sadece herkese görünen ilan şikayet edilebilir; ilan yoksa ya da görünür değilse `404`.
- Kendi ilanını şikayet etmek `422` "Kendi ilanınızı şikayet edemezsiniz". Butonu sahibine hiç göstermeyin (`seller.id` = giriş yapan kullanıcı).
- Aynı ilanı daha önce şikayet ettiyseniz ve şikayet hâlâ inceleniyorsa `409` "Bu ilanı zaten şikayet ettiniz, inceleniyor". Bu bir hata değil, bilgilendirme olarak gösterilebilir. Şikayet sonuçlandıktan sonra tekrar şikayet edilebilir.
- Kullanıcı kendi şikayetlerini listeleyemez; şikayet sonucu kullanıcıya bildirilmez.
- Sınır: IP başına saatte 10 şikayet (`429`, bkz. "İstek sınırı").

```ts
interface ListingReport {
  id: number;
  listingId: number;
  reason: ReportReason;
  description: string | null;
  status: ReportStatus;   // yeni şikayette her zaman "OPEN"
  createdAt: string;      // ISO-8601 UTC
}
```

## Yönetim (admin)

Bu uçlar sadece `role: "ADMIN"` olan kullanıcılara açıktır: token yoksa `401`, kullanıcı admin değilse `403`. Frontend admin menüsünü `GET /api/users/me` cevabındaki `role` alanına göre gösterir (bu sadece görünüm kararıdır, yetkiyi backend kontrol eder). Rol istemciden hiçbir zaman alınmaz; ilk admin sunucu yapılandırmasıyla (`APP_ADMIN_EMAILS`) atanır.

### Kullanıcılar

| Metot | URL | Cevap |
|---|---|---|
| GET | `/api/admin/users?q=&page=&size=` | `PageResponse<AdminUser>`, en yeni kayıt başta |
| POST | `/api/admin/users/{id}/block` | 204. Tekrar çağrılabilir |
| POST | `/api/admin/users/{id}/unblock` | 204. Tekrar çağrılabilir |

- `q`: e-posta adresinin herhangi bir parçası, büyük/küçük harf duyarsız. En fazla 100 karakter (fazlası `400`, `errors.q`). Boşsa tüm kullanıcılar listelenir.
- Engelleme hataları: kendini ya da başka bir admini engellemeye çalışınca `422` "Yönetici hesapları engellenemez". Kullanıcı yoksa `404`.
- Engellenen kullanıcının oturumları hemen kapanır: access token'ı bir sonraki istekte `401` alır, refresh `401` döner, girişte `403` "Hesap devre dışı" görür. İlanları gizlenir ama silinmez (bkz. "Görünürlük").

```ts
interface AdminUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: 'USER' | 'ADMIN';
  enabled: boolean;        // false = engelli
  emailVerified: boolean;
  createdAt: string;       // ISO-8601 UTC
  listingCount: number;    // her durumdaki ilanları
}
```

### İlan onayı

| Metot | URL | Gövde | Cevap |
|---|---|---|---|
| GET | `/api/admin/listings?status=&page=&size=` | | `PageResponse<ListingSummary>`, en eski başta. `status` verilmezse `PENDING_APPROVAL` (her `ListingStatus` değeri geçerli; bilinmeyen değer `400`, `errors.status`) |
| GET | `/api/admin/listings/{id}` | | `ListingDetail`, her durumda; satıcı telefonu dahil; görüntülenme sayılmaz |
| POST | `/api/admin/listings/{id}/approve` | `{ "version": number }` | `ListingDetail` (`ACTIVE`) |
| POST | `/api/admin/listings/{id}/reject` | `{ "version": number, "reason": string }` | `ListingDetail` (`REJECTED`) |

- `version`: admin'in incelediği detaydaki `version`. Sahibi bu arada ilanı değiştirdiyse `409` döner ("İlan siz incelerken değişti…"): detayı yeniden çekip tekrar inceleyin. Böylece görülmemiş içerik onaylanmaz.
- `reason`: zorunlu, en fazla 500 karakter, sahibine aynen gösterilir. Eksikse `400` (`errors.reason`).
- Onay sadece `PENDING_APPROVAL` ilanda, red `PENDING_APPROVAL` veya `ACTIVE` ilanda yapılabilir; aksi `422`. İlan yoksa `404`.

### Katalog ekleme

| Metot | URL | Gövde | Cevap |
|---|---|---|---|
| POST | `/api/admin/catalog/brands` | `{ "name": string }` | 201 `{ id, name }` |
| POST | `/api/admin/catalog/brands/{brandId}/models` | `{ "name": string }` | 201 `{ id, name }` |
| POST | `/api/admin/catalog/models/{modelId}/series` | `{ "name": string }` | 201 `{ id, name }` |

- Sadece **ekleme** vardır; yeniden adlandırma ve silme yoktur.
- Bu uçlar `Location` header'ı döndürmez (katalogda tek kayıt ucu yok). Ekledikten sonra ilgili listeyi (`GET /api/catalog/...`) yeniden çekin.
- `name`: zorunlu, en fazla 50 karakter (`400`, `errors.name`). Baştaki/sondaki boşluk kırpılır, içteki çoklu boşluk teke iner; cevaptaki `name` kaydedilen haldir.
- Aynı ebeveyn altında aynı isim (büyük/küçük harf farkı gözetilmeden, Türkçe kurallarla: `bmw` = `BMW`, `şahin` = `ŞAHİN`) `409` döner. Aynı seri adı farklı modellerde olabilir.
- Ebeveyn marka/model yoksa `404`.

### Şikayetler

Şikayetler ilan bazında ele alınır: admin bir ilan için karar verir ve o ilanın tüm açık şikayetleri birlikte kapanır.

| Metot | URL | Gövde | Cevap |
|---|---|---|---|
| GET | `/api/admin/reports?page=&size=` | | `PageResponse<ReportedListing>`. Sadece açık şikayeti olan ilanlar; en çok şikayet alan başta, eşitse son şikayeti en yeni olan |
| GET | `/api/admin/reports/listings/{listingId}?page=&size=` | | `PageResponse<AdminListingReport>`. İlanın tüm şikayetleri (sonuçlananlar dahil), en yeni başta. İlan yoksa boş sayfa |
| POST | `/api/admin/reports/listings/{listingId}/dismiss` | | 204. Açık şikayetleri `DISMISSED` yapar; ilan değişmez |
| POST | `/api/admin/reports/listings/{listingId}/take-down` | `{ "version": number, "reason": string }` | `ListingDetail` (`REJECTED`). İlanı reddeder ve açık şikayetleri `ACTION_TAKEN` yapar |

- İlanın detayı için mevcut `GET /api/admin/listings/{id}` kullanılır (her durumda açılır, `version` oradan alınır).
- `take-down` "İlan onayı"ndaki red ile aynıdır: sahibi `rejectionReason`'ı görür, ilanı düzenlerse tekrar onaya düşer. Gövde kuralları da aynı (`reason` zorunlu, en fazla 500; eski `version` → `409`). Sadece `PENDING_APPROVAL` veya `ACTIVE` ilan kaldırılabilir; sahibi ilanı pasife aldıysa ya da satıldı yaptıysa `422` döner, bu durumda şikayetler `dismiss` ile kapatılabilir.
- Açık şikayeti olmayan ilanda `dismiss` ve `take-down` `422` "Bu ilan için açık şikayet yok" döner.
- `409` gelirse şikayetler açık kalır; detayı yeniden çekip tekrar karar verin.
- Kuyruktaki `listing.status` ilanın şu anki durumunu gösterir (ör. sahibi engelliyse ya da ilan pasifse yine listelenir).

```ts
interface ReportedListing {
  listing: ListingSummary;
  openReportCount: number;
  reasons: Partial<Record<ReportReason, number>>;  // açık şikayetlerin nedenlere göre dağılımı
  firstReportedAt: string;
  lastReportedAt: string;
}

interface AdminListingReport {
  id: number;
  reason: ReportReason;
  description: string | null;
  status: ReportStatus;
  reporter: { id: number; firstName: string; lastName: string; email: string };
  createdAt: string;
  resolvedAt: string | null;
}
```

### Satış kaydını gizleme

Uydurma satış fiyatına karşı: admin satışı satılmış araçlar sayfasından (liste, özet, detay) kaldırır. İlan `SOLD` kalır; sahibi kendi detayında `sale.hidden: true` görür ve isterse satışı geri alabilir.

| Metot | URL | Cevap |
|---|---|---|
| POST | `/api/admin/sold-vehicles/{listingId}/hide` | 200 `ListingDetail` (`sale.hidden: true`). Tekrar çağrılırsa yine 200. İlan `SOLD` değilse 422, yoksa 404 |

Gizlemeyi geri alan uç yoktur. Admin bu işlemi satılmış araç detayından (`/api/sold-vehicles/{id}`) ya da `GET /api/admin/listings/{id}` ile açtığı ilandan başlatabilir; `GET /api/admin/listings?status=SOLD` satılmış ilanları (gizlenenler dahil) listeler.

## Enum değerleri ve önerilen Türkçe etiketler

| Enum | Değer → etiket |
|---|---|
| `ListingStatus` | `PENDING_APPROVAL` Onay bekliyor · `ACTIVE` Yayında · `PASSIVE` Yayında değil · `SOLD` Satıldı · `REJECTED` Reddedildi |
| `CurrencyType` | `TRY` ₺ · `USD` $ · `EUR` € |
| `FuelType` | `GASOLINE` Benzin · `DIESEL` Dizel · `LPG` LPG · `HYBRID` Hibrit · `ELECTRIC` Elektrik |
| `GearType` | `MANUAL` Manuel · `AUTOMATIC` Otomatik · `SEMI_AUTOMATIC` Yarı otomatik |
| `BodyType` | Frontend'de `src/shared/config/bodyTypes.ts` zaten var |
| `Traction` | `FWD` Önden çekiş · `RWD` Arkadan itiş · `AWD` 4x4 |
| `VehicleCondition` | `NEW` Sıfır · `USED` İkinci el |
| `FromWho` | `OWNER` Sahibinden · `GALLERY` Galeriden · `AUTHORIZED_DEALER` Yetkili bayiden |
| `ReportReason` | `FAKE_OR_MISLEADING` Sahte veya yanıltıcı ilan · `FRAUD_SUSPECTED` Dolandırıcılık şüphesi · `WRONG_INFO` Yanlış bilgi (fiyat, km vb.) · `INAPPROPRIATE_CONTENT` Uygunsuz içerik · `ALREADY_SOLD` Araç satılmış · `OTHER` Diğer |
| `ReportStatus` | `OPEN` İnceleniyor · `DISMISSED` Reddedildi (işlem yapılmadı) · `ACTION_TAKEN` İlan kaldırıldı |

## Bilinen kısıtlar

- Görseller sunucuda yeniden boyutlandırılmaz ve küçük önizleme (thumbnail) üretilmez; küçültme ve EXIF temizliği frontend'de yapılır.
- Fiyat filtresi kur çevirmez.
- Satıcıyla iletişim sadece telefonla (mesajlaşma yok); e-posta hiç paylaşılmaz.
- Katalogda adlandırma ve silme yok (sadece ekleme).

Backend tarafında uyumsuzluk veya eksik bulunursa backend reposunda düzeltilir; frontend sözleşmeyi kendi tarafında değiştirmemeli.
