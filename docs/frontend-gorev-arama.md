# satmis.com Frontend Görevi 6: Anahtar Kelimeyle İlan Arama

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. İlan listeleme/filtreleme kodunu (`src/features` altındaki ilan listesi, filtre paneli, URL parametresi ↔ filtre eşlemesi ve API çağrısı) oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

## Hedef

Kullanıcı bir arama kutusuna "passat dizel" gibi kelimeler yazıp ilan arayabilsin. Arama mevcut filtrelerle (marka, şehir, fiyat…) ve sıralamayla birlikte çalışsın, URL'de dursun (paylaşılabilir, geri tuşu çalışır).

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"Arama parametreleri"** bölümü ve altındaki **"Anahtar kelime (`q`) nasıl eşleşir"** notu. Özet:

- `GET /api/listings?q=...`. Opsiyonel, en fazla 100 karakter. Fazlası `400` döner ve `errors.q` alanında hata mesajı olur.
- Büyük/küçük harf ve Türkçe karakter farkını backend çözer. Frontend metni **olduğu gibi** gönderir: küçük harfe çevirme, Türkçe karakter dönüştürme yapma.
- Kelime başı eşleşir, kelimelerin hepsi geçmelidir. Özel karakterler hata üretmez.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut filtre/sıralama/sayfalama davranışı korunmalı, mevcut testler geçmeye devam etmeli.
- Yeni bağımlılık ekleme.

## Kapsam dışı

- Otomatik tamamlama/öneri, arama geçmişi, sonuçlarda kelime vurgulama, alaka düzeyine göre sıralama (backend'de yok).

---

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- İlan arama tiplerinin (`ListingSearchParams` vb.) ve URL ↔ filtre eşlemesinin nerede olduğunu bul, raporda yaz.

## Adım 1: Tip ve API katmanı

- Arama parametre tipine `q?: string` ekle.
- API çağrısında `q` boşsa veya sadece boşluksa parametreyi **hiç gönderme**. Değilse baştaki ve sondaki boşlukları kırpıp gönder.
- TanStack Query anahtarı `q`'yu içermeli (farklı aramalar farklı cache'te dursun).

## Adım 2: URL eşlemesi

- URL parametresi adı `q` olsun (`/ilanlar?q=passat+dizel`). Mevcut slug'lı filtre eşlemesine (`?kasa=sedan` gibi) aynı şekilde ekle.
- Yeni arama yapılınca sayfa numarası ilk sayfaya döner; diğer filtreler ve sıralama korunur.
- Sayfa değiştirme, sıralama değiştirme ve filtre ekleme `q`'yu kaybetmez.
- URL'den gelen `q` 100 karakterden uzunsa 100 karaktere kısaltılır (Zod şemasıyla doğrulama, mevcut kalıba uygun).

## Adım 3: Arama kutusu

- İlan listesi sayfasında filtrelerin üstünde (mobilde de görünür) bir arama formu: `<form role="search">`, görünür ya da `sr-only` `label` ("İlanlarda ara"), `type="search"` input, `maxLength={100}`, "Ara" butonu.
- Arama Enter'a ya da butona basınca yapılır, her tuş vuruşunda istek atılmaz.
- Input URL'deki `q` ile dolu gelir. Geri/ileri tuşunda input URL ile senkron kalır.
- Kutu temizlenip aranırsa `q` URL'den kaldırılır.
- Uygun yerde (header varsa) aynı forma sahip bir kısayol eklenebilir; header'dan arama ilan listesine `?q=` ile gider. Header yoksa bu kısmı atla.

## Adım 4: Durumlar

- Yükleniyor ve hata durumları mevcut liste kalıbıyla aynı.
- `q` varken sonuç yoksa: "“{q}” için ilan bulunamadı." mesajı ve aramayı/filtreleri temizleme butonu. (`q` metni React ile metin olarak basılır, HTML olarak değil.)
- `400` ve `errors.q` gelirse (normalde `maxLength` yüzünden gelmez): input altında backend'in mesajı gösterilir.
- Sonuç sayısı başlığında (varsa) arama metni de gösterilebilir: "“passat” için 12 ilan".

## Adım 5: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. Kutuya "passat dizel" yazıp Enter'a basınca istek `q=passat dizel` ile gider ve URL `?q=` içerir.
2. Baştaki/sondaki boşluk kırpılır. Sadece boşluk aranırsa istekte `q` yoktur ve URL'den kalkar.
3. `?q=golf&...` ile açılan sayfada input dolu gelir ve istek `q=golf` ile gider. Sayfa/sıralama değişince `q` korunur. Yeni arama sayfayı ilk sayfaya alır.
4. Türkçe karakterli metin (`IŞIKLI çağdaş`) değiştirilmeden gönderilir.
5. Sonuç yokken boş durum mesajı ve temizleme butonu görünür; buton `q`'yu kaldırır.
6. Yazarken istek atılmaz, sadece gönderince atılır.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 6: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa `npm run dev` ile tarayıcıda dene:

1. `audi`, `AUDİ`, `aud` aramaları mevcut Audi ilanını bulur. `audi bmw` sonuç vermez.
2. `hatasiz` ile `hatasız` aynı sonucu verir.
3. Arama + şehir filtresi + fiyat sıralaması birlikte çalışır. URL'yi kopyalayıp yeni sekmede açınca aynı sonuç gelir.
4. Geri tuşu önceki aramaya döner, input da güncellenir.

Backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 7: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any`, kullanılmayan kod, her tuşta istek atan kod, `dangerouslySetInnerHTML`. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
