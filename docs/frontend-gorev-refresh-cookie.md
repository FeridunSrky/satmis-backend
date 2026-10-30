# satmis.com Frontend Görevi 4: Refresh Token'ı httpOnly Cookie'ye Taşıma

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut auth kodunu (özellikle `src/shared/api/http.ts`, `src/shared/api/apiError.ts`, `src/features/auth` ve token/oturum tutan store) oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

## Hedef

Refresh token artık JavaScript'in hiç göremediği bir **httpOnly cookie**'de duruyor. Frontend refresh token'ı saklamayı tamamen bırakacak:

- Refresh token **hiçbir yerde** tutulmaz (localStorage, sessionStorage, store, bellek). Tarayıcı cookie'yi kendisi gönderir.
- Access token **sadece bellekte** tutulur (kalıcı depolamaya yazılmaz). Sayfa yenilenince açılışta sessiz refresh ile yeniden alınır.
- Birden çok sekme aynı anda refresh yapınca oturum düşmez.

Bu sayede bir XSS açığı olsa bile 7 günlük refresh token çalınamaz.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı baştan sona oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle **"Refresh token cookie'si"** bölümü. Özet:

- Login, register ve refresh cevapları `Set-Cookie: satmis_refresh=...; HttpOnly; SameSite=Strict; Path=/api/auth` döner.
- `POST /api/auth/refresh` ve `POST /api/auth/logout` **gövdesiz** çağrılabilir; token cookie'den okunur.
- `401` + `code: "REFRESH_TOKEN_ROTATED"`: başka sekme token'ı az önce yeniledi, oturum geçerli. Refresh **bir kez** tekrarlanır.
- Kodsuz `401`: oturum bitti. `403` "İstek kaynağı doğrulanamadı": Origin sorunu (normalde olmaz, raporla).
- Gövdedeki `refreshToken` alanı geçiş için hâlâ geliyor ama **deprecated**: frontend artık onu okumamalı. Bu görev bitince backend alanı kaldıracak.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut davranış (401→refresh→tekrar, 429'da oturumu silmeme, şifre sıfırlama sonrası temizleme) korunmalı; mevcut testler geçmeye devam etmeli (sadece refresh token saklamayı varsayan testler yeni davranışa göre güncellenir).
- Yeni bağımlılık ekleme.
- Token'ları loglama.

## Kapsam dışı

- Backend'de değişiklik, access token'ı cookie'ye taşımak, "beni hatırla" seçeneği.

---

## Adım 0: Ön kontrol

- `git status` temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et.
- Refresh token ve access token'ın şu an nerede saklandığını bul (store, `persist` middleware, localStorage anahtarları) ve raporda yaz.

## Adım 1: Token saklama

- Refresh token'ı tutan her şeyi kaldır: store alanı, `persist` ile kalıcılaştırma, localStorage/sessionStorage anahtarları, tip alanları.
- Access token sadece bellekte (store'da `persist` olmadan veya modül değişkeninde). Kullanıcı bilgisi de kalıcı depolamada tutulmasın; açılışta refresh cevabındaki `user` ile doldurulur.
- **Eski veriyi temizle:** uygulama açılışında önceki sürümün localStorage/sessionStorage'a yazdığı token anahtarlarını bir kez sil (eski kullanıcıların tarayıcısında token kalmasın).
- `AuthResponse` tipinde `refreshToken` alanını artık okuma. Alanı tipte `@deprecated` olarak bırakabilir ya da tipten kaldırabilirsin (backend kaldırınca sorun çıkmasın diye kodda kullanılmasın).

## Adım 2: HTTP katmanı

- `POST /api/auth/refresh` ve `POST /api/auth/logout` **gövdesiz** çağrılır.
- Geliştirmede istekler Vite proxy üzerinden aynı origin'den gittiği için cookie otomatik gider. Yine de auth isteklerinde `withCredentials: true` ayarla (ileride API başka origin'e taşınırsa çalışsın). Diğer isteklerin davranışı değişmesin.
- **Tek uçuşta refresh (single-flight):** aynı anda biten birden çok istek tek bir refresh isteği beklesin (mevcut kodda varsa koru).
- **Sekmeler arası:** refresh çağrısını `navigator.locks.request('satmis-refresh', ...)` ile kilitle (destekleniyorsa; yoksa kilitsiz devam). Böylece aynı tarayıcıdaki sekmeler refresh'i sırayla yapar.
- **`REFRESH_TOKEN_ROTATED`:** refresh `401` + bu kodla dönerse refresh'i **bir kez** tekrarla. Yine başarısızsa oturumu bitir. Sonsuz döngü olmasın.
- Kodsuz `401` (refresh'te) → yerel oturumu temizle, girişe yönlendir (mevcut davranış). `429` → oturumu silme (mevcut davranış). `403` → oturumu temizle ve hatayı raporlanabilir şekilde konsola değil kullanıcıya genel bir mesajla göster.
- Hata kodu `REFRESH_TOKEN_ROTATED` mevcut hata kodu sabitlerine/union tipine eklenir.

## Adım 3: Açılışta sessiz refresh

- Uygulama açılırken bir kez `POST /api/auth/refresh` çağrılır:
  - `200` → access token belleğe, `user` store'a; kullanıcı girişli.
  - `401` (kodsuz) → kullanıcı misafir. Hata göstermez.
  - `REFRESH_TOKEN_ROTATED` → bir kez tekrar.
  - Ağ hatası/`5xx` → misafir say, uygulama açılsın (sonsuz yükleme yok).
- Bu istek bitene kadar korumalı rotalar ve "girişli mi" kararı veren bileşenler kısa bir yükleme durumu gösterir (giriş sayfasına yanlış yönlendirme olmasın). Herkese açık sayfalar beklemeden açılabilir.
- React StrictMode'da istek iki kez atılmasın.

## Adım 4: Giriş, kayıt, çıkış, şifre işlemleri

- Login/register sonrası sadece `accessToken` ve `user` kullanılır; cookie'yi backend yazdı.
- Logout: gövdesiz `POST /api/auth/logout`, sonra yerel oturumu temizle. İstek hata verse de yerel oturum temizlenir.
- Şifre sıfırlama ve şifre değiştirme sonrası mevcut temizleme davranışı korunur (backend refresh token'ları zaten iptal ediyor).
- Bir sekmede çıkış yapılınca diğer sekmeler bir sonraki istekte 401 alıp girişe düşer; ekstra senkronizasyon gerekmez (istersen `BroadcastChannel` ile anında çıkış eklenebilir, opsiyonel).

## Adım 5: Testler

Vitest + React Testing Library + MSW. Kullanıcı davranışını test et. En az:

1. Login sonrası localStorage/sessionStorage'da token **yok**; access token ile korumalı istek atılabiliyor.
2. Açılışta refresh `200` → kullanıcı girişli görünür; `401` → misafir, hata yok; korumalı rota yükleme sırasında girişe yönlendirmez.
3. Refresh ve logout istekleri gövdesiz gider.
4. Access token süresi dolunca (401) refresh yapılır ve istek tekrarlanır; eşzamanlı iki 401 tek refresh isteği üretir.
5. Refresh `401 REFRESH_TOKEN_ROTATED` → bir kez tekrar edilir ve başarılı olur; ikinci kez de aynı kod gelirse oturum biter (sonsuz döngü yok).
6. Refresh kodsuz `401` → oturum temizlenir; `429` → oturum korunur.
7. Eski sürümden kalan localStorage token anahtarları açılışta silinir.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 6: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa `npm run dev` ile tarayıcıda dene ve DevTools'ta doğrula:

1. Giriş yap → Application > Cookies'te `satmis_refresh` var, **HttpOnly** işaretli, Path `/api/auth`. Local/Session Storage'da token yok. Konsolda `document.cookie` içinde `satmis_refresh` **görünmüyor**.
2. Sayfayı yenile → girişli kalıyorsun (Network'te açılışta gövdesiz `refresh` isteği, `200`).
3. İki sekme aç, ikisini aynı anda yenile → iki sekme de girişli kalıyor (biri `REFRESH_TOKEN_ROTATED` alıp tekrar denemiş olabilir; ikisi de çıkışa düşmemeli).
4. Çıkış yap → cookie silindi; yenileyince misafirsin.
5. Şifremi unuttum ile şifre değiştir → diğer sekme bir sonraki istekte girişe düşüyor.

Backend çalışmıyorsa bu adımı atla ve raporda belirt; backend'i kendin başlatmaya çalışma.

## Adım 7: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: kodda kalan `refreshToken` okuma/yazma, kalıcı depolamaya yazılan token, `any`, token loglama, kullanılmayan kod. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Token'lar önceden nerede tutuluyordu, şimdi nerede
   - Eklenen/değişen dosyalar
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - **Frontend'in gövdedeki `refreshToken` alanına artık hiç bağlı olmadığını** açıkça doğrula (backend bu alanı kaldıracak)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
