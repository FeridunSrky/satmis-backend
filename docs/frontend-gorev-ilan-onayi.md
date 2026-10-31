# satmis.com Frontend Görevi 7: İlan Onay Süreci (ilan sahibi tarafı)

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. İlan verme, ilan düzenleme, ilan detayı ve "İlanlarım" kodunu (`src/features` altındaki ilan bileşenleri, durum rozetleri, `ListingStatus` tipi ve etiketleri) oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

## Hedef

İlanlar artık admin onayından sonra yayına giriyor. İlan sahibi:

- İlan verdikten sonra ilanının **onay beklediğini** açıkça görsün (ilan "kayboldu" sanmasın).
- Reddedilen ilanında **red nedenini** görsün ve düzeltip tekrar onaya gönderebilsin.
- Yayındaki ilanında başlık, açıklama, fiyat veya fotoğraf değiştirirse ilanın **tekrar onaya gideceğini** önceden bilsin.

Admin ekranları bu görevin kapsamında değil, ayrı bir görevde gelecek.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"Admin onayı"**, **"Durum geçişleri"**, **"Cevap tipleri"** (`ListingDetail.rejectionReason`) ve **"Enum değerleri"** (`REJECTED`) bölümleri. Özet:

- Yeni durum: `REJECTED` (etiket: "Reddedildi"). `PENDING_APPROVAL` ("Onay bekliyor") artık gerçekten kullanılıyor.
- `POST /api/listings` cevabı `status: "PENDING_APPROVAL"` döner.
- `ListingDetail.rejectionReason: string | null`: sadece `REJECTED` iken dolu.
- `ACTIVE`/`PASSIVE` ilanda başlık, açıklama, fiyat/para birimi veya görsel **seti** değişirse `PUT` cevabı `status: "PENDING_APPROVAL"` döner. Sadece km/renk/ilçe gibi alanlar ya da görsel **sırası** değişirse durum aynı kalır.
- `REJECTED` ilan herhangi bir düzenlemeyle tekrar `PENDING_APPROVAL` olur, `rejectionReason` `null` olur.
- `PATCH /status` ile `PENDING_APPROVAL` ve `REJECTED` ilanlardan çıkılamaz (422). Bu durumlarda pasife alma/satıldı butonları gösterilmez.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut ilan verme/düzenleme/silme/durum değiştirme davranışı korunmalı, mevcut testler geçmeye devam etmeli (sadece "ilan verince ACTIVE olur" varsayan testler yeni davranışa göre güncellenir).
- Yeni bağımlılık ekleme.

## Kapsam dışı

- Admin paneli ekranları (onay kuyruğu, onay/red, kullanıcı engelleme).
- Onay/red sonucunun e-posta veya bildirimle iletilmesi (backend'de yok).

---

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- `ListingStatus` tipinin, durum etiketlerinin/rozetlerinin ve durum geçiş butonlarının nerede olduğunu bul, raporda yaz.

## Adım 1: Tipler ve etiketler

- `ListingStatus` union'ına `'REJECTED'` ekle; etiket "Reddedildi". Rozet rengi: reddedildi için hata/uyarı tonu, onay bekliyor için nötr/bilgi tonu (mevcut tasarım token'larıyla).
- `ListingDetail` tipine `rejectionReason: string | null` ekle.
- Durum → izin verilen geçişler eşlemesi varsa `PENDING_APPROVAL` ve `REJECTED` için boş liste olmalı.

## Adım 2: İlan verme sonrası

- İlan başarıyla oluşturulunca mevcut yönlendirme korunur, ama kullanıcıya "İlanınız alındı, onaylandıktan sonra yayına girecek." mesajı gösterilir (mevcut toast/bildirim kalıbıyla).
- Yönlendirilen sayfada (detay veya İlanlarım) ilan "Onay bekliyor" rozetiyle görünür.

## Adım 3: İlan detayı (sahibi bakarken)

- `PENDING_APPROVAL`: üstte bilgi kutusu: "Bu ilan onay bekliyor. Onaylanana kadar sadece siz görebilirsiniz."
- `REJECTED`: üstte uyarı kutusu: "İlanınız yayına alınmadı." ve altında `rejectionReason` (metin olarak, HTML değil). Yanında "Düzenle" butonu (mevcut düzenleme sayfasına gider).
- Bu iki durumda pasife al / satıldı butonları gösterilmez; sil ve düzenle kalır.

## Adım 4: İlan düzenleme

- İlan `ACTIVE` veya `PASSIVE` ise formun üstünde kalıcı bir not: "Başlık, açıklama, fiyat veya fotoğraf değişikliği ilanı yeniden onaya gönderir; onaylanana kadar ilan yayında görünmez."
- İlan `REJECTED` ise formun üstünde red nedeni ve "Kaydettiğinizde ilan tekrar onaya gönderilir." notu.
- Kaydettikten sonra `PUT` cevabındaki `status`'a bak:
  - Önceki durum `ACTIVE`/`PASSIVE`/`REJECTED` iken cevap `PENDING_APPROVAL` ise: "İlanınız tekrar onaya gönderildi." mesajı.
  - Aksi halde mevcut "kaydedildi" mesajı.
- Durumu frontend'de tahmin etme; her zaman cevaptaki `status`'u kullan.
- Kaydettikten sonra ilgili detay, İlanlarım ve arama cache'leri geçersiz kılınır (ilan yayından kalkmış olabilir).

## Adım 5: İlanlarım

- Rozetler yeni durumları gösterir. `REJECTED` ilan kartında kısa bir "Reddedildi, nedenini görmek için tıklayın" ipucu yeterli.
- Durum filtresi/sekmesi varsa "Onay bekliyor" ve "Reddedildi" seçenekleri eklenir.

## Adım 6: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. İlan oluşturma `PENDING_APPROVAL` dönünce onay bekliyor mesajı görünür.
2. Sahibi `PENDING_APPROVAL` ilanın detayında bilgi kutusunu görür; pasife al/satıldı butonları yok.
3. `REJECTED` ilanın detayında red nedeni metin olarak görünür (`<b>` gibi içerik HTML olarak işlenmez) ve "Düzenle" butonu düzenleme sayfasına gider.
4. `ACTIVE` ilan düzenleme formunda onay uyarısı görünür; `PUT` cevabı `PENDING_APPROVAL` dönünce "tekrar onaya gönderildi" mesajı çıkar; `ACTIVE` dönünce normal mesaj çıkar.
5. İlanlarım'da `REJECTED` ve `PENDING_APPROVAL` rozetleri doğru etiketle görünür.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 7: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa `npm run dev` ile tarayıcıda dene:

1. Yeni ilan ver → "onay bekliyor" mesajı, İlanlarım'da rozet. Çıkış yapıp aramada ilanın **görünmediğini** doğrula.
2. Onay ve red için admin ekranı henüz yok; bu adımları Swagger'dan (`/swagger-ui.html`) admin hesabıyla yap: `POST /api/admin/listings/{id}/reject` (gövde `{ "version": ..., "reason": "Deneme" }`). Sonra sahibi olarak detayda nedeni gör, düzenle, tekrar onaya gittiğini doğrula. Admin hesabın yoksa bu adımı atla ve raporda belirt.
3. Yayındaki bir ilanın sadece km'sini değiştir → durum `ACTIVE` kalır. Fiyatını değiştir → "tekrar onaya gönderildi".

Backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 8: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, `dangerouslySetInnerHTML`, frontend'de durum tahmin eden kod. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
