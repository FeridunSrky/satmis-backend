# satmis.com Frontend Görevi 5: İlan Görsellerini Dosya Olarak Yükleme

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut ilan kodunu (ilan formu, ilan detayı, ilan API'si ve tipleri), `src/shared/api` katmanını ve hata işleme (`apiError`) kodunu oku; aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

## Hedef

İlan görselleri artık URL olarak yazılmıyor, kullanıcının cihazından **dosya olarak** yükleniyor. Backend'deki değişiklik **geriye uyumsuz**: ilan gövdesindeki `imageUrls` alanı kaldırıldı, yerine `imageIds` geldi. Bu görev bitene kadar frontend'de ilan oluşturma ve güncelleme çalışmaz.

- Kullanıcı ilan formunda görsel seçer (çoklu seçim). Görseller hemen yüklenir, önizlemesi görünür.
- Kullanıcı sıralamayı değiştirebilir; ilk görsel kapaktır. Görsel kaldırabilir.
- Yüklemeden önce görsel tarayıcıda küçültülür ve yeniden kodlanır. Böylece dosya küçülür ve fotoğraftaki EXIF bilgisi (GPS konumu dahil) silinir.
- İlan kaydedilirken görsellerin id'leri `imageIds` ile gönderilir.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı baştan sona oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"İlan görselleri (dosya yükleme)"** bölümü ile "İlanlar" altındaki **"Oluşturma ve güncelleme gövdesi"** ve **"İlan hata durumları"**. Özet:

1. `POST /api/listing-images` gövde `{ contentType, sizeBytes }` → `201 { id, uploadUrl, uploadHeaders, expiresAt }`
2. `PUT uploadUrl`, gövde dosyanın kendisi, header'lar `uploadHeaders`'ın **aynısı**, `Authorization` **yok** → depodan `200`
3. `POST /api/listing-images/{id}/complete` → `200 { id, url }`
4. İlan `POST`/`PUT` gövdesinde `imageIds: number[]` (1–20, tekrarsız, sıra = gösterim sırası, ilki kapak)

Yeni hata kodları: `IMAGE_LIMIT_REACHED`, `IMAGE_NOT_UPLOADED`, `IMAGE_INVALID`, `IMAGE_NOT_AVAILABLE`. Alan hataları artık `errors.imageIds` altında.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut davranış (auth, refresh, 429 işleme, ilan arama/detay) korunmalı; mevcut testler geçmeye devam etmeli. Sadece `imageUrls`'e bağlı testler yeni davranışa göre güncellenir.
- **Yeni bağımlılık ekleme.** Küçültme, sürükle-bırak ve ilerleme göstergesi tarayıcı API'leriyle yapılır.
- Token'ları ve imzalı URL'leri loglama; URL'nin içinde imza var.

## Kapsam dışı

- Backend'de değişiklik, sunucu tarafında görsel işleme, küçük önizleme (thumbnail) üretme, görsel kırpma/düzenleme.

---

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- `imageUrls` geçen her yeri bul (tipler, form şeması, form bileşeni, API, MSW handler'ları, testler) ve raporda listele.

## Adım 1: Tipler ve API katmanı

- İlan istek tipinde `imageUrls` kaldırılır, `imageIds: number[]` eklenir. Yeni hata kodları mevcut hata kodu sabitlerine/union tipine eklenir.
- Yeni tipler: `ImageUploadRequest`, `ImageUpload`, `UploadedImage` (sözleşmedeki gibi).
- İlan görselleri için API fonksiyonları, mevcut API katmanında (axios örneği ve interceptor'larıyla): `requestImageUpload`, `completeImageUpload`, `deleteImageUpload`.
- **Depoya PUT ayrı tutulur:** mevcut axios örneği ve interceptor'ları **kullanılmaz**, çünkü `Authorization` header'ı ve refresh mantığı bu isteğe karışmamalı. İlerleme göstermek için `XMLHttpRequest` kullan (`upload.onprogress`); `withCredentials = false`. Header'lar `uploadHeaders`'tan **aynen** eklenir, başka header eklenmez. Bu fonksiyon tek yerde durur (ör. `src/shared/api/uploadToStorage.ts`); component içinde ham istek olmaz.
- Depo hata cevabı XML'dir ve API hata formatında değildir. `2xx` dışı her cevabı genel bir yükleme hatasına çevir, gövdesini parse etmeye çalışma.

## Adım 2: Görseli yüklemeye hazırlama (küçültme + EXIF temizliği)

`src/lib` (ya da mevcut util klasörü) altında saf bir fonksiyon, ör. `prepareImage(file: File): Promise<Blob>`:

- Sadece `image/*` dosyaları kabul edilir. Diğerleri kullanıcıya anlaşılır bir hatayla reddedilir.
- `createImageBitmap(file, { imageOrientation: 'from-image' })` ile açılır, böylece telefonda yan çekilmiş fotoğraf doğru yönde olur. Uzun kenar 2048 px'i geçiyorsa oran korunarak küçültülür; küçükse büyütülmez.
- Canvas (destekleniyorsa `OffscreenCanvas`) ile `image/webp` kalite 0.85 olarak kodlanır. Tarayıcı WebP üretemiyorsa (`toBlob` sonucu `image/webp` değilse) `image/jpeg` kalite 0.85 kullanılır.
- Yeniden kodlanan dosyada EXIF olmaz. Orijinal dosya **hiçbir koşulda** olduğu gibi yüklenmez.
- Açılamayan dosyalarda anlaşılır bir hata verilir. Örneğin Chrome'da HEIC: "Bu görsel açılamadı, JPEG veya PNG olarak deneyin".
- Sonuç 10 MB'ı geçerse (pratikte olmaz) yükleme başlamadan hata verilir.
- `createImageBitmap` ve canvas sonrası kaynakları serbest bırak (`bitmap.close()`).

## Adım 3: Yükleme durumu (hook)

İlan formunun kullanacağı bir hook, ör. `useListingImages(initialImages)`. Her görsel bir öğedir:

```ts
type ListingImageItem =
  | { key: string; status: 'existing'; id: number; url: string }                   // düzenlemede ilandan gelen
  | { key: string; status: 'preparing' | 'uploading'; previewUrl: string; progress: number }
  | { key: string; status: 'uploaded'; id: number; url: string; previewUrl: string }
  | { key: string; status: 'error'; previewUrl: string; message: string; retry: () => void };
```

- Bir görsel için akış: `prepareImage` → `requestImageUpload({ contentType: blob.type, sizeBytes: blob.size })` → depoya PUT → `completeImageUpload`. Bu sırayla yürür.
- Aynı anda en fazla 3 görsel yüklenir; fazlası sırada bekler.
- Önizleme için `URL.createObjectURL` kullanılır; öğe kaldırılınca ya da component kapanınca `URL.revokeObjectURL` ile serbest bırakılır.
- Toplam en fazla 20 görsel. Fazlası seçilirse ilk 20'ye kadarı alınır ve kullanıcı bilgilendirilir.
- **Kaldırma:**
  - `uploaded` öğe kaldırılırsa `DELETE /api/listing-images/{id}` çağrılır. Hata verse de öğe listeden çıkar.
  - `existing` öğe (ilanın mevcut görseli) için `DELETE` **çağrılmaz** (backend `409` döner). Sadece listeden çıkar; ilan kaydedilince backend onu kaldırır.
  - Yüklenmekte olan öğe kaldırılırsa istek iptal edilir (`xhr.abort()`). Id alınmışsa `DELETE` çağrılır.
- **Hatalar:** `IMAGE_INVALID` → "Dosya geçerli bir görsel değil" (tekrar deneme yok, kullanıcı başka dosya seçsin). `IMAGE_LIMIT_REACHED`, `400`, `429` ve `503` → backend `message`'ı gösterilir.
  - Depo `403` ya da ağ hatası → "Yükleme başarısız" ve **tekrar dene** (tekrar dene 1. adımdan, yeni URL ile başlar).
  - `IMAGE_NOT_UPLOADED` → PUT'u bir kez tekrarla, yine olmazsa hata durumuna geç.
- Sıralama: öğeyi bir öne/arkaya taşıma ve "kapak yap" (başa alma) fonksiyonları.
- Hook dışarıya şunları verir: öğe listesi, `isUploading`, `imageIds` (sadece `existing` ve `uploaded` öğelerin id'leri, sırayla).

## Adım 4: İlan formu

- Mevcut URL giriş alanları kaldırılır, yerine görsel seçici gelir:
  - `<input type="file" accept="image/jpeg,image/png,image/webp,image/*" multiple>`, bir `label` ile.
  - Sürükle-bırak alanı da olabilir ama klavyeyle seçim mutlaka çalışmalı.
- Görseller grid'de küçük resim olarak görünür. Her birinde: yükleme ilerlemesi ya da hata mesajı, "kapak" etiketi (ilk öğe), sola/sağa taşıma ve "kapak yap" butonları, kaldır butonu.
  - Butonlar `button` elemanıdır ve anlamlı `aria-label` taşır (ör. "3. görseli kaldır").
  - Küçük resimlerde `alt` vardır.
- Form şeması (Zod): `imageIds` 1–20 eleman. Yükleme sürerken ya da hata durumundaki öğe varken **kaydet butonu devre dışı** ve neden devre dışı olduğu yazılır.
- **Oluşturma:** gövdeye `imageIds: hook.imageIds`.
- **Düzenleme:** hook, ilan detayındaki `images` (sıralı) ile `existing` öğeler olarak başlar. PUT gövdesinde mevcut ve yeni görsellerin id'leri birlikte, ekrandaki sırayla gönderilir.
- İlan cevabındaki hatalar:
  - `errors.imageIds` → görsel alanının altında gösterilir.
  - `IMAGE_NOT_AVAILABLE` → backend `message`'ı ve "Görselleri yeniden yükleyin" uyarısı. Form verisi kaybolmaz.
  - Diğer hatalar mevcut davranışla işlenir.
- İlan detay ve liste sayfalarında görsel gösterimi değişmez (`images[].url`, `coverImageUrl`). Görsellere `loading="lazy"` ekli değilse ekle. Görseli olmayan ilan (eski test verisi) için mevcut boş durum gösterimi çalışmalı.

## Adım 5: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. Depo PUT'u da MSW ile mock'la; `uploadUrl` için örneğin `http://storage.test/...` kullan.

1. `prepareImage`: büyük görsel uzun kenarı 2048'e iner, küçük görsel büyütülmez, sonuç `image/webp` ya da `image/jpeg`, resim olmayan dosya reddedilir. jsdom'da canvas yoksa bu fonksiyonun bağımlılıklarını (`createImageBitmap`, canvas) test içinde sahtele.
2. Görsel seçilince: upload isteği **küçültülmüş blob'un** `type` ve `size`'ıyla gider. PUT `uploadHeaders` ile ve **`Authorization` olmadan** gider. Ardından complete çağrılır ve önizleme görünür.
3. Kaydet: `imageIds` ekrandaki sırayla gider, ilk görsel kapak. Görsel sırası değiştirilince gönderilen sıra da değişir.
4. Yükleme sürerken kaydet butonu devre dışı.
5. Yüklenmiş görsel kaldırılınca `DELETE` çağrılır. Düzenlemedeki mevcut görsel kaldırılınca `DELETE` **çağrılmaz** ve PUT'ta o id yoktur.
6. `IMAGE_INVALID` → hata mesajı, öğe kaydı engeller ve kaldırılabilir. Depo `403` → tekrar dene çalışır ve yeni upload URL'si ister.
7. İlan kaydında `IMAGE_NOT_AVAILABLE` → mesaj görünür, form verisi korunur.
8. Kodda ve MSW handler'larında `imageUrls` kalmadı.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 6: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de, SeaweedFS `docker compose up -d seaweedfs seaweedfs-init` ile çalışıyorsa `npm run dev` ile tarayıcıda dene:

1. Doğrulanmış ve telefonu olan bir kullanıcıyla giriş yap. İlan formunda 3 telefon fotoğrafı seç (büyük, ideal olarak GPS'li). Network'te:
   - `POST /api/listing-images` 201.
   - `localhost:8333`'e PUT 200, `Authorization` header'ı yok.
   - `complete` 200.
2. Yüklenen dosyanın boyutu orijinalden küçük. Dosyayı `complete`'in döndüğü URL'den indirip EXIF'ine bak (`exiftool` varsa onunla); GPS bilgisi yok.
3. Sırayı değiştir, kapağı değiştir, birini kaldır, kaydet. Detay sayfasında sıra ve kapak doğru.
4. İlanı düzenle: mevcut bir görseli kaldır, yeni bir görsel ekle, kaydet. Detay doğru.
5. Resim olmayan bir dosyanın uzantısını `.jpg` yapıp seç. Uygun hata mesajı geliyor.

Backend ya da depo çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 7: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir:
   - Kalan `imageUrls`.
   - `any`.
   - İmzalı URL ya da token loglama.
   - Serbest bırakılmayan object URL'ler.
   - Depo PUT'una giden `Authorization` header'ı.
   - Kullanılmayan kod.

   Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar.
   - Küçültme sonuçları: örnek bir fotoğrafın önceki ve sonraki boyutu, çıkan format.
   - Bu belgeden sapmalar ve nedenleri.
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen).
   - Uçtan uca kontrol sonucu veya neden yapılamadığı.

`git commit` veya `git push` **yapma**.
