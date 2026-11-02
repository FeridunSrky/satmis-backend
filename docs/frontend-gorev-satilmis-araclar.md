# satmis.com Frontend Görevi 11: Satış Paneli ve Satılmış Araçlar

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut ilan arama sayfası (`ListingsPage`, `ListingSearchForm`, `filters/`, `searchParams.ts`, `ListingResults`, `ListingCard`), ilan detay sayfası (`ListingDetailPage`, `ListingGallery`, `ListingSpecs`), `OwnerActions` + `statusTransitions.ts`, `ConfirmDialog`/`FormDialog`, admin bölümü (`AdminLayout`, `AdminListingReview`) ve API katmanını oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

**Ön koşul:** Görev 10 (onay pencereleri) tamamlanmış olmalı. Tamamlanmamışsa **dur ve bana bildir**.

## Hedef

1. **Satış paneli:** ilan sahibi "Satıldı olarak işaretle"ye basınca bir panel açılır; satış fiyatını (pazarlıkla değişmiş olabilir) ve isteğe bağlı satış açıklamasını girer, onaylayınca ilan satılır.
2. **Tekrar yayına al:** satılmış ilanda "Tekrar yayına al" butonu; satış iptal olursa ilan yayına döner.
3. **Satılmış araçlar sayfası:** ayrı bir sayfa (`/satilmis-araclar`). Satılan araçlar burada gerçek satış fiyatlarıyla listelenir, filtrelenir, fiyat özeti ve detay sayfası vardır.
4. **Admin:** uydurma fiyatlı bir satışı sayfadan gizleme.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"İlanlar" → "Durum geçişleri"**, **"Satış"**, `ListingDetail.sale` (`ListingSale`), **"Satılmış araçlar"**, **"Yönetim (admin)" → "Satış kaydını gizleme"**, hata kodu `SALE_PRICE_OUT_OF_RANGE`. Özet:

- `POST /api/listings/{id}/sale` `{salePrice, note?, version}` → `ListingDetail` (`SOLD`, `sale` dolu). Fiyat ilan fiyatının %80–%110'u dışında `422` + `SALE_PRICE_OUT_OF_RANGE`; eski version `409`; satılamaz durum `422`.
- `DELETE /api/listings/{id}/sale` → `ListingDetail` (`ACTIVE`, `sale: null`).
- `PATCH /api/listings/{id}/status` artık `SOLD` kabul etmez (422). Bugünkü "Satıldı olarak işaretle" butonu bu ucu kullanıyorsa **değişmeli**.
- Satılmış ilan düzenlenemez ve **silinemez** (422).
- `GET /api/sold-vehicles` (filtreler `GET /api/listings` ile aynı, fiyat filtresi satış fiyatına; `sort`: `SoldVehicleSort`), `GET /api/sold-vehicles/summary` (aynı filtreler, para birimi başına sayı/ortalama/min/max), `GET /api/sold-vehicles/{id}`. Token gerekmez, satıcı bilgisi yok.
- `POST /api/admin/sold-vehicles/{id}/hide` → `ListingDetail` (`sale.hidden: true`).

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut sayfalar ve testler etkilenmemeli.
- Yeni bağımlılık ekleme.

## Kapsam dışı

- Satış tarihini kullanıcının girmesi (sunucu kaydettiği anı kullanır).
- Admin'in gizlemeyi geri alması (backend'de yok).
- Satılmış araçlar için grafik veya fiyat geçmişi.

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- "Satıldı olarak işaretle" butonunun bugün hangi ucu çağırdığını ve arama sayfasının filtre/URL kalıbını raporda yaz.

## Adım 1: Tipler ve API

- `ListingSale`, `ListingDetail.sale`, `MarkSoldRequest`, `SoldVehicleSummary`, `SoldVehicleDetail`, `SalePriceSummary`, `SoldVehicleSort` tipleri (sözleşmedeki gibi).
- `ErrorCode`'a `SALE_PRICE_OUT_OF_RANGE`.
- Satış uçları ilanlar feature'ının API'sinde; satılmış araçlar için yeni feature: `src/features/soldVehicles/{api,components,hooks,types}`. Admin gizleme `src/features/admin` altında.
- TanStack Query anahtarları: satış/geri alma sonrası ilan detayı, "İlanlarım", ilan araması ve satılmış araç listesi/özeti geçersiz kılınır.

## Adım 2: Satış paneli (ilan sahibi)

- `statusTransitions.ts`: `SOLD` hedefi `PATCH` aksiyonlarından çıkar. "Satıldı olarak işaretle" `ACTIVE` ve `PASSIVE` ilanda ayrı bir aksiyon olarak kalır ve **panel açar** (Görev 10'daki basit onay penceresinin yerini alır).
- Panel: mevcut `FormDialog` (yoksa `ConfirmDialog` kalıbında erişilebilir diyalog), React Hook Form + Zod.
  - Üstte ilanın özeti: başlık, marka/model/seri, yıl, km ve ilan fiyatı (salt okunur).
  - **Satış fiyatı:** zorunlu sayı alanı, ilan fiyatıyla dolu başlar, para birimi ilanınki (değiştirilemez, yanında gösterilir). Altında yardımcı metin: "İlan fiyatının %80'i ile %110'u arasında olmalı: 600.000 – 825.000 ₺". Aralık istemcide de doğrulanır (Zod), hesap: `ceil(fiyat × 0.80)` ve `floor(fiyat × 1.10)` kuruş hassasiyetinde; tek yerde (ör. `features/listings/salePrice.ts`) ve birim testli. Son söz backend'in: `422 SALE_PRICE_OUT_OF_RANGE` gelirse `message` alanın altında gösterilir.
  - **Satış açıklaması:** `label`'lı textarea, isteğe bağlı, en fazla 1000 karakter, kalan karakter göstergesi. Yardım metni: "Satılmış araçlar sayfasında herkese görünür."
  - Bilgi notu: "Satış fiyatınız ve açıklamanız satılmış araçlar sayfasında satıcı bilgisi olmadan yayınlanır. İlan yayından kalkar; satış iptal olursa tekrar yayına alabilirsiniz."
  - "Satışı kaydet" → ikinci onay (`ConfirmDialog`: "İlan satıldı olarak işaretlensin mi?" + "Satış fiyatı: 720.000 ₺") → istek `version` ile gider. "Vazgeç" hiçbir şey göndermez.
- Sonuçlar: başarıda panel kapanır, "İlan satıldı olarak işaretlendi" bildirimi, detay/listeler yenilenir. `409`: "İlan başka bir işlemle güncellendi" + detayı yeniden çek. `400`: alan hataları (`errors.salePrice`, `errors.note`). Diğer `422`: `message`.

## Adım 3: Satılmış ilan (sahibinin görünümü)

- `SOLD` ilanın detayında ve "İlanlarım"da: "Satıldı" rozeti; detayda satış bilgisi kartı (satış fiyatı, ilan fiyatı, satış tarihi yerel saatle, açıklama).
- `sale.hidden` ise kartta uyarı: "Bu satış yönetici tarafından satılmış araçlar sayfasından kaldırıldı."
- **"Tekrar yayına al"** butonu (sadece `SOLD`): Görev 10 kalıbında onay ("Satış kaydı silinecek ve ilan tekrar yayına girecek. Satış fiyatı ve açıklaması satılmış araçlar sayfasından kalkar.") → `DELETE /api/listings/{id}/sale` → "İlan tekrar yayında" bildirimi.
- `SOLD` ilanda "Düzenle" ve "Sil" görünmez (backend 422 döner). Silme için not: "Satılmış ilan silinemez; önce tekrar yayına alın." (buton yerine kısa metin ya da `title`).

## Adım 4: Satılmış araçlar sayfası

**Gezinme:** üst menüye, mobil menüye ve footer'a "Satılmış Araçlar" bağlantısı (`/satilmis-araclar`). Rotalar `React.lazy` ile:
- `/satilmis-araclar` (liste)
- `/satilmis-araclar/:id` (detay)

**Liste (`/satilmis-araclar`):**
- İlan aramasındaki filtre bileşenleri ve URL ↔ filtre eşlemesi (`searchParams.ts`) **yeniden kullanılır**, kopyalanmaz. Gerekirse ortak parçaya çıkar. Fiyat filtresinin etiketi "Satış fiyatı".
- Sıralama seçenekleri `SoldVehicleSort`: "En yeni satış", "En eski satış", "Satış fiyatı (artan/azalan)", yıl, km.
- **Fiyat özeti kartı** listenin üstünde, aynı filtrelerle `GET /api/sold-vehicles/summary`: "Bu aramada 23 araç satıldı · Ortalama 845.000 ₺ · En düşük 790.000 ₺ · En yüksek 910.000 ₺". Birden fazla para birimi varsa her biri ayrı satır. Sonuç yoksa kart gösterilmez. Özet ayrı sorgu: kendi loading/error durumu listeyi bloklamaz.
- Kart: kapak görseli, başlık, marka/model/seri, yıl, km, il/ilçe, **satış fiyatı belirgin**, ilan fiyatı ikincil ve üstü çizili değil ("İlan fiyatı: 750.000 ₺"), fark yüzdesi (ör. "%4 pazarlık"), satış tarihi. Satıcı bilgisi yok. Mevcut `ListingCard` görünümünden yararlan ama ilan kartıyla karışmasın ("Satıldı" etiketi).
- Loading, error, empty: "Bu filtrelerle satılmış araç bulunamadı."
- Sayfalama mevcut kalıpla.

**Detay (`/satilmis-araclar/:id`):**
- Galeri ve araç özellikleri mevcut bileşenlerle (`ListingGallery`, `ListingSpecs`).
- Satış kutusu: satış fiyatı, ilan fiyatı, fark, satış tarihi, satış açıklaması.
- Satıcı/telefon/favori/şikayet yok. `404`: "Satılmış araç bulunamadı" + listeye dönüş bağlantısı.

## Adım 5: Admin

- Satılmış araç detayında, giriş yapan kullanıcı `ADMIN` ise "Satışı gizle" butonu (`danger`). Onay: "Bu satış satılmış araçlar sayfasından kaldırılacak. İlan satılmış olarak kalır. Geri alınamaz." → `POST /api/admin/sold-vehicles/{id}/hide` → bildirim, satılmış araç listesine dönüş, listeler/özet geçersiz.
- Admin ilan incelemesinde (`AdminListingReview`) ilan `SOLD` ise satış bilgisi ve gizlenmemişse aynı "Satışı gizle" işlemi; gizlenmişse "Gizlendi" rozeti.

## Adım 6: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. "Satıldı olarak işaretle" panel açar; panel açılınca istek gitmez. Fiyat ilan fiyatıyla dolu; aralık dışı fiyatta alan hatası ve istek yok; geçerli fiyat + ikinci onaydan sonra `POST /sale` `salePrice`, kırpılmış `note` ve `version` ile gider.
2. Backend `422 SALE_PRICE_OUT_OF_RANGE` dönerse `message` fiyat alanının altında görünür.
3. Aralık hesabı birim testi (ör. 750000 → 600000–825000; kuruşlu fiyat).
4. `SOLD` ilanda "Tekrar yayına al" onaydan sonra `DELETE /sale` çağırır; "Düzenle" ve "Sil" görünmez; `sale.hidden` uyarısı görünür.
5. Satılmış araçlar listesi: filtre URL'den okunur ve isteğe gider; özet kartı TRY değerleriyle görünür; boş durum metni; satıcı bilgisi render edilmez.
6. Detay `404` mesajı.
7. Admin "Satışı gizle" onaydan sonra `hide` çağırır; normal kullanıcıya buton görünmez.

Testi geçirmek için testi zayıflatma; hata kodda ise kodu düzelt.

## Adım 7: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa `npm run dev` ile:

1. Yayındaki bir ilanı satış panelinden aralık dışı fiyatla satmayı dene (hata) → geçerli fiyat ve açıklamayla sat.
2. `/satilmis-araclar`'da aracı gör, filtrele, özeti kontrol et, detayı aç (çıkış yapmış haldeyken de).
3. "İlanlarım"dan "Tekrar yayına al" → araç satılmış listesinden çıkar, ilan aramada görünür.
4. Admin hesabın varsa tekrar sat ve admin olarak "Satışı gizle" → listeden çıkar, sahibi uyarıyı görür.

Bu kontrolde oluşturduğun ilanları ve kullanıcıları raporda listele. Backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 8: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, `dangerouslySetInnerHTML`, `window.confirm`, 150 satırı aşan bileşenler, kopyalanmış filtre/kart/diyalog kodu. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar ve rotalar
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
