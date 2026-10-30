# satmis.com — Frontend API devir notu

Bu not, frontend'in henüz kullanmadığı backend uçlarını (katalog, ilan, favori) anlatır. Auth ve profil uçlarındaki değişiklikler: telefon numarası (bkz. "Telefon numarası ve satıcı iletişimi"), e-posta doğrulama, şifre sıfırlama ve istek sınırı (bkz. "E-posta doğrulama", "Şifre sıfırlama", "İstek sınırı (429)").

- **Tek doğruluk kaynağı OpenAPI'dir:** backend çalışırken `http://localhost:8080/v3/api-docs` (Swagger UI: `/swagger-ui.html`). TypeScript tiplerini oradan üretmek önerilir (openapi-typescript / orval). Bu nottaki tipler elle yazılmıştır ve kuralları açıklamak içindir.
- Geliştirmede istekler Vite proxy üzerinden gider (`/api` → `http://localhost:8080`), CORS gerekmez.
- Backend'i başlatmak için proje kökünde `.env` olmalı (`.env.example`'dan kopyala: `SPRING_PROFILES_ACTIVE=dev`, `JWT_SECRET`, `DB_PASSWORD`): `.\mvnw.cmd spring-boot:run`.
- E-postalar geliştirmede Mailpit'e gider: `docker compose up -d mailpit`, gelen kutusu `http://localhost:8025`.

## Genel kurallar

| Konu | Kural |
|---|---|
| Hata gövdesi | Her hatada `{ timestamp, status, code?, message, path, errors? }`. `message` Türkçe ve kullanıcıya gösterilebilir. `code` sadece frontend'in özel davranması gereken hatalarda gelir (şu an: `PHONE_REQUIRED`, `EMAIL_NOT_VERIFIED`); diğer hatalarda alan hiç yoktur. |
| Alan hataları | `errors` alan → mesaj haritası. İç içe alanlar noktalı (`vehicle.modelYear`). Görsel adresi hataları tek anahtar altında döner (`imageUrls`). Bilinmeyen enum / sayı olmayan değer: `"Geçersiz değer"`. |
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

## İlanlar

### Uçlar

| Metot | URL | Kimlik | Başarılı cevap |
|---|---|---|---|
| GET | `/api/listings` | herkes | `PageResponse<ListingSummary>` — sadece ACTIVE ilanlar |
| GET | `/api/listings/{id}` | herkes (token varsa gönderin) | `ListingDetail` |
| POST | `/api/listings` | giriş | 201 `ListingDetail` |
| PUT | `/api/listings/{id}` | sahibi | 200 `ListingDetail` |
| PATCH | `/api/listings/{id}/status` | sahibi | 200 `ListingDetail` |
| DELETE | `/api/listings/{id}` | sahibi | 204 |
| GET | `/api/users/me/listings` | giriş | `PageResponse<ListingSummary>` — her durumdaki kendi ilanları |

### Arama parametreleri (`GET /api/listings`)

Hepsi opsiyonel query parametresidir; verilmeyen filtre uygulanmaz.

| Parametre | Tip | Not |
|---|---|---|
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
  imageUrls: string[];            // 1–20 adet, her biri https:// ile başlar, en fazla 500 karakter
}
```

- **Görseller:** Şimdilik dosya yükleme yok; görseller `https://` URL listesi olarak gönderilir. Listedeki sıra gösterim sırasıdır, **ilk görsel kapak** olur. PUT görsel listesini tamamen değiştirir.
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
| 422 | Seri veya ilçe bulunamadı; SOLD ilanı güncelleme; izin verilmeyen durum geçişi | `message`'ı göster |
| 422 + `code: "PHONE_REQUIRED"` | Profilde telefon yokken ilan verme | Profil sayfasına yönlendir |
| 422 + `code: "EMAIL_NOT_VERIFIED"` | E-posta doğrulanmamışken ilan verme | "E-postanı doğrula" uyarısı + tekrar gönder butonu |

### Durum geçişleri (`PATCH /api/listings/{id}/status`, gövde `{ "status": "PASSIVE" }`)

| Mevcut | İzin verilen hedef |
|---|---|
| ACTIVE | PASSIVE, SOLD |
| PASSIVE | ACTIVE, SOLD |
| SOLD | — (son durum, ilan artık güncellenemez) |

Diğer her geçiş (PENDING_APPROVAL dahil) 422 döner. Yeni ilanlar şu an doğrudan ACTIVE yayınlanır; admin onayı ileride gelecek.

### Görünürlük

- Arama (`GET /api/listings`) sadece ACTIVE ilanları döner.
- Detay: ACTIVE olmayan ilanı sadece sahibi görür; diğerlerine 404 (403 değil, ilanın varlığı sızmasın diye). Sahibinin kendi PASSIVE/SOLD ilanını açabilmesi için detay isteğinde token gönderilmeli; mevcut `http` istemcisi bunu yapıyor.
- "İlanlarım" (`/api/users/me/listings`) her durumu döner; `status` alanına göre rozet gösterilebilir.

## Favoriler

| Metot | URL | Kimlik | Cevap |
|---|---|---|---|
| PUT | `/api/listings/{id}/favorite` | giriş | 204 — tekrar çağrılabilir; ilan yoksa veya ACTIVE değilse 404 |
| DELETE | `/api/listings/{id}/favorite` | giriş | 204 — tekrar çağrılabilir; favoride değilse de 204 |
| GET | `/api/users/me/favorites` | giriş | `PageResponse<ListingSummary>` — son eklenen başta; sadece `page`/`size` (sıralama parametresi yok) |

- İki uç da idempotent olduğu için favori butonunda optimistic update güvenle yapılabilir; hata olursa geri alınır.
- Favorilerim listesi sadece şu an ACTIVE olan ilanları gösterir. Pasife alınan ilanın favorisi silinmez; ilan tekrar yayına girince listede geri görünür.
- İlan silinirse favorisi de silinir.
- Favori değişince ilgili detayın (`favoriteCount`, `favoritedByMe`) ve favoriler listesinin cache'i geçersiz kılınmalı.

## Enum değerleri ve önerilen Türkçe etiketler

| Enum | Değer → etiket |
|---|---|
| `ListingStatus` | `PENDING_APPROVAL` Onay bekliyor · `ACTIVE` Yayında · `PASSIVE` Yayında değil · `SOLD` Satıldı |
| `CurrencyType` | `TRY` ₺ · `USD` $ · `EUR` € |
| `FuelType` | `GASOLINE` Benzin · `DIESEL` Dizel · `LPG` LPG · `HYBRID` Hibrit · `ELECTRIC` Elektrik |
| `GearType` | `MANUAL` Manuel · `AUTOMATIC` Otomatik · `SEMI_AUTOMATIC` Yarı otomatik |
| `BodyType` | Frontend'de `src/shared/config/bodyTypes.ts` zaten var |
| `Traction` | `FWD` Önden çekiş · `RWD` Arkadan itiş · `AWD` 4x4 |
| `VehicleCondition` | `NEW` Sıfır · `USED` İkinci el |
| `FromWho` | `OWNER` Sahibinden · `GALLERY` Galeriden · `AUTHORIZED_DEALER` Yetkili bayiden |

## Bilinen kısıtlar

- Gerçek görsel yükleme yok (sadece https URL).
- Kelimeyle arama (`q`) yok.
- Fiyat filtresi kur çevirmez.
- Satıcıyla iletişim sadece telefonla (mesajlaşma yok); e-posta hiç paylaşılmaz.
- Admin uçları (ilan onayı, katalog yönetimi) yok.

Backend tarafında uyumsuzluk veya eksik bulunursa backend reposunda düzeltilir; frontend sözleşmeyi kendi tarafında değiştirmemeli.
