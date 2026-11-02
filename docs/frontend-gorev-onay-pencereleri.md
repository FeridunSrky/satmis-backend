# satmis.com Frontend Görevi 10: Onay Pencereleri

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut `src/shared/components/ConfirmDialog.tsx` bileşenini, onu kullanan yerleri (`OwnerActions` silme, `UserStatusAction` engelleme, `ReportActions`) ve ilan formunu (`ListingForm`, `CreateListingPage`, `EditListingPage`) oku ve aynı kalıbı kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

**Ön koşul:** Görev 6–9 tamamlanmış olmalı. Tamamlanmamışsa **dur ve bana bildir**.

## Hedef

Kullanıcı bir butona yanlışlıkla bastığında işlem hemen uygulanmasın. Sonucu kolayca geri alınamayan ya da başkalarını etkileyen her işlem önce onay penceresi açar; işlem ancak "onayla" butonuyla gider.

Backend'de değişiklik yok; bu görev sadece frontend.

## Onay isteyecek işlemler

Bugün onaysız çalışanlar (hepsine onay eklenecek):

| Yer | Buton | Başlık | Açıklama | Onay butonu |
|---|---|---|---|---|
| Yeni ilan formu | Gönder | İlan gönderilsin mi? | İlanınız admin onayından sonra yayına girecek. | Gönder |
| İlan düzenleme formu (onayı bozan değişiklik var) | Kaydet | Değişiklikler kaydedilsin mi? | Başlık, açıklama, fiyat veya fotoğraf değiştiği için ilan tekrar admin onayına gidecek ve onaylanana kadar yayında görünmeyecek. | Kaydet ve onaya gönder |
| İlan düzenleme formu (onayı bozmayan değişiklik) | Kaydet | Değişiklikler kaydedilsin mi? | İlanınız güncellenecek. | Kaydet |
| `OwnerActions` | Yayından kaldır | İlan yayından kaldırılsın mı? | İlan aramada ve detayda başkalarına görünmeyecek. İstediğinizde tekrar yayına alabilirsiniz. | Yayından kaldır |
| `OwnerActions` | Yayına al | İlan yayına alınsın mı? | İlan tekrar aramada görünecek. | Yayına al |
| `OwnerActions` | Satıldı olarak işaretle | İlan satıldı olarak işaretlensin mi? | İlan yayından kalkacak. | Satıldı olarak işaretle |
| Admin ilan inceleme (`ReviewActions`) | Onayla | İlan onaylansın mı? | İlan hemen yayına girecek. | Onayla |
| Admin kullanıcılar (`UserStatusAction`) | Engeli kaldır | Engel kaldırılsın mı? | Kullanıcı tekrar giriş yapabilecek ve ilanları yeniden görünecek. | Engeli kaldır |
| Admin katalog | Ekle (marka/model/seri) | `<ad>` eklensin mi? | Eklenen kayıt silinemez ve adı değiştirilemez. | Ekle |

Zaten onaylı olanlar (dokunma, sadece metinlerin bu tabloyla tutarlı olduğunu kontrol et): ilan silme, kullanıcı engelleme, admin red (gerekçe formu), şikâyet "yok say" ve "ilanı kaldır".

Onay **istemeyecek** olanlar: favori ekleme/çıkarma, filtre ve sıralama, çıkış yap, form içindeki fotoğraf ekleme/çıkarma/sıralama (form kaydedilene kadar hiçbir şey kalıcı değil).

> "Satıldı olarak işaretle" bir sonraki görevde (Görev 11) satış bilgisi girilen bir panele dönüşecek. Bu görevde sadece onay penceresi ekle; panel işini yapma.

## Kurallar

- Hepsi mevcut `ConfirmDialog` ile. `window.confirm` yok, yeni diyalog bileşeni yok. `ConfirmDialog`'a yeni bir özellik gerekirse (ör. onay butonunun rengi) bileşene ekle, kopyalama.
- Tehlikeli işlemlerde (yayından kaldır, sil, engelle) onay butonu `danger` görünümünde; diğerlerinde birincil buton.
- Onay butonu istek sürerken pasif ve yükleniyor görünür (`isConfirming`); çift tıklama iki istek göndermez.
- "Vazgeç" ve Esc pencereyi kapatır, hiçbir istek gitmez; odak işlemi açan butona döner.
- Başarıda pencere kapanır ve mevcut bildirim (toast) gösterilir. Hatada pencere kapanır ve mevcut hata davranışı korunur (`toApiError(error).message`; `409`/`422` gibi özel durumların bugünkü davranışı değişmez).
- Formlarda onay penceresi **doğrulamadan sonra** açılır: geçersiz formda pencere çıkmaz, alan hataları gösterilir. Onaylanınca mevcut gönderim kodu aynen çalışır.
- Düzenleme formunda "onayı bozan değişiklik" kontrolü için `src/features/listings/approval.ts` içine saf bir yardımcı yaz (ör. `needsReapproval(listing, formValues)`), backend kuralıyla birebir: ilan `REJECTED` ise her kayıt onaya gider; `ACTIVE`/`PASSIVE`'de başlık, açıklama, fiyat, para birimi ya da fotoğraf **seti** değiştiyse gider (fotoğraf sırası saymaz; metinler backend'e giden kırpılmış haliyle, fiyat sayısal değeriyle karşılaştırılır). `PENDING_APPROVAL` zaten onayda. Yardımcının birim testi olsun. Kayıttan sonraki son söz yine backend'in döndüğü `status`'tadır.
- Her işlemin başlığı, açıklaması ve buton metni tek bir yerde dursun (ör. `statusTransitions.ts`'teki `StatusAction`'a `confirmTitle`, `confirmDescription` alanları). Bileşen içine dağınık metin yazma.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut sayfalar ve testler etkilenmemeli (onay adımı eklendiği için güncellenmesi gereken testleri güncelle; testi zayıflatma).
- Yeni bağımlılık ekleme.

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- Tablodaki her butonun bugün hangi dosyada olduğunu bul, raporda yaz.

## Adım 1: İlan sahibi işlemleri

`OwnerActions` içindeki durum butonları (yayından kaldır, yayına al, satıldı) onaylı hale gelir. Hangi işlemin penceresinin açık olduğu tek bir state ile tutulur (ör. `pendingAction: StatusAction | 'delete' | null`), her işlem için ayrı boolean açma.

## Adım 2: İlan formları

Yeni ilan ve düzenleme formuna onay adımı. Düzenlemede açıklama metni, onayı bozan değişiklik olup olmamasına göre değişir.

## Adım 3: Admin işlemleri

Admin onayı, engel kaldırma ve katalog ekleme onaylı hale gelir.

## Adım 4: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. "Yayından kaldır"a basınca istek **gitmez**, pencere açılır; "Vazgeç" sonrası hâlâ istek yok; onaylayınca `PATCH /status` `PASSIVE` ile gider ve bildirim çıkar.
2. Onay butonu istek sürerken pasif; iki tıklama tek istek gönderir.
3. Yeni ilan formu: geçersiz formda pencere açılmaz; geçerli formda pencere açılır, onaydan sonra `POST /api/listings` gider.
4. Düzenleme: fiyat değişince açıklamada "tekrar admin onayına gidecek" yazar; sadece km değişince yazmaz.
5. Admin "Onayla" pencereden sonra `approve` çağırır.
6. Esc pencereyi kapatır ve odak butona döner.

Testi geçirmek için testi zayıflatma; hata kodda ise kodu düzelt.

## Adım 5: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa `npm run dev` ile tablodaki her butonu bir kez dene: vazgeç → istek yok; onayla → işlem uygulanır. Oluşturduğun ilanları ve kullanıcıları raporda listele. Backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 6: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, `window.confirm`, 150 satırı aşan bileşenler, kopyalanmış diyalog kodu. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar
   - Tablodan sapmalar ve nedenleri (eklediğin ya da onay koymadığın butonlar)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
