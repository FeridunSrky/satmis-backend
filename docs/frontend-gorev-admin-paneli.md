# satmis.com Frontend Görevi 8: Admin Paneli

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut yönlendirme (router, korumalı rota bileşeni), auth/oturum store'u (`user.role` dahil), ilan detay ve liste bileşenleri, form kalıbı (React Hook Form + Zod) ve API katmanını oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

**Ön koşul:** Görev 7 (ilan onay süreci, sahip tarafı) tamamlanmış olmalı: `REJECTED` durumu, `rejectionReason` alanı ve durum etiketleri o görevde eklendi. Tamamlanmamışsa **dur ve bana bildir**.

## Hedef

`ADMIN` rolündeki kullanıcı için `/admin` altında bir yönetim bölümü:

1. **İlan onayı:** onay bekleyen ilanlar kuyruğu, ilanı inceleme, onaylama, gerekçeyle reddetme. Diğer durumlara göre listeleme (yayındaki ilanı yayından kaldırma dahil).
2. **Kullanıcılar:** e-postaya göre arama, engelleme ve engeli kaldırma.
3. **Katalog:** marka, model ve seri ekleme.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"Yönetim (admin)"** bölümünün tamamı (Kullanıcılar, İlan onayı, Katalog ekleme), ayrıca "Admin onayı" ve "Görünürlük". Özet:

- Tüm uçlar `/api/admin/**`. Token yoksa `401`, admin değilse `403`. Rol kararını backend verir; frontend sadece menü ve rotaları gizler.
- İlan onayı: `GET /api/admin/listings?status=` (varsayılan `PENDING_APPROVAL`, en eski başta), `GET /api/admin/listings/{id}`, `POST .../approve` `{version}`, `POST .../reject` `{version, reason}`. Onay/red sahibi ilanı arada değiştirdiyse `409` döner.
- Kullanıcılar: `GET /api/admin/users?q=`, `POST /api/admin/users/{id}/block`, `POST /api/admin/users/{id}/unblock`. Admin engellenemez (`422`).
- Katalog: `POST /api/admin/catalog/brands`, `/brands/{brandId}/models`, `/models/{modelId}/series`, gövde `{name}`. Tekrarlayan isim `409`. `Location` yok, listeyi yeniden çek.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut sayfalar ve testler etkilenmemeli.
- Yeni bağımlılık ekleme (tablo/grid kütüphanesi dahil).

## Kapsam dışı

- Katalogda adlandırma/silme, kullanıcı rolü değiştirme, admin işlem geçmişi (backend'de yok).
- İl/ilçe yönetimi.

---

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- Router yapısını, korumalı rota bileşenini ve `user.role`'ün nerede tutulduğunu bul, raporda yaz.

## Adım 1: Yönlendirme ve erişim

- Feature klasörü: `src/features/admin/{components,hooks,api,types}`.
- Rotalar (route bazlı code splitting, `React.lazy` + `Suspense`):
  - `/admin` → `/admin/ilanlar`'a yönlendirir
  - `/admin/ilanlar` (kuyruk), `/admin/ilanlar/:id` (inceleme)
  - `/admin/kullanicilar`
  - `/admin/katalog`
- Admin rota koruması: oturum yükleniyorsa yükleme durumu; giriş yoksa giriş sayfasına; `role !== 'ADMIN'` ise ana sayfaya yönlendir (ya da "Bu sayfaya erişiminiz yok" sayfası; mevcut kalıba uygun olanı seç). Bir admin isteği `403` dönerse aynı davranış uygulanır.
- Header/kullanıcı menüsünde sadece adminlere görünen "Yönetim" bağlantısı.
- Admin sayfalarında ortak bir alt gezinme (İlan onayı · Kullanıcılar · Katalog); `nav` + `a`/router link, aktif sekme belirgin.

## Adım 2: İlan onayı

**Kuyruk (`/admin/ilanlar`):**
- Durum seçici: Onay bekliyor (varsayılan), Yayında, Reddedildi, Yayında değil, Satıldı. Seçim URL'de (`?durum=`) tutulur ve backend'e enum olarak çevrilir (mevcut slug eşleme kalıbıyla).
- Liste mevcut ilan kartı/satır bileşeniyle gösterilir; tıklayınca inceleme sayfası açılır. Sayfalama mevcut kalıpla yapılır.
- Boş durum: "Onay bekleyen ilan yok."

**İnceleme (`/admin/ilanlar/:id`):**
- `GET /api/admin/listings/{id}` ile gelen detay; mevcut ilan detay bileşenleri yeniden kullanılır (görseller, araç bilgileri, açıklama, satıcı adı ve telefonu). Durum rozeti ve varsa red nedeni görünür.
- `PENDING_APPROVAL` ise "Onayla" ve "Reddet" butonları; `ACTIVE` ise sadece "Yayından kaldır" (red akışı, aynı form). Diğer durumlarda buton yok.
- Onay ve red, sayfanın yüklendiği detaydaki `version` ile gönderilir.
- Reddet: gerekçe alanı (textarea, `label`'lı, zorunlu, en fazla 500 karakter, Zod ile), gönder/iptal. Diyalog kullanılırsa erişilebilir olmalı (odak yönetimi, Esc ile kapanma); `window.confirm` kullanma.
- Başarıda: kısa bildirim, kuyruğa dönüş, kuyruk ve detay cache'i geçersiz.
- `409`: "İlan siz incelerken değişti. Güncel hali yüklendi, lütfen tekrar inceleyin." mesajı ve detayı yeniden çek (eski version ile tekrar gönderilmesin).
- `422`: backend `message`'ını göster ve detayı yeniden çek.

## Adım 3: Kullanıcılar (`/admin/kullanicilar`)

- E-posta arama formu (`role="search"`, `label`'lı input, Enter ile arama). Arama metni URL'de (`?q=`).
- Tablo (semantik `table`, `th scope`): ad soyad, e-posta, telefon, rol, e-posta doğrulandı mı, ilan sayısı, kayıt tarihi (yerel saatle), durum (Aktif/Engelli rozeti), işlem.
- Mobilde tablo yatay kayar ya da kart görünümüne geçer (mevcut responsive kalıba uygun).
- İşlem: aktif kullanıcıda "Engelle", engellide "Engeli kaldır". Adminlerde buton yok. Engelleme öncesi onay ("Kullanıcının oturumları kapanacak ve ilanları gizlenecek.") erişilebilir bir diyalogla.
- Başarıda liste yeniden çekilir. `422` ve `404` için backend `message`'ı gösterilir.
- Sayfalama mevcut kalıpla.

## Adım 4: Katalog (`/admin/katalog`)

- Üç sütun/adım: Markalar → Modeller → Seriler. Mevcut katalog `GET` uçlarıyla listelenir (sıralama backend'den gelir).
- Marka seçilince modelleri, model seçilince serileri gösterir. Seçimler URL'de tutulabilir (opsiyonel).
- Her listenin altında ekleme formu: `label`'lı input + "Ekle". Zod: zorunlu, en fazla 50 karakter.
- Başarıda input temizlenir, ilgili liste yeniden çekilir, yeni öğe seçili/vurgulu olabilir.
- `409`: input altında backend `message`'ı ("Bu isimde bir marka zaten var" vb.). `400`: `errors.name`.
- Ekranda "Katalog kayıtları eklendikten sonra değiştirilemez veya silinemez." notu.
- Katalog listeleri başka sayfalarda (ilan formu, filtreler) cache'leniyorsa ekleme sonrası o cache'ler de geçersiz kılınır.

## Adım 5: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. `USER` rolüyle `/admin/...` açılınca yönlendirilir; "Yönetim" bağlantısı görünmez. `ADMIN` ile görünür ve sayfa açılır.
2. Kuyruk varsayılan olarak `status=PENDING_APPROVAL` ister; durum değişince istek ve URL güncellenir.
3. İnceleme: "Onayla" detaydaki `version` ile gider ve kuyruğa döner. `409` dönünce mesaj çıkar ve detay yeniden çekilir.
4. Reddet: gerekçe boşken gönderilmez (alan hatası). Doluyken `version` ve `reason` ile gider. `ACTIVE` ilanda sadece "Yayından kaldır" görünür.
5. Kullanıcılar: arama `q` ile gider; "Engelle" onay diyaloğundan sonra `block` çağrılır ve liste yenilenir; admin satırında buton yok.
6. Katalog: marka ekleme başarıda listeyi yeniler; `409` mesajı input altında görünür; model listesi seçilen markaya göre istenir.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 6: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa ve admin hesabın varsa (backend `.env`'inde `APP_ADMIN_EMAILS` ile atanır) `npm run dev` ile dene:

1. Normal kullanıcıyla ilan ver → admin olarak kuyrukta gör → reddet (gerekçeyle) → sahibi olarak nedeni gör → düzenle → admin olarak onayla → ilan aramada görünür.
2. Admin olarak bir kullanıcıyı engelle → o kullanıcının sekmesinde bir sonraki istekte oturum düşer, ilanı aramadan kalkar → engeli kaldır → ilan geri gelir.
3. Katalogda yeni bir seri ekle → ilan verme formunda görünür. Aynı ismi tekrar eklemeyi dene → hata mesajı.

Admin hesabın yoksa ya da backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 7: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, `dangerouslySetInnerHTML`, `window.confirm`, rol kontrolünü sadece frontend'e bırakan varsayımlar, 150 satırı aşan bileşenler. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar ve rotalar
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
