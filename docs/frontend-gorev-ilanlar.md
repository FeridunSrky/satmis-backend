# satmis.com Frontend Görevi 2: Katalog, İlanlar ve Favoriler

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut kodu (özellikle `src/shared/api/http.ts`, `src/shared/api/apiError.ts`, `src/shared/lib/formServerErrors.ts`, `src/features/auth`, `src/features/user`, `src/app/routes.tsx`) oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

## Hedef

Backend'de hazır olan katalog, ilan ve favori uçlarını frontend'e bağla: ilan arama/listeleme, ilan detayı, ilan verme, ilan düzenleme, ilanlarım ve favorilerim sayfaları.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı baştan sona oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Uç listesi, istek/cevap tipleri, doğrulama kuralları, hata kodları, durum geçişleri, görünürlük kuralları ve enum etiketleri orada. Bu belge onu tekrar etmez, sadece frontend tarafında ne yapılacağını anlatır.

- Backend çalışıyorsa OpenAPI şeması `http://localhost:8080/v3/api-docs` adresindedir; tipleri yazarken alan adlarını oradan doğrulayabilirsin. Backend çalışmıyorsa sadece `frontend-api.md` yeterli.
- Tipleri mevcut düzene uygun olarak elle yaz (`src/shared/api/types.ts` veya ilgili feature'ın `types` dosyası). `frontend-api.md` içindeki TypeScript tipleri birebir kullanılabilir.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut auth/profil davranışını bozma; mevcut testler geçmeye devam etmeli.
- Listede olmayan bağımlılık ekleme; gerekiyorsa nedenini yazıp ekle. Bu iş mevcut bağımlılıklarla (TanStack Query, React Hook Form, Zod, Zustand, axios, react-router) yapılabilir.

## Kapsam dışı (bunları YAPMA)

- Gerçek dosya/görsel yükleme (backend sadece `https://` URL listesi kabul ediyor).
- Kelimeyle arama, satıcıyla iletişim/mesajlaşma, admin paneli, ilan onay akışı.
- Backend'de olmayan bir uç için ekran.

---

## Adım 0: Ön kontrol

- `git status` çalıştır. Proje git deposu değilse veya çalışma ağacı temiz değilse **dur ve bana bildir** (geri alınabilir bir başlangıç noktası olmadan büyük değişiklik yapma).
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et. Zaten kırık bir şey varsa raporla.

## Adım 1: Rota ve URL eşleşmesi

Frontend rotaları Türkçe kalır (mevcut `/ilanlar`, `/giris`, `/kayit`, `/profil` düzeni). Backend uçlarıyla eşleşme:

| Frontend rotası | Erişim | Sayfa | Kullandığı backend uçları |
|---|---|---|---|
| `/ilanlar` | herkes | Arama + filtre + sonuç listesi | `GET /api/listings`, katalog uçları |
| `/ilan/:id` | herkes | İlan detayı | `GET /api/listings/{id}`, favori `PUT/DELETE`, sahibi için `PATCH status`, `DELETE` |
| `/ilan-ver` | sadece girişli | Yeni ilan formu | `POST /api/listings`, katalog uçları |
| `/ilan/:id/duzenle` | sadece girişli (sahibi) | İlan düzenleme formu | `GET /api/listings/{id}`, `PUT /api/listings/{id}`, katalog uçları |
| `/ilanlarim` | sadece girişli | Kendi ilanlarım (her durum) | `GET /api/users/me/listings`, `PATCH status`, `DELETE` |
| `/favorilerim` | sadece girişli | Favorilerim | `GET /api/users/me/favorites`, favori `DELETE` |

- Sayfalar mevcut `routes.tsx` düzeninde `lazy` ile eklenir; korumalı olanlar `ProtectedRoute` altına.
- `:id` sayı değilse veya ilan 404 dönerse "İlan bulunamadı" boş durumu göster (genel 404 sayfası da olabilir), uygulama çökmesin.
- Header, MobileNav ve UserMenu'ye ekle: herkes için "İlanlar"; girişli kullanıcı için "İlan ver" (belirgin buton), "İlanlarım", "Favorilerim".

### `/ilanlar` URL parametreleri

Filtre durumu **URL'de** tutulur (paylaşılabilir, geri tuşu çalışır); ayrı bir global store'a kopyalanmaz. Mevcut `?kasa=` sözleşmesi korunur.

| URL parametresi | Backend parametresi | Değer |
|---|---|---|
| `marka`, `model`, `seri` | `brandId`, `modelId`, `seriesId` | katalog id |
| `il`, `ilce` | `cityId`, `districtId` | katalog id |
| `min-fiyat`, `max-fiyat` | `minPrice`, `maxPrice` | sayı |
| `para-birimi` | `currency` | `TRY` / `USD` / `EUR` |
| `min-yil`, `max-yil` | `minYear`, `maxYear` | sayı |
| `max-km` | `maxKm` | sayı |
| `yakit` | `fuelType` | enum değeri |
| `vites` | `gearType` | enum değeri |
| `kasa` | `bodyType` | mevcut slug (`sedan`, `station-wagon`…) → `bodyTypes.ts` ile enum'a çevrilir |
| `durum` | `vehicleCondition` | enum değeri |
| `kimden` | `fromWho` | enum değeri |
| `hasar` | `hasDamageRecord` | `var` → `true`, `yok` → `false` |
| `sirala` | `sort` | `ListingSort` değeri |
| `sayfa` | `page` | **1'den başlar** (URL'de); backend'e `sayfa - 1` gönderilir |

- URL'den okunan her değer Zod ile doğrulanır; geçersiz/bilinmeyen değer sessizce yok sayılır (hata ekranı değil). Boş parametreler backend'e gönderilmez.
- Bu eşlemeyi tek bir dosyada topla (ör. `src/features/listings/searchParams.ts`): `URLSearchParams` → backend query objesi ve tersi. Bileşenler parametre adlarını kendileri yazmasın.
- Bir üst filtre değişince bağlı alt filtreler temizlenir: marka değişince `model` ve `seri`, il değişince `ilce`. Herhangi bir filtre değişince `sayfa` sıfırlanır.

## Adım 2: API katmanı

Yeni feature klasörleri mevcut düzende (`api`, `hooks`, `components`, `schemas`, tipler):

- `src/features/catalog`: markalar, modeller, seriler, iller, ilçeler. Katalog verisi nadiren değişir: uzun `staleTime` (ör. 1 saat). Alt liste sorguları üst id seçilmeden çalışmaz (`enabled`).
- `src/features/listings`: arama, detay, oluşturma, güncelleme, durum değiştirme, silme, ilanlarım.
- `src/features/favorites`: ekle, çıkar, favorilerim.

Kurallar:
- Tüm çağrılar `@/shared/api/http` üzerinden (token ve 401→refresh otomatik). Herkese açık uçlar da `http` kullanır; böylece girişli kullanıcının token'ı detay isteğine eklenir (sahibinin pasif ilanını görebilmesi ve `favoritedByMe` için gerekli).
- Query key'ler tek yerde tanımlanır (ör. `listingKeys.search(params)`, `listingKeys.detail(id)`, `listingKeys.mine(page)`, `favoriteKeys.list(page)`).
- Mutasyonlardan sonra invalidation:
  - Oluştur/güncelle/durum/sil → ilgili `detail`, arama listeleri, ilanlarım, favorilerim.
  - Favori ekle/çıkar → o ilanın `detail`'i ve favorilerim. Detaydaki favori butonu **optimistic update** ile anında değişir, hata olursa geri alınır (backend uçları idempotent).
- Para: `Intl.NumberFormat('tr-TR', { style: 'currency', currency })`. Tarih: backend UTC ISO gönderir, `tr-TR` yerel saatle göster.
- Enum → Türkçe etiket eşlemesi tek bir config dosyasında (`frontend-api.md` sonundaki tablo; `BodyType` için mevcut `bodyTypes.ts`).

## Adım 3: Sayfalar

Her veri çekiminde **loading, error ve empty** durumları ayrı ayrı ele alınır. Hata mesajı backend'in `message` alanından gelir (`toApiError`).

### `/ilanlar`
- Mevcut yer tutucu içerik kaldırılır; kasa tipi çipleri korunur.
- Filtre paneli: marka → model → seri kademeli seçim, il → ilçe kademeli seçim, fiyat aralığı + para birimi, yıl aralığı, maksimum km, yakıt, vites, durum, kimden, hasar kaydı. Mobilde panel açılır/kapanır.
- Sıralama seçimi ve sayfalama (önceki/sonraki + sayfa bilgisi, `totalPages`'e göre).
- Sonuç kartı (`ListingSummary`): kapak görseli (`loading="lazy"`, anlamlı `alt`, görsel yoksa yer tutucu), başlık, fiyat, il/ilçe, marka-model-seri, yıl, km, yakıt, vites. Kart `/ilan/:id`'ye link.
- Sonuç yoksa "Aramanıza uygun ilan bulunamadı" + "Filtreleri temizle" butonu.

### `/ilan/:id`
- Görseller (`displayOrder` sırasıyla; kapak büyük, diğerleri küçük önizleme; klavye ile gezilebilir), başlık, fiyat, açıklama, konum, araç bilgileri tablosu (Türkçe etiketlerle), ilan tarihi, görüntülenme ve favori sayısı, satıcı adı.
- Favori butonu: girişsiz kullanıcı tıklarsa `/giris?next=/ilan/:id`'ye yönlendir. `aria-pressed` ile durum bildirilir.
- Sahibi bakıyorsa (`seller.id === currentUser.id`): durum rozeti, "Düzenle", durum değiştirme ("Yayından kaldır" / "Yayına al" / "Satıldı olarak işaretle"; sadece `frontend-api.md`'deki izinli geçişler gösterilir) ve "Sil" (onay penceresiyle). SOLD ilanda düzenleme ve durum aksiyonları gizlenir.
- Silme sonrası `/ilanlarim`'a yönlendir ve toast göster.

### `/ilan-ver` ve `/ilan/:id/duzenle`
- Tek bir form bileşeni iki sayfada kullanılır. React Hook Form + Zod; Zod şeması backend doğrulama kurallarını yansıtır (`frontend-api.md` → "Oluşturma ve güncelleme gövdesi"). Model yılı üst sınırı çalışma anındaki yıla göre hesaplanır (bu yıl + 1).
- Form alanları gruplu: İlan bilgileri, Araç (marka → model → seri kademeli; formda marka/model sadece seçim içindir, backend'e sadece `seriesId` gider), Konum (il → ilçe; backend'e sadece `districtId`), Görseller.
- Görseller: 1–20 adet `https://` URL girişi; ekle/sil/yukarı-aşağı taşı; ilk görselin "Kapak" olduğu belirtilir; geçerli URL için küçük önizleme.
- Düzenlemede: detaydan gelen veriyle form doldurulur (marka/model/il seçimleri `vehicle.brandId`, `vehicle.modelId`, `location.cityId` ile), `version` gizli olarak tutulup PUT ile gönderilir.
- Hata davranışı:
  - 400 → `applyServerErrors` ile alanlara yaz. Form alan adları backend `errors` anahtarlarıyla aynı olmalı (`title`, `price`, `vehicle.modelYear`, `imageUrls`…), böylece eşleme ek kod gerektirmez.
  - 409 → "İlan başka bir yerde güncellendi" uyarısı + "Güncel hali yükle" butonu (detay yeniden çekilir, form sıfırlanır).
  - 422 → `message` form üstünde gösterilir.
  - 403 / 404 → "İlan bulunamadı veya düzenleme yetkiniz yok" boş durumu.
- Başarılı oluşturma/güncelleme sonrası `/ilan/:id`'ye yönlendir, toast göster.
- Sahibi olmayan kullanıcı `/ilan/:id/duzenle`'ye gelirse formu gösterme (detaydaki `seller.id` ile kontrol et; backend zaten 403 döner).

### `/ilanlarim`
- Sayfalı liste, her kartta durum rozeti ve sahibine özel hızlı aksiyonlar (durum değiştirme, düzenle, sil). Boş durumda "Henüz ilanın yok" + "İlan ver" butonu.

### `/favorilerim`
- Sayfalı liste, her kartta "Favoriden çıkar". Boş durumda açıklayıcı metin + "İlanlara göz at" butonu. Not: yayından kalkan ilanlar backend tarafından bu listede gösterilmez.

## Adım 4: Testler

Vitest + React Testing Library + MSW (mevcut `src/test/server.ts` ve `apiUrl` yardımcıları). Kullanıcı davranışını test et, implementasyon detayını değil. En az:

1. `searchParams` eşlemesi: URL → backend query (sayfa 1→0, `kasa` slug→enum, `hasar` var/yok, geçersiz değerlerin atlanması) ve ters yön.
2. `/ilanlar`: filtre seçimi URL'yi günceller ve doğru query ile istek atılır; marka değişince model/seri temizlenir; boş sonuçta boş durum görünür.
3. Kademeli seçim: marka seçilmeden model listesi istenmez.
4. İlan verme: geçersiz alanlarda Türkçe mesajlar; backend 400 `errors.vehicle.modelYear` dönerse ilgili alanda görünür; başarıda detay sayfasına gidilir.
5. Düzenleme: 409 gelince uyarı ve "Güncel hali yükle" çalışır.
6. Detay: sahibi değilse sahibi aksiyonları görünmez; sahibiyse sadece izinli durum geçişleri görünür; 404'te "İlan bulunamadı".
7. Favori: girişsiz tıklama `/giris?next=...`'e yönlendirir; girişli tıklamada buton anında değişir, istek hata verirse geri döner.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 5: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa (`/v3/api-docs` 200 dönüyorsa) `npm run dev` ile şu akışı elle dene: kayıt → ilan ver → `/ilanlar`'da filtreyle bul → detay → başka hesapla favorile → favorilerim → ilk hesapla düzenle → pasife al → sil. Backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 6: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any` kullanımı, `dangerouslySetInnerHTML`, token/şifre loglama, loading/error/empty durumu eksik veri çekimi, erişilebilirlik (label, alt, klavye, `aria`), kullanılmayan kod, 150 satırı geçen bileşenler. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar (feature bazında)
   - Rota ↔ backend uç eşleşmesi (Adım 1 tablosu son haliyle)
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (backend tarafında düzeltilmek üzere; uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
