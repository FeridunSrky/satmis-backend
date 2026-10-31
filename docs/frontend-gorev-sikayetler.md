# satmis.com Frontend Görevi 9: İlan Şikayetleri

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut ilan detay sayfası, auth/oturum store'u, admin bölümü (Görev 8: rotalar, alt gezinme, inceleme sayfası, red formu), erişilebilir diyalog bileşeni, form kalıbı (React Hook Form + Zod) ve API katmanını oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

**Ön koşul:** Görev 8 (admin paneli) tamamlanmış olmalı. Tamamlanmamışsa **dur ve bana bildir**.

## Hedef

1. **Kullanıcı tarafı:** giriş yapmış kullanıcı ilan detayında "İlanı şikayet et" ile nedeni seçip (isteğe bağlı açıklamayla) şikayet gönderir.
2. **Admin tarafı:** `/admin/sikayetler` altında şikayet edilen ilanlar kuyruğu ve ilan bazında inceleme: şikayetleri görme, "Şikayetleri yok say" veya "İlanı kaldır" (gerekçeyle).

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"İlan şikayeti"**, **"Yönetim (admin)" → "Şikayetler"**, "İlan onayı" (red kuralları), "İstek sınırı (429)" ve enum tablosundaki `ReportReason` / `ReportStatus` etiketleri. Özet:

- `POST /api/listings/{id}/reports` `{reason, description?}` → `201`. Token yok `401`, kendi ilanı `422`, açık şikayeti zaten var `409`, ilan görünür değil `404`, `OTHER` + boş açıklama `400`, sınır aşımı `429`.
- `GET /api/admin/reports` → şikayet edilen ilanlar (en çok şikayet alan başta).
- `GET /api/admin/reports/listings/{listingId}` → ilanın tüm şikayetleri.
- `POST /api/admin/reports/listings/{listingId}/dismiss` → `204`.
- `POST /api/admin/reports/listings/{listingId}/take-down` `{version, reason}` → `ListingDetail` (`REJECTED`). Eski version `409`, kaldırılamaz durum ya da açık şikayet yok `422`.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut sayfalar ve testler etkilenmemeli.
- Yeni bağımlılık ekleme.

## Kapsam dışı

- Kullanıcının kendi şikayetlerini listelemesi ve şikayet sonucunun kullanıcıya bildirilmesi (backend'de yok).
- Kullanıcı (satıcı) şikayet etme; sadece ilan şikayet edilir.

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- İlan detay sayfasında satıcı bilgisinin ve giriş yapan kullanıcının nerede olduğunu, Görev 8'deki red formunun ve diyalog bileşeninin yerini bul, raporda yaz.

## Adım 1: Tipler ve API

- `ReportReason`, `ReportStatus`, `ListingReport`, `ReportedListing`, `AdminListingReport` tipleri (sözleşmedeki gibi).
- Türkçe etiketler enum tablosundaki gibi, tek bir yerde (mevcut etiket eşleme kalıbıyla).
- Kullanıcı şikayeti API fonksiyonu ilanlar feature'ında, admin şikayet uçları `src/features/admin` altında.

## Adım 2: Kullanıcı tarafı (ilan detayı)

- Detayda, satıcı bilgisinin yakınında ikincil görünümlü "İlanı şikayet et" butonu (`button`).
  - Giriş yoksa buton görünür; tıklayınca mevcut kalıpla giriş sayfasına yönlendirir (dönüşte aynı ilana).
  - İlan giriş yapan kullanıcının kendisininse buton **görünmez**.
- Tıklayınca erişilebilir diyalog (odak yönetimi, Esc ile kapanma; `window.confirm` yok):
  - Neden: radyo grubu (`fieldset` + `legend`), altı seçenek, zorunlu.
  - Açıklama: `label`'lı textarea, en fazla 1000 karakter, kalan karakter göstergesi. "Diğer" seçiliyse zorunlu (Zod `superRefine`), değilse isteğe bağlı.
  - "Gönder" / "Vazgeç". Gönderirken buton pasif.
- Sonuçlar:
  - `201`: diyalog kapanır, "Şikayetiniz alındı, incelenecek." bildirimi. Bu oturumda buton "Şikayet edildi" olarak pasif kalabilir.
  - `409`: diyalog kapanır, backend `message`'ı bilgilendirme olarak gösterilir (hata rengi değil); buton "Şikayet edildi".
  - `400`: alan hataları (`errors.reason`, `errors.description`) ya da `message` formda gösterilir.
  - `404`: "Bu ilan artık yayında değil." mesajı.
  - `422`, `429`: backend `message`'ı (429 için mevcut `Retry-After` davranışı korunur).

## Adım 3: Admin tarafı

**Gezinme:** admin alt gezinmesine "Şikayetler" sekmesi (İlan onayı · Şikayetler · Kullanıcılar · Katalog). Rotalar `React.lazy` ile:
- `/admin/sikayetler` (kuyruk)
- `/admin/sikayetler/:listingId` (inceleme)

**Kuyruk (`/admin/sikayetler`):**
- Her satır bir ilan: mevcut ilan kartı/satır bileşeni + açık şikayet sayısı rozeti, neden dağılımı (ör. "Dolandırıcılık şüphesi 2 · Yanlış bilgi 1"), son şikayet tarihi (yerel saat). İlanın durum rozeti de görünür (`listing.status`).
- Sıralama backend'den gelir; sayfalama mevcut kalıpla.
- Boş durum: "İncelenecek şikayet yok."
- Satıra tıklayınca inceleme sayfası.

**İnceleme (`/admin/sikayetler/:listingId`):**
- Üstte ilan: `GET /api/admin/listings/{id}` ile gelen detay; Görev 8'deki inceleme bileşenleri yeniden kullanılır (durum, red nedeni, görseller, açıklama, satıcı).
- Altta şikayet listesi (`GET /api/admin/reports/listings/{listingId}`): neden, açıklama, şikayet eden (ad soyad, e-posta), tarih, durum rozeti. Açık olanlar belirgin. Sayfalama mevcut kalıpla.
- Açık şikayet varsa iki işlem:
  - **"Şikayetleri yok say"**: onay diyaloğu ("İlan yayında kalacak, açık şikayetler kapanacak.") → `dismiss`.
  - **"İlanı kaldır"**: sadece ilan `ACTIVE` veya `PENDING_APPROVAL` ise. Görev 8'deki red formu yeniden kullanılır (gerekçe zorunlu, en fazla 500, Zod) → `take-down`, detaydaki `version` ile.
  - İlan başka bir durumdaysa (pasif, satıldı, zaten reddedildi) sadece "Şikayetleri yok say" görünür ve kısa bir not: "İlan yayında olmadığı için kaldırılamaz."
- Başarıda: bildirim, kuyruğa dönüş, şikayet kuyruğu + ilan detayı + ilan onay kuyruğu cache'leri geçersiz.
- `409`: "İlan siz incelerken değişti. Güncel hali yüklendi, lütfen tekrar inceleyin." ve detayı yeniden çek.
- `422`: backend `message`'ı göster, detayı ve şikayetleri yeniden çek.

## Adım 4: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. Detay: ilan sahibine buton görünmez; giriş yokken tıklama giriş sayfasına yönlendirir.
2. Şikayet formu: neden seçilmeden gönderilmez; "Diğer" seçili ve açıklama boşken alan hatası; geçerli formda istek `reason` ve kırpılmış `description` ile gider, başarı bildirimi çıkar.
3. `409` cevabında bilgilendirme mesajı görünür ve buton "Şikayet edildi" olur.
4. Admin kuyruğu: şikayet sayısı ve neden dağılımı Türkçe etiketlerle görünür; boş durum metni.
5. İnceleme: "Şikayetleri yok say" onaydan sonra `dismiss` çağırır ve kuyruğa döner. "İlanı kaldır" gerekçe ve detaydaki `version` ile `take-down` çağırır. Pasif ilanda "İlanı kaldır" görünmez. `409`'da mesaj ve yeniden çekme.
6. `USER` rolüyle `/admin/sikayetler` açılınca yönlendirilir.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 5: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa ve admin hesabın varsa `npm run dev` ile dene:

1. Normal kullanıcıyla yayındaki bir ilanı şikayet et → aynı ilanı tekrar şikayet etmeyi dene (`409` bilgisi).
2. Admin olarak kuyrukta ilanı gör → incele → "Şikayetleri yok say" → ilan kuyruktan çıkar, yayında kalır.
3. Tekrar şikayet et → admin olarak "İlanı kaldır" (gerekçeyle) → ilan aramadan kalkar, sahibi "İlanlarım"da red nedenini görür.

Bu kontrolde oluşturduğun ilanları ve kullanıcıları raporda listele. Admin hesabın yoksa ya da backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 6: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, `dangerouslySetInnerHTML`, `window.confirm`, 150 satırı aşan bileşenler, Görev 8 bileşenlerinin kopyalanması (yeniden kullanılmalı). Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar ve rotalar
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
