# satmis.com Frontend Görevi 12: İlan Yorumları ve Kanıt Belgeleri

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut ilan detay sayfasını (`ListingDetailPage`), görsel yükleme kodunu (`uploadToStorage`, görsel yükleme hook'u, `prepareImage`), `ConfirmDialog`/`FormDialog`, şikayet akışını, satılmış araç detayını, admin bölümünü (`AdminLayout`, alt gezinme, `AdminListingReview`, şikayet kuyruğu) ve API katmanını (`src/shared/api`, `apiError`) oku. Aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

**Ön koşul:** Görev 11 (satılmış araçlar) tamamlanmış olmalı. Tamamlanmamışsa **dur ve bana bildir**.

## Hedef

1. **İlan yorumları:** ilan detayının altında herkese açık "Yorumlar" bölümü. Aracı inceleyen kullanıcı, ilanda yazmayan bir şeyi yorumla bildirir ve kanıt ekleyebilir (ekspertiz raporu PDF'i, fotoğraf).
2. **Sahibin cevabı:** ilan sahibi her yayındaki yoruma bir cevap yazar (isterse belge ekler).
3. **Onay:** yorum da cevap da admin onayından sonra görünür. Kullanıcı kendi yorumlarının durumunu "Yorumlarım" sayfasında görür.
4. **Admin:** yorumlar ilan onayından **ayrı bir sekmede** incelenir. Sekmelerde bekleyen iş sayısı rozeti görünür.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"İlan yorumları"** (Kurallar, Uçlar, Ek yükleme), **"Yönetim (admin)" → "Yorum onayı"** ve **"Sekme sayıları"**, "İstek sınırı (429)", genel kurallardaki hata kodları ve enum tablosundaki `CommentStatus`. Özet:

- `GET /api/listings/{id}/comments` (açık, token isteğe bağlı): onaylı yorumlar + onaylı cevap, en yeni başta. `canReply` sadece ilan sahibine `true` döner.
- `POST /api/listings/{id}/comments` `{ body, attachmentIds? }` → 201 `MyComment` (`PENDING_APPROVAL`). Doğrulanmış e-posta ister (`422 EMAIL_NOT_VERIFIED`). Kişi başı ilan başına 2 yorum (`409 COMMENT_LIMIT_REACHED`), reddedilen/silinen de sayılır.
- `POST /api/comments/{id}/reply` (sadece ilan sahibi, sadece onaylı yoruma) → 201. Aynı anda tek cevap (`409`), toplam 2 deneme (`409 REPLY_LIMIT_REACHED`).
- `DELETE /api/comments/{id}` (yazarı) → 204. Düzenleme yok.
- `GET /api/users/me/comments` (kendi yorum/cevapları, durum + red nedeni), `GET /api/users/me/comments/quota?listingId=`.
- Ekler: `POST /api/comment-attachments` → PUT → `POST /{id}/complete`. Görsel yüklemeyle aynı akış; türler JPEG/PNG/WEBP/**PDF**, en fazla 10 MB, yorum başına 5. Hata kodları `ATTACHMENT_*`.
- Admin: `GET /api/admin/comments?status=&listingId=`, `POST /{id}/approve` `{version}`, `POST /{id}/reject` `{version, reason}`; `GET /api/admin/moderation/counts`.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut sayfalar ve testler etkilenmemeli.
- Yeni bağımlılık ekleme (PDF önizleyici dahil).
- `dangerouslySetInnerHTML` yok: yorum metni düz metin olarak render edilir (satır sonları CSS `white-space: pre-line` ile).

## Kapsam dışı

- Yorum düzenleme, beğenme, yorumlara yorum (cevap sadece ilan sahibinden ve bir tane).
- Kullanıcılar arası özel mesajlaşma.
- PDF'i sayfada gömülü gösterme (PDF indirme olarak açılır).
- Yorum için ayrı bildirim merkezi (sahibe e-posta backend'den gider).

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır, başlangıç durumunu not et.
- Görsel yükleme hook'unun ve `uploadToStorage`'ın bugünkü imzasını, admin alt gezinmesinin hangi bileşende olduğunu raporda yaz.

## Adım 1: Tipler ve API

- Tipler (sözleşmedeki gibi): `CommentStatus`, `CommentAttachment`, `ListingComment`, `CommentReply`, `MyComment`, `CommentQuota`, `CommentRequest`, `AdminComment`, `ModerationCounts`.
- `ErrorCode`'a `COMMENT_LIMIT_REACHED`, `REPLY_LIMIT_REACHED`, `ATTACHMENT_LIMIT_REACHED`, `ATTACHMENT_NOT_UPLOADED`, `ATTACHMENT_INVALID`, `ATTACHMENT_NOT_AVAILABLE`.
- `CommentStatus` Türkçe etiketleri enum tablosundaki gibi, mevcut etiket eşleme kalıbıyla tek yerde.
- Yeni feature: `src/features/comments/{api,components,hooks,types}`. Admin uçları `src/features/admin` altında.
- TanStack Query anahtarları: ilanın yorumları, "Yorumlarım", ilanın yorum hakkı, admin yorum kuyruğu, moderasyon sayıları. Yorum/cevap gönderme ve silme sonrası "Yorumlarım" ve hak sorgusu geçersiz olur. Admin onay/red sonrası kuyruk, sayılar ve o ilanın yorumları geçersiz olur.

## Adım 2: Ek yükleme (görsel yüklemeyi genelleştir, kopyalama)

- Görsel yükleme hook'u ve `uploadToStorage` **yeniden kullanılır**, kopyalanmaz. Gerekirse hook'u uç adreslerini (`/api/listing-images` ↔ `/api/comment-attachments`), izin verilen türleri ve en fazla dosya sayısını parametre olarak alacak şekilde genelleştir. İlan formunun davranışı ve testleri **değişmemeli**.
- Görseller ilan görselleri gibi `prepareImage` ile küçültülür ve EXIF'ten temizlenir. **PDF küçültülmez**, olduğu gibi yüklenir (`type: application/pdf`).
- PDF'te `uploadHeaders` içinde `content-disposition` gelir. Diğer header'lar gibi **aynen** PUT'a eklenir. `uploadToStorage` header'ları zaten aynen ekliyorsa değişiklik gerekmez. Bunu testle doğrula.
- Dosya seçici: `accept="image/jpeg,image/png,image/webp,application/pdf"`, en fazla 5 dosya, 10 MB üstü istemcide reddedilir (mesaj: "Dosya en fazla 10 MB olabilir").
- Önizleme: görselde küçük resim. PDF'te dosya simgesi, dosya adı (sadece istemcide; backend saklamaz) ve boyut. Her öğede "Kaldır" (yüklendiyse `DELETE /api/comment-attachments/{id}`).
- Hatalar: `ATTACHMENT_INVALID` → "Dosya geçerli bir görsel ya da PDF değil". `ATTACHMENT_LIMIT_REACHED` → `message`. `ATTACHMENT_NOT_UPLOADED` ve depo `403` → mevcut tekrar dene davranışı.

## Adım 3: İlan detayında "Yorumlar" bölümü

İlan detayında, ilan bilgilerinin altında, semantik `section` + başlık "Yorumlar (N)". `N` sayfalı cevabın `totalElements` değeridir.

**Liste** (`GET /api/listings/{id}/comments`, giriş yapmışsa token ile):
- Her yorum `article`: yazar adı (`authorName`, ör. "Ayşe Y."), tarih (yerel saatle), metin (düz metin, `pre-line`), ekler.
- Ekler: görseller küçük resim. Tıklanınca büyük görüntü (mevcut galeri/lightbox varsa onunla, yoksa yeni sekmede `a`). PDF'ler `a href={url} download` + "Ekspertiz/Belge (PDF, 1,2 MB)" gibi bir metin, `rel="noopener noreferrer"`, `target="_blank"`. `img`'lerde anlamlı `alt` ("Yorum eki 1").
- `reply` doluysa yorumun altında girintili "İlan sahibi" etiketli cevap: metin, tarih, ekler.
- Sayfalama: "Daha fazla yorum" butonu (`useInfiniteQuery`) ya da mevcut sayfalama kalıbı.
- Loading, error ve boş durum: "Henüz yorum yok."
- Bölümün üstünde kısa bilgi metni: "Yorumlar yayınlanmadan önce incelenir. Aracı gördüyseniz deneyiminizi ve varsa ekspertiz raporunu paylaşabilirsiniz."

**Yorum formu** (bölümün üstünde ya da altında, tutarlı bir yerde):
- Giriş yapmamışsa: "Yorum yazmak için giriş yapın" (giriş sayfasına link, dönüş adresiyle).
- Kullanıcı ilanın sahibiyse form **gösterilmez** (`seller.id` = giriş yapan kullanıcı). Yerine: "Gelen yorumlara cevap verebilirsiniz."
- E-postası doğrulanmamışsa (`emailVerified === false`): mevcut "E-postanı doğrula" uyarısı/bandı, form gösterilmez.
- İlan satılmışsa (`status === 'SOLD'`) ya da yayında değilse form yok, yorumlar okunur.
- Hak: `GET /api/users/me/comments/quota?listingId=`. `remainingComments === 0` ise form yerine "Bu ilana yorum hakkınız doldu (en fazla 2)". Değilse form üstünde "Kalan yorum hakkınız: 1".
- Form: React Hook Form + Zod. `label`'lı textarea (20–2000 karakter, **kırpılmış** uzunluk doğrulanır, kalan karakter göstergesi) + Adım 2'deki ek alanı. Yükleme sürerken gönder butonu devre dışı.
- "Gönder" → onay (`ConfirmDialog`): "Yorumunuz incelendikten sonra yayınlanacak. Yorumlar düzenlenemez; silerseniz hakkınız geri gelmez. Gönderilsin mi?" → istek.
- Başarı: form temizlenir, ekler sıfırlanır, bildirim "Yorumunuz incelemeye gönderildi". Yorum listede **görünmez** (onay bekliyor). "Yorumlarım" bağlantısı gösterilir.
- Hatalar: `409 COMMENT_LIMIT_REACHED` → `message` + formu kapat. `422 ATTACHMENT_NOT_AVAILABLE` → "Eklerden biri kullanılamıyor, yeniden yükleyin" ve sorunlu ekleri sıfırla (hak tüketilmedi). `422 EMAIL_NOT_VERIFIED` → doğrulama uyarısı. `400` → alan hataları (`errors.body`) ya da `message`. `404` → "İlan artık yorum almıyor". `429` → genel 429 davranışı.

## Adım 4: İlan sahibinin cevabı

- Sahibi ilanı görüntülerken `canReply === true` olan yorumlarda "Cevap yaz" butonu. Buton açılır form gösterir (yorum formuyla **aynı** bileşen, farklı metinler; kopyalama).
- Cevap formu üstünde: "Her yoruma bir cevap hakkınız var. Cevabınız incelendikten sonra yayınlanır."
- Gönderim onayı → `POST /api/comments/{id}/reply` → bildirim "Cevabınız incelemeye gönderildi". Yorumlar sorgusu geçersiz olur (`canReply` `false` gelir). Cevap onaylanana kadar listede görünmez.
- Hatalar: `409` (zaten cevap var) → `message` + yorumları yenile. `409 REPLY_LIMIT_REACHED` → "Bu yoruma cevap hakkınız kalmadı". `403`/`404` → `message` + yenile.
- Satılmış ilanın detayında da (sahibi için) cevap verilebilir. Satılmış araçlar detay sayfasında (`/satilmis-araclar/:id`) yorum bölümü **salt okunur** gösterilir (form ve cevap butonu yok).

## Adım 5: "Yorumlarım" sayfası

- Rota: `/hesabim/yorumlar` (mevcut hesap/“İlanlarım” rotalarının kalıbına uy; farklıysa ona uydur ve raporla), `React.lazy`. Hesap menüsüne "Yorumlarım" bağlantısı.
- Liste (`GET /api/users/me/comments`): ilan başlığı (`/ilan/{listingId}` linki), "Yorum" ya da "Cevap" (`parentId` doluysa), durum rozeti (`CommentStatus` etiketi), tarih, metin (uzunsa kısaltılmış + "devamı"), ek sayısı. `REJECTED` ise red nedeni belirgin: "Red nedeni: …".
- Her öğede "Sil" (`danger`): onay "Yorum silinecek. Yorum hakkınız geri gelmez. Yorumun altındaki cevap da silinir." → `DELETE /api/comments/{id}` → bildirim ve listeler yenilenir. `404` da başarı sayılır.
- Loading, error ve boş durum: "Henüz yorum yazmadınız." Sayfalama mevcut kalıpla.

## Adım 6: Admin: "Yorumlar" sekmesi ve rozetler

**Gezinme:** admin alt gezinmesine "Yorumlar" sekmesi (İlan onayı · **Yorumlar** · Şikayetler · Kullanıcılar · Katalog). Rota `/admin/yorumlar`, `React.lazy`.

**Rozetler:** `GET /api/admin/moderation/counts` ile "İlan onayı" (`pendingListings`), "Yorumlar" (`pendingComments`), "Şikayetler" (`reportedListings`) sekmelerinde sayı rozeti (0 ise rozet yok). Ekran okuyucu için `aria-label`: "Yorumlar, 3 bekleyen". Admin sayfaları açıkken 60 sn'de bir ve her onay/red sonrası yenilenir.

**Yorum kuyruğu** (`/admin/yorumlar`):
- Durum filtresi: Onay bekleyen (varsayılan) · Yayında · Reddedilen (`status` parametresi; URL'de tutulur).
- Liste en eski başta. Her kart: tür ("Yorum" / "İlan sahibinin cevabı"), ilan başlığı (admin ilan incelemesine link `/admin/ilanlar/{listingId}`) + ilan durumu rozeti, yazar (ad soyad, e-posta), tarih, **tam metin**, ekler (görseller küçük resim, PDF'ler indirme bağlantısı). Cevapsa üstte "Cevaplanan yorum": `parent.authorName`, `parent.body`, `parent.status`.
- Uyarı metni (kartın üstünde, bir kez): "Eklerde kişisel veri (ad soyad, plaka, şasi no, telefon) varsa yorumu reddedin ve nedenini yazın."
- **Onayla:** `ConfirmDialog` → `POST /api/admin/comments/{id}/approve` `{ version }`.
- **Reddet / Yayından kaldır** (`APPROVED`'da "Yayından kaldır"): `FormDialog`'da zorunlu neden (en fazla 500, kalan karakter) → `POST /reject` `{ version, reason }`. Not: "Yazar bu nedeni görür."
- Hatalar: `409` → "Yorum siz incelerken değişti" + kuyruğu yenile. `422` → `message` (ör. "Cevaplanan yorum yayında değil"). `404` → kuyruğu yenile.
- Loading, error ve boş durum: "Onay bekleyen yorum yok."

**İlan incelemesinde:** `AdminListingReview` sayfasına "Bu ilanın yorumları" bağlantısı → `/admin/yorumlar?status=APPROVED&listingId={id}`. Kuyruk `listingId`'yi URL'den okur ve filtreyi "İlan #id" çipiyle gösterir (çip kapatılabilir).

## Adım 7: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az şunlar:

1. Detayda onaylı yorumlar maskeli adla, cevap "İlan sahibi" etiketiyle, PDF eki `download` bağlantısı olarak görünür; boş durum metni.
2. Anonim kullanıcıya form yerine giriş bağlantısı, sahibine "Gelen yorumlara cevap verebilirsiniz", doğrulanmamış kullanıcıya doğrulama uyarısı, hakkı bitmişe "yorum hakkınız doldu" görünür.
3. Kırpılmış metin 20 karakterden kısaysa istek gitmez. Geçerli metin + PDF eki → upload isteği `application/pdf` ve dosya boyutuyla gider. PUT `content-disposition` dahil `uploadHeaders` ile ve `Authorization` **olmadan** gider. Onaydan sonra yorum `attachmentIds` ile gönderilir, başarı bildirimi görünür, yorum listede görünmez.
4. `409 COMMENT_LIMIT_REACHED` ve `422 ATTACHMENT_NOT_AVAILABLE` mesajları.
5. Sahip `canReply` olan yorumda "Cevap yaz" görür, olmayan yorumda görmez. Cevap gönderilince `POST /api/comments/{id}/reply` çağrılır. `REPLY_LIMIT_REACHED` mesajı.
6. "Yorumlarım": durum rozetleri, red nedeni, silme onaydan sonra `DELETE` çağırır.
7. Admin kuyruğu: cevapta cevaplanan yorum görünür. Onay `version` ile gider. Red nedeni zorunlu. `409` mesajı. Sekme rozetleri sayıları gösterir, 0'da rozet yok. Normal kullanıcı `/admin/yorumlar`'a giremez.
8. İlan formundaki görsel yükleme testleri hâlâ geçer (genelleştirme bir şeyi bozmadı).

Testi geçirmek için testi zayıflatma. Hata koddaysa kodu düzelt.

## Adım 8: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de, SeaweedFS ve Mailpit (`docker compose up -d seaweedfs seaweedfs-init mailpit`) açıksa `npm run dev` ile:

1. Kullanıcı A ile yayındaki bir ilana (sahibi B) bir fotoğraf ve bir PDF ekli yorum yaz → yorum listede görünmez, "Yorumlarım"da "Onay bekliyor".
2. Admin olarak "Yorumlar" sekmesinde rozeti gör → yorumu onayla → ilan detayında yorum görünür. PDF tıklanınca indirilir, görsel açılır. Mailpit'te B'ye "ilanınıza yeni yorum yapıldı" maili ve `/ilan/{id}` linki gelir.
3. B olarak cevap yaz → admin reddetsin (nedenle) → B "Yorumlarım"da red nedenini görür, bir kez daha cevap yazar → admin onaylasın → cevap "İlan sahibi" etiketiyle görünür.
4. A ikinci yorumunu yazıp silsin → A için form "yorum hakkınız doldu" gösterir.
5. Admin yayındaki yorumu ilan incelemesindeki bağlantıdan bulup "Yayından kaldır" → yorum ve cevabı detaydan kalkar.

Bu kontrolde oluşturduğun kullanıcıları, ilanları ve yorumları raporda listele. Backend çalışmıyorsa bu adımı atla ve raporda belirt. Backend'i kendin başlatmaya çalışma.

## Adım 9: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, `dangerouslySetInnerHTML`, `window.confirm`, 150 satırı aşan bileşenler, kopyalanmış yükleme/form/diyalog kodu. Bulduklarını düzelt.
3. Kısa bir rapor yaz:
   - Eklenen/değişen dosyalar ve rotalar
   - Görsel yükleme hook'unda yaptığın genelleştirme
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrolün sonucu ya da neden yapılamadığı

`git commit` veya `git push` **yapma**.
