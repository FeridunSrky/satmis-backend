# satmis.com Frontend Görevi 3: E-posta Doğrulama, Şifre Sıfırlama ve İstek Sınırı

Proje kökündeki `CLAUDE.md` dosyasını oku ve oradaki kurallara uy. Mevcut kodu (özellikle `src/shared/api/http.ts`, `src/shared/api/apiError.ts`, `src/shared/lib/formServerErrors.ts`, `src/features/auth`, `src/features/user`, `src/features/listings`, `src/app/routes.tsx`) oku ve aynı kalıpları kullan.

Bu belgedeki adımları **sırayla** uygula. Her adımdan sonra `npm run lint` ve `npm run build` çalıştır, hata varsa düzelt, sonra devam et. Bana soru sormadan ilerle. Karar vermen gerekirse bu belgeye en yakın seçeneği seç ve sonda raporla.

## Hedef

Backend'e eklenen üç özelliği frontend'e bağla:

1. **E-posta doğrulama**: kayıttan sonra e-postaya link gider. Doğrulanmamış kullanıcı giriş yapabilir ama ilan veremez.
2. **Şifre sıfırlama**: "Şifremi unuttum" → e-postaya link → yeni şifre belirleme.
3. **İstek sınırı (429)**: bazı auth uçları IP başına sınırlı; aşılınca anlaşılır mesaj göster, oturumu düşürme.

## API sözleşmesi (tek doğruluk kaynağı)

**Önce şu dosyayı baştan sona oku:**
`C:\Users\Feridun\Desktop\STSProjects\satmis-backend\docs\frontend-api.md`

Özellikle şu bölümler: "Genel kurallar" (hata gövdesi, `code` alanı), "E-posta doğrulama", "Şifre sıfırlama", "İstek sınırı (429)", "İlan hata durumları". Bu belge onları tekrar etmez, sadece frontend tarafında ne yapılacağını anlatır.

- Backend çalışıyorsa OpenAPI şeması `http://localhost:8080/v3/api-docs` adresindedir; alan adlarını oradan doğrulayabilirsin.
- `UserResponse` tipine `emailVerified: boolean` alanı eklendi. Hata gövdesindeki `code` artık `PHONE_REQUIRED` veya `EMAIL_NOT_VERIFIED` olabilir.

## Çalışma sınırları (kesin)

- Sadece bu frontend projesinin klasörüne yaz. Backend klasörüne (`satmis-backend`) **hiçbir şey yazma**, sadece yukarıdaki dokümanı oku.
- Backend sözleşmeden farklı davranırsa backend'e uyacak hack yazma. Farkı raporla.
- `git commit` ve `git push` yapma.
- Var olan dosyaları sormadan silme. Mevcut auth/profil/ilan davranışını bozma; mevcut testler geçmeye devam etmeli.
- Yeni bağımlılık ekleme; bu iş mevcut bağımlılıklarla (TanStack Query, React Hook Form, Zod, Zustand, axios, react-router) yapılabilir.
- Token'ları ve e-postadaki `token` parametresini **loglama**, analytics'e veya hata mesajına yazma.

## Kapsam dışı (bunları YAPMA)

- SMS/telefon doğrulama, e-posta adresi değiştirme, admin ekranları.
- Refresh token'ı cookie'ye taşıma (backend'de henüz yok; ayrı bir görev olacak).

---

## Adım 0: Ön kontrol

- `git status` çalıştır. Proje git deposu değilse veya çalışma ağacı temiz değilse **dur ve bana bildir**.
- `npm run lint`, `npm run build`, `npm test` çalıştır; başlangıç durumunu not et. Zaten kırık bir şey varsa raporla.

## Adım 1: Rotalar

E-postadaki linkler backend tarafından üretilir ve yolları **sabittir**: `/verify-email?token=...` ve `/reset-password?token=...`. Bu iki rota diğer Türkçe rotalardan farklı olarak bu adlarla eklenir.

| Frontend rotası | Erişim | Sayfa | Kullandığı backend uçları |
|---|---|---|---|
| `/verify-email` | herkes (girişli olması gerekmez) | E-posta doğrulama sonucu | `POST /api/auth/verify-email` |
| `/sifremi-unuttum` | sadece girişsiz | E-posta formu | `POST /api/auth/forgot-password` |
| `/reset-password` | herkes | Yeni şifre formu | `POST /api/auth/reset-password` |

- Sayfalar mevcut `routes.tsx` düzeninde `lazy` ile eklenir.
- `token` URL parametresi Zod ile doğrulanır (boş olmayan string). Yoksa veya boşsa istek atmadan "Link geçersiz" durumu gösterilir.
- Sayfa token'ı okuduktan sonra URL'den temizlesin (`replace` ile `?token=` olmadan aynı rotaya), böylece token tarayıcı geçmişinde ve ekran paylaşımında kalmaz. Token bileşen state'inde tutulur.
- Giriş sayfasına "Şifremi unuttum" linki ekle (`/sifremi-unuttum`).

## Adım 2: API katmanı

Mevcut düzende (`src/features/auth/api`, `hooks`, `schemas`):

- `verifyEmail(token)`, `resendVerificationEmail()` (`POST /api/users/me/email-verification`, girişli), `forgotPassword(email)`, `resetPassword(token, newPassword)`.
- Tüm çağrılar `@/shared/api/http` üzerinden.
- `UserResponse` tipine `emailVerified` ekle; tipin kullanıldığı mock'ları/fixture'ları güncelle.
- Hata kodları (`PHONE_REQUIRED`, `EMAIL_NOT_VERIFIED`) tek bir yerde sabit/union tip olarak tanımlansın; bileşenler string'i elle yazmasın.

## Adım 3: 429 ve refresh interceptor

- `http.ts`'deki 401→refresh mantığı `429`'u oturum düşmesi gibi ele **almamalı**: refresh isteği 429 dönerse kullanıcıyı çıkışa atma, oturum verisini silme; hatayı çağırana ilet.
- `toApiError` (veya eşdeğeri) `Retry-After` header'ını (saniye) okuyup `ApiError`'a `retryAfterSeconds?: number` olarak koysun. Header'ı istemcinin okuyabilmesi için backend CORS'ta açıktır.
- 429'da backend'in `message`'ı gösterilir. Formlarda gönder butonu `retryAfterSeconds` boyunca devre dışı kalabilir (geri sayım opsiyonel, basit tut).
- Retry otomatik yapılmaz (TanStack Query mutation'larında retry kapalı kalsın; query'lerde 429 için retry yapma).

## Adım 4: E-posta doğrulama ekranları

### Doğrulama bandı
- Girişli kullanıcıda `emailVerified === false` ise header'ın altında (tüm sayfalarda) bir uyarı bandı: "E-posta adresini doğrula. Doğrulama linkini {email} adresine gönderdik." + **"Tekrar gönder"** butonu.
- "Tekrar gönder" → `204` ise "Yeni doğrulama linki gönderildi" toast'ı; `409` ise (zaten doğrulanmış) kullanıcıyı yenile ve bandı kaldır; `429` ise backend mesajı.
- Bant `role="status"` veya uygun bir landmark ile erişilebilir olsun; buton gerçek `button`.

### `/verify-email`
- Açılınca token ile bir kez `POST /api/auth/verify-email` çağrılır (React StrictMode'da iki kez çalışmasın: mutation'ı effect'te koruyarak veya tek seferlik bir mekanizmayla çağır).
- Durumlar: yükleniyor; başarılı → "E-posta adresin doğrulandı" + girişliyse "İlan ver", değilse "Giriş yap" butonu; `400` → backend mesajı + girişliyse "Yeni link gönder" butonu, değilse "Giriş yapıp yeni link iste" metni.
- Başarıda kullanıcı girişliyse `GET /api/users/me` query'sini invalidate et (bant kaybolsun).

### İlan verme
- `POST /api/listings` `422` + `code: "EMAIL_NOT_VERIFIED"` dönerse form üstünde "İlan vermek için e-posta adresini doğrula" uyarısı + "Tekrar gönder" butonu (bandla aynı mutation).
- `/ilan-ver` sayfası açılırken kullanıcı zaten doğrulanmamışsa form yerine bu uyarıyı baştan göstermek de kabul (hangisini seçtiğini raporla). Mevcut `PHONE_REQUIRED` davranışı korunur; backend önce e-postayı kontrol eder.

## Adım 5: Şifre sıfırlama ekranları

### `/sifremi-unuttum`
- React Hook Form + Zod: e-posta zorunlu ve geçerli biçimde.
- `204` → form yerine **tarafsız** mesaj: "Bu e-posta adresi kayıtlıysa şifre sıfırlama linki gönderdik. Link 1 saat geçerlidir." Kayıtlı olup olmadığını belli eden hiçbir metin/davranış olmasın (backend her durumda `204` döner).
- `400` → `applyServerErrors`; `429` → backend mesajı.
- Girişli kullanıcı bu sayfaya gelirse `/profil`'e yönlendir (veya mevcut girişsiz-rota kalıbı neyse onu kullan).

### `/reset-password`
- Form: yeni şifre + yeni şifre tekrar. Zod: 8–64 karakter (backend kuralı), iki alan eşleşmeli (tekrar alanı sadece frontend'de, backend'e gönderilmez). Mevcut kayıt/şifre değiştirme formunda şifre göster/gizle veya güç göstergesi varsa aynısını kullan.
- `204` → mevcut oturum varsa **yerel oturumu temizle** (token'lar, kullanıcı cache'i; backend tüm refresh token'ları iptal etti), `/giris`'e yönlendir ve "Şifren değişti, yeni şifrenle giriş yap" toast'ı göster.
- `400` + `errors.newPassword` → alana yaz (link hâlâ geçerli, kullanıcı tekrar deneyebilir).
- `400` (`errors` yok) → link geçersiz/kullanılmış/süresi dolmuş: form yerine backend mesajı + "Yeni link iste" butonu (`/sifremi-unuttum`).
- `429` → backend mesajı.

## Adım 6: Testler

Vitest + React Testing Library + MSW (mevcut `src/test/server.ts` ve `apiUrl` yardımcıları). Kullanıcı davranışını test et. En az:

1. `/verify-email`: başarılı token → başarı mesajı ve istek **tek kez** atılır; `400` → hata mesajı; token yok → istek atılmaz, "Link geçersiz".
2. Doğrulama bandı: `emailVerified: false` kullanıcıda görünür, `true`'da görünmez; "Tekrar gönder" `204`'te toast, `409`'da bant kaybolur.
3. İlan verme: `422 EMAIL_NOT_VERIFIED` → doğrulama uyarısı görünür; `PHONE_REQUIRED` davranışı bozulmamış.
4. `/sifremi-unuttum`: geçersiz e-postada Türkçe hata; `204`'te tarafsız mesaj.
5. `/reset-password`: şifreler eşleşmezse hata; kısa şifrede hata; `204`'te `/giris`'e gidilir ve oturum temizlenir; `400` + `errors.newPassword` alanda görünür; `400` (errors yok) → "Yeni link iste".
6. 429: refresh isteği 429 dönerse kullanıcı çıkışa atılmaz; `Retry-After` `ApiError`'a okunur; login formunda 429 mesajı görünür.

Testi geçirmek için testi zayıflatma, hata kodda ise kodu düzelt.

## Adım 7: Uçtan uca kontrol (backend çalışıyorsa)

Backend `http://localhost:8080`'de çalışıyorsa (`/v3/api-docs` 200 dönüyorsa) ve Mailpit `http://localhost:8025`'te açıksa, `npm run dev` ile elle dene. E-postaları Mailpit arayüzünde okursun:

1. Kayıt ol → bant görünür → `/ilan-ver`'de doğrulama uyarısı → Mailpit'teki linki aç → "doğrulandı" → bant kaybolur → ilan verilebilir.
2. "Tekrar gönder" → Mailpit'te yeni mail; eski link artık `400`.
3. Çıkış → "Şifremi unuttum" → kayıtlı ve kayıtsız e-postada aynı mesaj → Mailpit'teki linkle yeni şifre → eski şifreyle giriş `401`, yeni şifreyle başarılı. Aynı linki tekrar aç → "Yeni link iste".
4. Login'e hızlıca 11 kez yanlış şifre gönder → 429 mesajı görünür, uygulama çökmez.

Backend veya Mailpit çalışmıyorsa bu adımı atla ve raporda belirt; onları kendin başlatmaya çalışma.

## Adım 8: Son kontrol ve rapor

1. `npm run lint`, `npm run build`, `npm test` çalıştır, sonuçları yaz.
2. Gözden geçir: `any` kullanımı, token/şifre loglama, URL'de kalan token, loading/error durumu eksik istek, erişilebilirlik (label, `aria`, klavye), kullanılmayan kod, 150 satırı geçen bileşenler. Bulduklarını düzelt.
3. Kısa rapor yaz:
   - Eklenen/değişen dosyalar (feature bazında)
   - Rota ↔ backend uç eşleşmesi (Adım 1 tablosu son haliyle)
   - Bu belgeden sapmalar ve nedenleri
   - Backend sözleşmesiyle uyuşmayan veya eksik bulduğun her şey (uç, beklenen, gelen)
   - Uçtan uca kontrol sonucu veya neden yapılamadığı

`git commit` veya `git push` **yapma**.
