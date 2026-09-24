# Widget „Terminal 2.0" — spec dla Claude (implementacja natywna Android)

Spec przenosi webową makietę `src/components/mockups/WidgetMock4x2.tsx` na natywny
widget Android (AppWidgetProvider + RemoteViews). Nie przepisuj logiki od zera —
rozszerz istniejący widget, zachowując jego kontrakt (odświeżanie w tle, cache,
offline, motywy, AQI, sigma-joke).

## Pliki referencyjne

- Makieta web: `src/components/mockups/WidgetMock4x2.tsx`, `WidgetStates.tsx`
- Tokeny web: `src/lib/widget-tokens.ts` (1:1 z kolorami native — trzymaj w syncu)
- Trasa podglądu: `src/routes/mockups.tsx` (`/mockups`)
- Natywny provider: `android/app/src/main/java/dev/pi0trdotsys/homebrewweather/widget/WeatherWidgetProvider.kt`
- Natywny layout: `android/app/src/main/res/layout/weather_widget.xml`
- Kolory: `android/app/src/main/res/values/widget_colors.xml`
- Info widgetu: `android/app/src/main/res/xml/weather_widget_info.xml`
- Ikony: `widget/PixelIcons.kt`, żarty: `widget/SigmaJokes.kt`

## 1. Elementy makupy → @+id (RemoteViews)

| Region w makiecie | Rola | `@+id` | Z czego jest budowane |
|---|---|---|---|
| `┌─ Warszawa ─┐` | lokalizacja (dim, theme) | `widget_header_label` | `setTextViewText`, prefix `"◎ "` gdy isLive |
| `_` (migający kursor) | blink | `widget_cursor` | `BlinkAlarm` przełącza `VISIBLE/GONE` |
| status online/offline | dot | `widget_online_dot` | `widget_dot_online` / `widget_dot_offline` |
| przycisk miasta | konfiguracja | `widget_city_btn` | `PendingIntent.getActivity` |
| przycisk odświeżania | refresh | `widget_refresh_btn` | `PendingIntent.getBroadcast` (`ACTION_REFRESH`) |
| **hero temp `21°C`** (duży VT323) | aktualna temp | **NOWY `widget_hero_temp`** | duży `TextView`, kolor `widget_cyan`, glow = cień |
| `now · partly cloudy · rain 20%` | warunki | `widget_now_line` | istniejące |
| ikona warunków | hero | `widget_now_icon` | `PixelIcons.render(kind, px, frame)` |
| **sparkline POP (4 słupki)** | trend opadów | **NOWY `widget_sparkline`** | `TextView` z blokami `▁▂▃▅▇` (patrz §4) |
| `AQI 42 · good` | meta | `widget_aqi_line` | istniejące |
| etykieta dnia `dziś/śr/cz/pt` | grid | `widget_day{0..3}_label` | theme primary, `dowAbbrev()` |
| ikona dnia | grid | `widget_icon{0..3}` | `PixelIcons.render(...)`, frame animacji |
| temp dzień/noc `24° / 14°` | grid | `widget_temp{0..3}` | `tempSpannable()` (amber/dim) |
| `▽ 20%` | PoP | `widget_pop{0..3}` | `"▽ ${pop}%"` cyan |
| meta (feels/hum/wind) | opcjonalne | `widget_meta_line` | kompakt → `GONE` |
| `// sigma.forecast()` | stopka | `widget_footer_comment` | dim |
| `> sigma komentarz` | stopka | `widget_footer_joke` | amber, `SigmaJokes.pick(...)` |
| **narożne nawiasy HUD** | dekoracja | **NOWE 4×`ImageView`** lub warstwa tła | patrz §4 |
| **siatka HUD** | tło | `widget_root` background | patrz §4 |
| banner stanu (stale/offline/refreshing) | status | **NOWY `widget_status_banner`** | opcjonalny pasek |

> Nie zmieniaj identyfikatorów, które już istnieją — natywny worker, provider i
> testy debugowe się do nich odwołują. Nowe widoki tylko dodawaj.

## 2. Tokeny → widget_colors.xml

Makieta używa `WIDGET_COLORS` z `src/lib/widget-tokens.ts`. Mapowanie na natywne
kolory (istniejące zostają; dopisz tylko brakujące):

| Token mockupu | Hex | Zasób native | Status |
|---|---|---|---|
| `bg` | `#0a0f0a` | `widget_bg` | istnieje |
| `green` | `#33ff66` | `widget_green` | istnieje |
| `greenDim` | `#4a6a4a` | `widget_green_dim` | istnieje |
| `amber` | `#ffb000` | `widget_amber` | istnieje |
| `amberDim` | `#806000` | `widget_amber_dim` | istnieje |
| `cyan` | `#55ffff` | `widget_cyan` | istnieje |
| `cyanDim` | `#2a6666` | `widget_cyan_dim` | istnieje |
| `crimson` | `#ff5555` | `widget_crimson` | istnieje |
| `crimsonDim` | `#703030` | `widget_crimson_dim` | istnieje |
| `online` / `offline` | `#33ff66` / `#ff5555` | `widget_online` / `widget_offline` | istnieje |
| `aqi*` (6 kolorów) | — | `widget_aqi_*` | istnieje |
| `hudCyan` | `#55ffff` | `widget_cyan` | alias (nie dodawaj duplikatu) |
| `hudCyanDim` | `#2a6666` | `widget_cyan_dim` | alias |
| `hudGrid` | `rgba(85,255,255,0.10)` | **NOWY `widget_hud_grid`** | dodaj |
| `hudGlow` | `rgba(85,255,255,0.45)` | **NOWY `widget_hud_glow`** | dodaj (glow/boxShadow → shadowColor/shadowRadius na TextView lub `drawable`) |
| `hudLine` | `rgba(85,255,255,0.35)` | **NOWY `widget_hud_line`** | dodaj |
| `hudScan` | `rgba(85,255,255,0.06)` | **NOWY `widget_hud_scan`** | dodaj |
| `hudMagenta` | `#ff4fd8` | **NOWY `widget_hud_magenta`** | dodaj |

> `withAlpha()` w webie to po prostu `#AARRGGBB` w XML (np. 10% → `0x1A`,
> 45% → `0x73`). Nie używaj `setViewAlpha` na całym widżecie — dotyczy to tylko
> tła (`widget_root` → `widget_background_*`).

## 3. Nowe drawable / kolory do dodania

1. **`widget_hud_grid.xml`** (drawable) — siatka blueprint 16dp: `layer-list` z
   dwoma `BitmapDrawable`/`shape` paskami (pion + poziom) w `widget_hud_grid`,
   opcjonalnie z `radial-gradient` maską (XML ma ograniczone `radial-gradient`;
   jak nie da się zamaskować — po prostu cała siatka, będzie subtelna i tak OK).
2. **`widget_background_hud.xml`** (drawable) — zaokrąglony fill `widget_bg` +
   `stroke` 1dp `widget_hud_line` + wewnętrzny `shape` siatki. Użyj jako bazowego
   tła `widget_root` (zamiast/obok `widget_background_*` — te trzymaj dalej, bo
   odpowiadają za przezroczystość).
3. **Narożne nawiasy** — 4× `ImageView` (po jednym na róg) z drawable `widget_corner_tl/tr/bl/br`
   (kształt „L" 2dp grubości, `widget_hud_line`), albo pojedynczy `layer-list`
   w `widget_background_hud`. Prościej: layer-list w tle (mniej widoków, mniejszy
   koszt RemoteViews).
4. **`widget_status_banner`** (TextView, `visibility=gone` domyślnie) — pasek
   `stale · retrying` / `offline · serving cached snapshot` / `refreshing…`,
   kolor wg stanu (amber/crimson/cyan).
5. **`widget_hero_temp`** (TextView) — duży `textSize` (np. 24sp), `fontFamily`
   „monospace", `textColor=widget_cyan`, `shadowColor=widget_hud_glow`,
   `shadowRadius=6`, `shadowDx/Dy=0`.
6. **`widget_sparkline`** (TextView) — trend POP 4 dni. RemoteViews nie rysuje
   wykresów, więc zamień `popToSparkline()` na Unicode: posortuj wartości,
   przypisz znaki `▁▂▃▄▅▆▇█` proporcjonalnie do `pop / maxPop`, wypisz 4 znaki
   w `widget_sparkline`. Kolor: `widget_amber` gdy któryś dzień ≥ 50%, inaczej
   `widget_cyan`.

## 4. Ograniczenia RemoteViews (minimum, którego nie wolno złamać)

- Tylko widoki z allow-listy (`FrameLayout`, `LinearLayout`, `TextView`,
  `ImageView`, `ImageButton`, `ProgressBar`, `Chronometer`, `AnalogClock`…). **Nie
  ma** zwykłego `View` ani custom view — stąd separator 1dp w layoutcie to `TextView`.
- **Brak CSS/`animate-spin`/`mix-blend-mode`/`mask-image`** — wszystko z webowej
  makety trzeba odwzorować statycznie lub dyskretnie (patrz §5).
- `RemoteViews.setTextColor(id, Int)` — bezpieczne od minSdk 24. `ColorStateList`
  (API 31+) i `setInt(id, "setBackgroundColor", …)` używaj ostrożnie/nie używaj.
- Kolor tła fill: `rv.setInt(widget_root, "setBackgroundResource", drawableRes)` —
  dokładnie tak robi obecny `WidgetTransparency`. Nie dodawaj nowej osi przezroczystości.
- Wszystko w `buildRemoteViews()` to czysty, deterministyczny stan — **żadnych
  ciągłych animacji**. Ticker ~60 s to `BlinkAlarm`/`handleBlinkTick` (już istnieje).
- `setImageViewBitmap` co refresh jest OK, ale pamiętaj o limicie rozmiaru
  RemoteViews (≈ rozsądne, małe bitmapy 16×16 skalowane nearest-neighbor).

## 5. Jak odtworzyć efekty webowe w natywie

| Efekt web | Natywny odpowiednik |
|---|---|
| `.widget-sweep` (przesuwający się błysk) | Nie da się płynnie. Opcje: (a) statyczny diagonalny `gradient` w tle, (b) `widget_sweep` ImageView z `level-list` przełączanym co tick ~60s (imitacja skanu). Wybierz (a) jako bazę, (b) jako smaczek. |
| `animate-spin` na ikonie refresh | `ProgressBar` (indeterminate, mały, cyan) pokazywany tylko w stanie `refreshing`; albo `level-list` na `ImageView`. `ProgressBar` jest prostszy i w allow-liście. |
| `pixel-materialize` / klatki ikon | `PixelIcons.transformGrid(frame)` + `BlinkPrefs.frame()` — już działa. |
| migający kursor | `widget_cursor` + `BlinkAlarm` — już działa. |
| glow tekstu (hero temp) | `shadowColor/shadowRadius` na TextView (android:shadowColor itd.). |
| siatka HUD | `widget_hud_grid.xml` w tle. |

## 6. Checklista implementacyjna

- [x] Dodać nowe kolory do `widget_colors.xml` (`widget_hud_grid`, 10% cyan —
      `hudGlow`/`hudLine` już istniały i zostały ponownie użyte 1:1; `hudScan`/
      `hudMagenta` pominięte — nieużywane natywnie, magenta to tylko debug-guide
      overlay w mockupie webowym).
- [x] Dodać drawable: `widget_hud_grid_tile.png` (16×16 mdpi, tile'owany przez
      `android:tileMode="repeat"`) + warstwa w `widget_background*.xml`
      (layer-list: shape 5dp-radius fill/stroke + bitmap grid), `widget_grid_divider*`
      (1×8dp dashed-tile dla hairline między kolumnami gridu), `widget_refresh_hitbox`
      (bordered 12dp hit-box), `widget_status_banner_bg` (bordered chip).
      4 narożne nawiasy — **pominięte** (nice-to-have per §5; rounded-rect border
      bez brackets, żeby nie ryzykować budżetu wierszy/czasu).
- [x] Layout: `widget_hero_temp`, `widget_sparkline` (zrealokowany z
      `popSparkline()`, który wcześniej trafiał do stopki), `widget_status_banner`
      już istniały z wcześniejszego przejścia; dodano `widget_hero_unit` ("C"),
      `widget_pop_max` ("▽ max%"), `widget_sync_line` ("sync HH:MM:SS"). Sekcja
      hero przeorganizowana (duża temp + sparkline po prawej), stare `@+id`
      zachowane. **Layout rozdzielony na dwa pliki**: `weather_widget.xml`
      (domyślny, prawdziwe 4×2, wiersze o stałej wysokości dp) i
      `weather_widget_compact.xml` (nowy, wrap_content, dla `compactHeight`) —
      RemoteViews nie potrafi zmienić wysokości wiersza w runtime
      (`setViewLayoutHeight` to API 31+), więc jeden plik nie mógł obsłużyć
      obu skrajnych rozmiarów.
- [x] `buildRemoteViews()`: hero temp, sparkline (mapowanie POP→znaki, teraz
      pisany bezpośrednio do `widget_sparkline` zamiast do komentarza stopki),
      status banner (stale/offline/refreshing), glow hero (już istniał).
- [x] Stan `refreshing`: `ProgressBar` (już istniał) nad `widget_refresh_btn`
      w 12dp hit-boxie.
- [x] Stan `offline`: banner „offline · serving cached snapshot" (już istniał).
- [x] Stan `stale`: banner „stale · retrying" (już istniał).
- [x] Stan `no-location`: header `set city` (już istniał).
- [x] 4×2: `weather_widget_info.xml` → `targetCellHeight="2"`, `minHeight="130dp"`
      (urosło z 110dp po realnym bugu na fizycznym urządzeniu — patrz §7).
      Zmierzone i **mieści się**, z realnym zapasem na `font_scale` do ~1.3.
- [x] Zsynchronizować `widget_colors.xml` z `src/lib/widget-tokens.ts` (hex 1:1;
      `widget_hud_grid` dodany, reszta już była zsynchronizowana).
- [x] `gradlew assembleDebug` przechodzi; widget zweryfikowany na realnym
      emulatorze (Pixel_10_Pro) przez `WidgetPreviewDebugActivity` w 3
      wariantach — 250×110dp (default), 180×90dp (minResize), i 250×110dp z
      bannerem ukrytym ("ok" state) — zero przycinania/nakładania się w
      żadnym z nich. `lint` nie był uruchamiany osobno (poza zakresem zadania).
- [x] Sweep animation (`.widget-sweep`) — **pominięta całkowicie** (ani statyczny
      gradient, ani level-list scan) — jawnie dozwolone przez §5 opcję (a)/(b)
      jako "nice to have"; priorytet poszedł w budżet wierszy i real-device fit.
- [x] Radial mask na siatce HUD — **pominięty**, cała siatka jest subtelna i
      nieukryta (dozwolone uproszczenie per §3.1).

## 7. Wymiary

**ROZSTRZYGNIĘTE (2026-08-27): cel to prawdziwe 4×2 = 250×110dp.** `src/lib/widget-tokens.ts`
(`ROWS`, `GRID_ROWS`, `LAYOUT`, `SP_SCALE`, `METRICS`) to jedyne źródło prawdy dla
dp/sp budżetu — są autorskie w dp/sp (native truth), mockup web tylko je skaluje ×2.4 do
podglądu. `fitReport()` w tym pliku dowodzi, że budżet się mieści (98dp użyte / 100dp
dostępne po paddingu, licząc `rule` jako osobną pozycję).

Grid = 4 kolumny × (`label 9 + icon 13 + temp 10 + pop 8` = 40dp). Ikony w gridzie są
**13dp** (nie 32dp) — znacznie gęstsze niż poprzednia implementacja 4×3; hero icon = **22dp**.
`weather_widget_info.xml` → `targetCellHeight="2"`, `minHeight="110dp"`, `minResizeWidth="180dp"`,
`minResizeHeight="90dp"` (poprzednio `3`/`180dp` / `180dp`/`140dp`).

**Zaimplementowana (native) odchyłka od tokenów, po weryfikacji na realnym emulatorze:**
`res/layout/weather_widget.xml` (domyślny 250×110dp) używa
`header 12 · gapA 1 · hero 32 · gapB 2 [1dp rule w środku] · grid 40 · gapC 1 · footer 11`
= **99dp** (token: 98dp) — hero urósł 28→32dp, gapA i gapC oddały po 1dp. Powód: CSS
`line-height: 1` (założenie web mockupu) nie ma prawdziwego odpowiednika w Androidzie —
nawet z `includeFontPadding="false"`, wysokość linii `TextView` to wciąż realny
ascent+descent czcionki, wyraźnie większy niż nominalny rozmiar `sp`. Przy dosłownym
28dp `widget_now_line` (linia warunków pod dużą temperaturą 22sp) była całkowicie
przycinana przez stały wiersz — potwierdzone wizualnie na Pixel_10_Pro (AVD) i
naprawione przez ten +4dp. 99dp mieści się w 100dp dostępnych (1dp zapasu).

Druga poprawka z real-device: `widget_aqi_line` musi mieć `android:maxWidth` (64dp) —
bez niego najdłuższa etykieta kategorii AQI ("unhealthy (sensitive)", patrz
`WidgetPreviewDebugActivity`'s celowo najgorszy-przypadek fake data) rozciąga się na
pełną szerokość `wrap_content` i głoduje lewy (ważony) klaster hero rzędu z
szerokości — Android elidesuje tekst (`ellipsize`) dopiero gdy widok jest faktycznie
zmierzony węziej niż jego treść, więc bez `maxWidth` `ellipsize="end"` nic nie robił i
duża temperatura była twardo obcinana (bez wielokropka) do pojedynczej cyfry.

Ze względu na brak `RemoteViews.setViewLayoutHeight` przed API 31, jeden layout XML nie
może obsłużyć zarówno 250×110dp (ścisły budżet dp) jak i 180×90dp (minResize) — stąd
`buildRemoteViews()` wybiera między `R.layout.weather_widget` (domyślny) i nowym
`R.layout.weather_widget_compact.xml` (wrap_content, prostszy, bez sparklinii/AQI/sync)
na podstawie `compactHeight` (patrz `WeatherWidgetProvider.COMPACT_HEIGHT_THRESHOLD_DP`).

**REALNY BUG (2026-08-27, zgłoszony przez usera na fizycznym POCO F8 Ultra):** powyższe
99dp/1dp-zapasu trzymało się tylko przy `font_scale=1.0` (domyślna skala tekstu
systemu), przy jakiej był weryfikowany poprzedni przebieg na emulatorze. Realne
urządzenie z `font_scale=1.1` (zwykłe ustawienie ułatwień dostępu "większy tekst" —
HyperOS i inne nakładki OEM pozwalają na jeszcze więcej) powiększa realną wysokość
linii (ascent+descent) każdego `TextView` o te ~10%, co wystarczyło by przebić ten
1dp zapasu i nałożyć `widget_now_line` na linię/grid poniżej — dokładnie zdiagnozowane
przez `adb shell settings get system font_scale` na podłączonym urządzeniu.

Naprawa (nie kolejna łatka "dodaj parę dp", tylko realny zapas + zabezpieczenie):
`minHeight` **110dp → 130dp** (`weather_widget_info.xml`), budżet wierszy w
`weather_widget.xml` urósł do `header 14 · gapA 2 · hero 44 · gapB 2 · grid 44
(label 10 + icon 13 + temp 12 + pop 9) · gapC 2 · footer 12` = 120dp na 120dp
dostępnych — zaprojektowany z zapasem do `font_scale` ~1.3. Dodatkowo
`widget_now_line` oraz wszystkie `widget_day{0..3}_label/temp/pop` i
`widget_footer_joke` dostały `android:autoSizeTextType="uniform"` (z realnym,
ograniczonym `layout_height`, nie `wrap_content` — bez tego autoSize nie ma się do
czego kurczyć) jako zabezpieczenie: nawet skala większa niż praktycznie testowana
kurczy tekst zamiast go nakładać. Zweryfikowane na prawdziwym POCO F8 Ultra usera
(nie tylko emulator) — zrzut ekranu po instalacji potwierdza brak nakładania.

Przy okazji naprawiono też `PixelIcons.render()`: piksele siatki ikon były cięte na
ułamkowych granicach (`x * px` bez zaokrąglenia), co przy bardzo małych rozmiarach
(13dp grid icon) dawało niespójne pokrycie komórek — najbardziej widoczne jako
rozmyta "szachownica" na ikonie deszczu/śniegu (drobny wzór kropli). Naprawione przez
zaokrąglanie każdej krawędzi komórki niezależnie (`Math.round`), nie płaski offset
+0.5f na całej siatce.

**REDESIGN "wykorzystaj miejsce" (2026-08-27, ten sam dzień):** po powyższej
poprawce user zwrócił uwagę, że widget ma teraz mnóstwo *pustej* przestrzeni —
120dp budżetu (poprzedni akapit) w 130dp deklarowanym `minHeight`, a realny
launcher (ten sam POCO F8 Ultra) grantował **~185-186dp**, więc ~55-65dp
renderowało się jako pusta siatka HUD pod stopką. Przeprojektowano, żeby
faktycznie wykorzystać tę przestrzeń zamiast tylko się w niej mieścić:

- `ICON_DP` (grid) 13→22dp, `NOW_ICON_DP` (hero) 22→30dp — dużo czytelniejsze
  ikony pikselowe.
- `widget_meta_line` (feels/hum/wind) **przywrócony** — był całkowicie
  `GONE`/`0dp` od poprzedniego "true 4×2" przebiegu (nie ma go w
  `WidgetMock4x2.tsx`), ale skoro jest miejsce, realne dane > pusty margines.
  `metaLine()` w `WeatherWidgetProvider.kt` odtworzony.
- Wiersz gridu zmieniony z twardego `44dp` na **elastyczny** (`0dp` +
  `layout_weight="1"`), z zawartością kolumny wyśrodkowaną
  (`gravity="center_vertical"`) — dowolny *dodatkowy* grant (więcej niż
  naturalne minimum layoutu) centruje siatkę zamiast zostawiać martwą
  przestrzeń u dołu. Zweryfikowane w harnessie przy 250×240dp ("generous",
  więcej niż deklarowany `minHeight`) — treść ładnie się centruje.
- `weather_widget_info.xml` → `minHeight` 130→182dp.

**Dwie kolejne realne usterki znalezione i naprawione w tej samej iteracji**
(obie potwierdzone zrzutami ekranu z prawdziwego, już-umieszczonego widgetu na
POCO F8 Ultra, nie tylko z harnessu):

1. Lewy klaster hero (ikona/temp/warunki) miał tylko `layout_weight="1"` na
   dwóch dzieciach o różnym naturalnym rozmiarze (`wrap_content` po prawej ze
   sparklinią+AQI+sync) — prawy blok "zjadał" większość szerokości, ściskając
   `widget_now_line` do ok. 70dp, gdzie renderował się **całkowicie pusty**
   (potwierdzone przybliżeniem zrzutu — nie mniejszy tekst, brak tekstu).
   Naprawione jawnymi wagami (6:5) na obu blokach hero zamiast "co zostanie".
   Druga iteracja tej samej naprawy: przy wadze 3:2 prawy blok stał się za
   wąski (`▽ 100%` łamał się na 2 linie, `AQI`/`sync` ucinane do 3 znaków) —
   `widget_pop_max`/`widget_sync_line` dostały `singleLine`+`ellipsize`+`maxWidth`
   (wcześniej ich brakowało — tylko `widget_aqi_line` je miał), a sparkline
   zmniejszony 20→13sp, żeby zostawić więcej miejsca kolumnie z tekstem.
2. Nowy, większy budżet (naturalne minimum ~197dp) nie mieścił się w
   **już-umieszczonej** instancji widgetu, która wciąż miała przyznane stare
   ~186dp (Android nie remierzy istniejących widgetów tylko dlatego, że
   aktualizacja aplikacji podniosła deklarowany `minHeight` w manifeście) —
   potwierdzone przez `adb shell run-as dev.pi0trdotsys.homebrewweather cat
   shared_prefs/widget_city_prefs.xml` (`min_height_107=186`). Efekt: wiersz
   PoP (`▽ X%`) w gridzie renderował się jako **całkowicie niewidoczny**.
   Naprawione przez ścięcie budżetu jeszcze raz (do ~177dp natural minimum:
   `header 16 · gapA 4 · hero 52 · gapB 3 · grid 58 [label 11 + icon 22 +
   temp 15 + pop 10] · gapC 4 · meta 13 · footer 13`, padding 7dp), tak żeby
   mieścił się w tym, co realne launchery *już* przyznawały przy starym,
   mniejszym `minHeight`, a nie tylko w nowej deklaracji. `minHeight` →
   182dp, `COMPACT_HEIGHT_THRESHOLD_DP` → 172dp (`DEFAULT_MIN_HEIGHT_DP` w
   `WidgetPrefs.kt` zsynchronizowany na 182).

Lekcja ogólna: przy zmianie `minHeight`/`minWidth` w `weather_widget_info.xml`
zawsze sprawdzić realny, już-przyznany rozmiar istniejącej instancji widgetu
(`adb shell dumpsys appwidget` lub `run-as … cat shared_prefs/widget_city_prefs.xml`)
zamiast zakładać, że nowa deklaracja natychmiast obowiązuje wszędzie — dla
nowych umieszczeń tak, dla istniejących nie, dopóki użytkownik nie usunie i
nie doda widgetu ponownie (lub go nie przeskaluje).

`WidgetPreviewDebugActivity` rozszerzony o 4. kontener (250×240dp, "generous")
testujący zachowanie przy grantcie większym niż deklarowane minimum.

**REALNY BUG #3 — wiersz PoP dalej ucinany (2026-09-18):** mimo poprzedniej
naprawy (177dp natural minimum / 182dp `minHeight`) user zgłosił, że na jego
już-umieszczonej instancji widgetu wiersz `▽ X%` w gridzie wciąż jest
niewidoczny. `adb shell run-as … cat shared_prefs/widget_city_prefs.xml`
potwierdził, że ten konkretny widget ma przyznane tylko **175dp** — mniej niż
poprzednie 177dp natural minimum, więc dalej się nie mieścił. Naprawione
kolejnym, celowanym ścięciem budżetu wierszy w `weather_widget.xml` do
**~159dp natural minimum**: `padding 6dp · header 14 · gapA 3 · hero 48 ·
gapB 2 [1dp rule] · grid 0dp+weight (kolumna: label 10 + icon 20 + temp 14 +
pop 9 = 53dp) · gapC 3 · meta 11+1marginBottom · footer 12`. Przy okazji
naprawiono błąd we własnym skrypcie do masowej edycji XML (Python
`str.replace` z `count=4` — nowa wartość jednego pola trafiła przypadkiem na
starą wartość szukaną przez kolejne `replace`, więc 2 z 4 kolumn gridu
dostały niespójne dp; naprawione precyzyjnymi `re.sub` per-`@+id`).
`weather_widget_info.xml` → `minHeight` 182→165dp, `COMPACT_HEIGHT_THRESHOLD_DP`
172→150 (`WidgetPrefs.kt`'s `DEFAULT_MIN_HEIGHT_DP` zsynchronizowany na 165).
Zweryfikowane zrzutem ekranu z tej samej, realnej instancji widgetu na POCO
F8 Ultra — wszystkie 4 wartości PoP w gridzie widoczne (`▽18% ▽0% ▽68% ▽54%`).

**Martwy endpoint reverse-geocode → `DeviceGeocoder` (2026-09-18):** user
zgłosił, że widget w trybie "użyj mojej lokalizacji" na stałe pokazuje
literalny placeholder `"current location"` zamiast prawdziwej nazwy miasta.
Przyczyna: `WeatherApi.reverseGeocode()` (Open-Meteo `/v1/reverse`) zwraca
twardy HTTP 404 dla *każdej* testowanej współrzędnej (potwierdzone `curl`-em
bezpośrednio, także dla znanych-dobrych współrzędnych Warszawy) — endpoint
jest po prostu martwy, nie brakuje mu danych dla konkretnego miejsca.
Naprawione nowym `widget/DeviceGeocoder.kt`, który używa natywnego,
działającego offline `android.location.Geocoder` (obecny praktycznie na
każdym realnym urządzeniu z Google Play Services) zamiast/przed martwym
Open-Meteo. Podłączony w obu miejscach, gdzie wcześniej był `"current
location"`/martwy fallback: `WidgetConfigureActivity.reverseGeocodeAndSelect()`
(pierwsza konfiguracja "użyj mojej lokalizacji") i
`WeatherWidgetProvider.resolveEffectiveCity()` (każde kolejne odświeżenie w
trybie live). Łańcuch fallbacków: `DeviceGeocoder` → stary
`WeatherApi.reverseGeocode` (zostawiony na wypadek, gdyby kiedyś ożył) →
poprzednia zapamiętana nazwa (przy odświeżeniu) / czytelna etykieta
współrzędnych `51.23°N 17.05°E` (przy pierwszej konfiguracji, żeby nigdy nie
wrócić do bezużytecznego statycznego placeholdera). Przy okazji: user zwrócił
uwagę, że sam placeholder "current location" nie mówi, które miejsce faktycznie
się synchronizuje — stąd decyzja, żeby *zawsze* wyświetlać realną nazwę
miasta (z prefiksem `◎ ` gdy tryb live), nigdy generyczny tekst trybu.
Zweryfikowane na realnym urządzeniu: współrzędne (51.10972897, 17.04785219)
poprawnie rozwiązane do prawdziwej miejscowości "Piekoszów".

**Bardziej chamskie powiadomienia — `RudeNotifications.kt` (2026-09-18):**
na wyraźną prośbę użytkownika ("chamskie, bad-boyowe, złośliwe, mogą być z
przekleństwami i slangiem") wszystkie 5 typów push-powiadomień (deszcz/burza,
wysoka temp, niska temp, duży skok dzień-do-dnia, AQI) w
`WeatherNotifier.kt` przełączone z generycznego angielskiego tekstu na nowy
`widget/RudeNotifications.kt` — pula kilku polskich wariantów na typ (losowany
seedem), utrzymana w tym samym rejestrze co istniejący `SigmaJokes.kt`
(kurwa/chuj-owa rodzina słów, kpina "sigma vs beta"), skierowana w samego
użytkownika/aplikację, nie w realną osobę. Tytuły powiadomień (`"Homebrew
Weather — ${city.name}"`) zostały bez zmian — cały charakter przeniesiony do
treści. Zweryfikowane pojedynczym realnym powiadomieniem wywołanym na
urządzeniu (tymczasowo obniżony próg wysokiej temperatury, potem przywrócony):
`dumpsys notification --noredact` potwierdził jednoczesnie poprawną nazwę
miasta i chamski tekst — `android.title="Homebrew Weather — Piekoszów"`,
`android.text="Piekoszów: 18°C, mózg ci się gotuje jak twoje pomysły na
życie"`.


## 8. Skalowanie adaptacyjne — koniec z dwoma sztywnymi layoutami (2026-09-23)

User: „rozmiar elementów zawsze dostosowany do wielkości widgetu, widget ma
wyglądać znacznie atrakcyjniej, ta sama idea, bez utraty funkcjonalności".

### 8.1 Realna przyczyna: widget nie wiedział, jak jest duży

Cztery opcje rozmiaru `AppWidgetManager` **nie są** parą (min, max) na oś — są
parą portret/pejzaż, rozrzuconą po nazwach, które czyta się na odwrót:

| opcja | znaczenie |
|---|---|
| `OPTION_APPWIDGET_MIN_WIDTH` | szerokość w **portrecie** |
| `OPTION_APPWIDGET_MAX_WIDTH` | szerokość w **pejzażu** |
| `OPTION_APPWIDGET_MIN_HEIGHT` | wysokość w **pejzażu** |
| `OPTION_APPWIDGET_MAX_HEIGHT` | wysokość w **portrecie** |

Czyli realny rozmiar w portrecie to (`MIN_WIDTH`, `MAX_HEIGHT`) — dwie opcje,
których nazwy do siebie nie pasują. Dotychczasowy kod czytał
(`MIN_WIDTH`, `MIN_HEIGHT`). Na urządzeniu usera widget renderujący się
faktycznie jako **368×176dp** był mierzony jako 368×**96**dp (96 to jego
wysokość w pejzażu), więc wpadał poniżej progu `COMPACT_HEIGHT_THRESHOLD_DP` i
rysował okrojony `weather_widget_compact.xml`: tekst 7–10sp, ikony 10dp,
sparkline/AQI/sync/meta całkowicie ukryte — a pod spodem ~70dp pustej siatki
HUD. To nie był problem stylistyczny: widget po prostu nigdy nie znał swojego
rozmiaru. Potwierdzone `run-as … cat shared_prefs/widget_city_prefs.xml`
(`min_width_225=367`, `min_height_225=96`) zestawione ze zmierzonym zrzutem
ekranu (368×176dp).

Naprawione w nowym `widget/WidgetSize.kt`. Rozmiar czytany jest teraz **na
żywo** z `getAppWidgetOptions()` przy każdym renderze (prefs zostały tylko jako
fallback dla harnessu debugowego), więc nie zależy już od tego, czy ten proces
dostał w ogóle callback `onAppWidgetOptionsChanged`.

### 8.2 Jeden layout, rozmiary liczone w runtime

Oba pliki layoutu zniknęły jako para — `weather_widget_compact.xml` **usunięty**,
a `weather_widget.xml` nie deklaruje już **żadnej wysokości wiersza ani żadnego
docelowego `textSize`**. Powód, dla którego ten podział w ogóle istniał
(„RemoteViews nie zmieni wysokości sztywnego wiersza w runtime,
`setViewLayoutHeight` to API 31+") przestaje obowiązywać, gdy żaden wiersz nie
ma sztywnej wysokości: wszystkie są `wrap_content`, więc wysokości wierszy
wynikają z rozmiarów tekstu, które provider ustawia przez
`setTextViewTextSize()` (API 16+) — na każdym API, przy każdym rozmiarze.

Ikony skalują się przez `WidgetGraphics.sized()`: `ImageView` z `wrap_content`
mierzy się do *intrinsic* rozmiaru drawable'a, a `BitmapDrawable` ma intrinsic
= `bitmap.width * targetDensity / bitmap.density` — więc bitmapa otagowana
gęstością docelową mierzy się dokładnie na swoją liczbę pikseli. Generujemy
bitmapę w takim rozmiarze, jaki chcemy, i widok sam się dopasowuje. Zero
`setViewLayoutHeight`.

`widget/WidgetMetrics.kt` to cała drabinka rozmiarów. Projekt referencyjny
(scale 1.0) to kolumna wierszy, z których każdy wnosi wysokość **skalowalną**
(linie tekstu, ikony) i **stałą** (marginesy zapisane w layoutcie);
`scale = FILL * (budget - fixed) / scalable`. Rozdzielenie tych dwóch części
czyni solver dokładnym zamiast przybliżonym: zwinięcie marginesów do jednej sumy
i przeskalowanie całości przestrzeliwuje dokładnie o sumę marginesów — czyli o
te kilka dp, które w tym widgetcie już nieraz po cichu ścinały dolny wiersz.

Szerokość jest **drugim, niezależnym** ograniczeniem: nigdy nie napędza skali
pionowej (szeroki i niski widget nie może rosnąć tekstem aż zacznie się ciąć w
pionie), tylko ogranicza tekst w kolumnach i bramkuje opcjonalne bloki.

### 8.3 Współczynniki linii i szerokości znaków — zmierzone, nie zgadnięte

Wszystkie odczytane z realnych renderów (`uiautomator dump`: wysokość / rozmiar
tekstu, szerokość / liczba znaków), nie założone:

- wysokość linii **nie jest jedną liczbą**: monospace łaciński z
  `includeFontPadding="false"` to ~**1.16×**, ale wiersz zawierający glif z
  fontu zastępczego (`▽` w PoP, `┌─ ─┐` w nagłówku) skacze do ~**1.35×**, bo
  fallback przynosi własne, wyższe metryki. Wcześniejsze płaskie „1.25"
  jednocześnie prze-rezerwowywało większość wierszy i nie-do-rezerwowywało dwa.
- szerokość znaku: łacina **0.62em**, `▽` **0.68em**, bloki `▁▂▃▅▇` **0.78em**.
  Założenie, że bloki mają szerokość łacińską, to dokładnie powód, dla którego
  „▽ 100%" obok sparkline'u ścinało się do „▽ 10…" na szerokim widgetcie.

`FILL = 0.97` zostawia 3% zapasu — fonty zaokrąglają do pełnych pikseli,
`LinearLayout` zaokrągla drugi raz, a nazwa miasta może wciągnąć do nagłówka
fallback z własnymi metrykami. Bycie 1dp ponad nie degraduje się łagodnie: ścina
dolny wiersz na pół.

### 8.4 Tiery — co znika i w jakiej kolejności

Poniżej `MIN_SCALE` (0.70) uczciwszy handel to „pokaż mniej, czytelnie" niż
„pokaż wszystko, nieczytelnie". Kolejność odwrotna do tego, jak często się na to
patrzy: linia meta (feels/hum/wind) → żart sigma → PoP + paski zakresu → linia
warunków w hero → etykiety dni. **Nagłówek nie znika nigdy** — to jedyny wiersz
z kontrolkami (wybór miasta, refresh).

`XXS` istnieje, bo deklarowane `minResizeHeight` (90dp) naprawdę nie mieściło
`XS`: solver dobijał do `MIN_SCALE` i wciąż przekraczał o ~9dp, co na urządzeniu
wyglądało jak temperatury 4 dni ścięte w pół wzdłuż dolnej krawędzi.

### 8.5 Warstwa wizualna (ta sama idea, mocniej postawiona)

- **Narożne nawiasy HUD** — `widget_hud_corners.xml`. Prosił o nie §3 pkt 3 od
  początku i każde poprzednie podejście je pomijało („nice-to-have", 4 dodatkowe
  `ImageView` to realny budżet RemoteViews). Jako drawable kosztują zero:
  `<item android:width/height/gravity>` (API 23+) stawia pasek w rogu bez
  znajomości rozmiaru widgetu, więc to jeden `layer-list`, który tła składają w
  siebie — zero dodatkowych widoków, zero pracy w runtime.
- **Paski zakresu temperatur** w siatce (`WidgetGraphics.rangeBar`) — gdzie
  min..max danego dnia leży w zakresie całych 4 dni. Czytanie czterech par
  „24°/14°" i rankowanie ich w głowie to praca; cztery odcinki na wspólnej osi to
  nie. To jedyna informacja, którą siatka już miała, ale nigdy nie *pokazywała*.
  Oś to kreska 1px, nie wypełniony pasek — wypełniony czytał się jak drugi słupek
  konkurujący z odcinkiem na nim i cały wiersz zamieniał się w nieczytelne paski.
- **Podświetlenie kolumny „dziś"** (`widget_today_col.xml`) — ~6% cyan. Cztery
  kolumny były identyczne, więc ta odpowiadająca na „co jest dzisiaj" nie miała
  większej wagi wizualnej niż ta sprzed trzech dni.
- Sparkline i jego „▽ max%" dzielą teraz jeden wiersz. To jeden odczyt, a jako
  cztery osobne linie kolumna statystyk mierzyła się wyżej niż stos
  temp+warunki obok — wiersz hero przerastał swój budżet i ścinał stopkę.
- `aqiShortLabel()` — dwie z sześciu kategorii US AQI są na tyle długie, że
  „AQI 142 · unhealthy (sensitive)" nie zmieści się w kolumnie statystyk przy
  żadnym realnym rozmiarze, a odczyt AQI z uciętą kategorią to tylko liczba.
  Świadomie osobna funkcja, nie zmiana w `aqiLabelAndColor()` — na tamtych
  dokładnych stringach dopasowuje się `aqiComfortPenalty()` i używa ich
  `WeatherNotifier`, a żadne z nich nie ma problemu z szerokością.

### 8.6 Weryfikacja

`WidgetPreviewDebugActivity` przepisany na **macierz rozmiarów** zamiast czterech
zaszytych kontenerów — skoro rozmiary są ciągłe, sprawdzać trzeba *zakres*, nie
jeden błogosławiony rozmiar. Renderuje 368×176 (zmierzony realny 4×2 na tym
urządzeniu), 250×165 (deklarowane minimum), 368×110, 180×90 (deklarowany
minResize), 170×170 i 300×260.

Sprawdzenie przepełnienia jest **zmierzone, nie ocenione okiem**: `uiautomator
dump` → dla każdego kontenera najniższa krawędź dowolnego `TextView` kontra dolna
krawędź kontenera. Wszystkie sześć rozmiarów: 5–18dp zapasu góra i dół,
symetrycznie (czyli `center_vertical` działa).

Przy okazji: `/mockups` **crashowało na `main`** (`fitReport`, `PREVIEW`, `ROWS`,
`dp` używane w `src/routes/mockups.tsx`, ale nie zaimportowane) — naprawione, bo
to podgląd akurat tej rzeczy, którą ta zmiana rusza.

### 8.7 `src/lib/widget-tokens.ts` nie jest już źródłem prawdy dla rozmiarów

I mówi to teraz wprost. Deklarował `250×110dp` w czasie, gdy
`weather_widget_info.xml` deklarował 250×165, natywny layout zdryfował do
trzeciej liczby, a realny launcher przyznawał 368×176 — ta „jedna prawda" była
fikcją od kilku iteracji. Rozmiary natywne liczy `WidgetMetrics.kt` z realnie
przyznanego footprintu. W tokenach został **projekt referencyjny** (wiersze przy
scale 1.0), z którego drabinka się skaluje — natywny widget w innym rozmiarze to
te proporcje razy współczynnik, nie inny layout. **Kolory zostają ścisłym
kontraktem 1:1 z `widget_colors.xml`.** Web mockup (`WidgetMock4x2.tsx`) nie
dostał pasków zakresu ani narożników — od tej pory to natyw jest z przodu.

## 9. Ikony pixel-art i baza sigma (2026-09-23)

### 9.1 Ikony — przeprojektowane pod rozmiar, w jakim naprawdę się rysują

Siatki renderują się przy 13–20dp (grid) i 26–30dp (hero). Na xxhdpi to ~2.4–3.8
piksela urządzenia na komórkę siatki, więc pojedyncza komórka jest widoczną
kropką, ale drobny naprzemienny detal zamienia się w papkę. Poprzednie siatki
były projektowane „na oko" w edytorze, nie w tym rozmiarze — obejrzane na
realnym urządzeniu przez nowy arkusz ikon (§9.3) pokazały cztery konkretne wady:

| ikona | było | jest |
|---|---|---|
| `sun` | tarcza na prawie całą siatkę + odklejone 1-pikselowe kikuty promieni → czytało się jak ameba | mniejsza tarcza (rzędy 5–10) + osiem wyraźnie **oddzielonych** promieni 2×2; przerwa między tarczą a promieniami jest celowa, stykające się zlewają się w jedną kulę przy 13dp |
| `rain` | jednolita szachownica pojedynczych niebieskich kropek przez 5 rzędów → czytało się jak niebieski szum | dwie przesunięte rangi **pionowych kresek** 2px w dwóch odcieniach niebieskiego → czyta się jak spadające strugi |
| `snow` | gęste plus-kształty w 6 rzędach | rzadkie **pojedyncze kropki** — i to jest właśnie powód, dla którego deszcz dostał kreski: przy identycznej chmurze powyżej i ~50px szerokości to jedyne, co odróżnia te dwie ikony na pierwszy rzut oka |
| `thunder` | chmura `#2a3a2a` na tle widgetu `#0a0f0a` → praktycznie niewidoczna smuga | łupkowy szaro-niebieski `#6b7c84`/`#45545b`, który realnie odcina się od tła, a wciąż czyta się jako „ciemniejsza niż zwykła chmura" |

Poza tym: każda ikona z objętością jest teraz **dwutonowa** (wielka litera = ton
oświetlony, mała = cieniowany: `W`/`w` chmura, `D`/`d` chmura burzowa, `Y`/`o`
słońce, `B`/`b` deszcz, `G`/`g` mgła). Płaskie jednotonowe wypełnienie czyta się
jak ziemniak w tych rozmiarach.

- `moon` — księżyc rysował „ugryzienie" sierpa jako nieprzezroczysty ciemny
  kształt, co na przezroczystym tle widgetu wyglądało jak brudna plama; teraz
  ugryzienie po prostu nie jest rysowane. Doszły dwie cyjanowe gwiazdki.
- `fog` — zamiast identycznych słupków w przygaszonej zieleni: przesunięte
  słupki w dwóch szarościach, które **dryfują w bok** (`FOG_DRIFT`) zamiast
  podskakiwać w pionie jak chmury. Mgła się przesuwa, nie podskakuje.

### 9.2 Piorun przygasa, nie znika

Osobna, realna usterka znaleziona przy okazji: klatka „bez błysku" dla
`thunder` blankowała cały piorun (`blankChar(grid, 'L')`). Ponieważ klatkę
przełącza ~60-sekundowy `BlinkAlarm`, przez **połowę każdego cyklu** ikona burzy
była zwykłą szarą chmurą — nieodróżnialną od `cloud`, na ikonie, której jedynym
zadaniem jest powiedzieć „burza". Teraz piorun jest przekolorowany na przygaszony
bursztyn (`recolor(grid, 'L', 'l')`, `l` = `#7a5300`): kształt zostaje zawsze
widoczny, a błysk dalej działa.

Przy okazji `SUN_RAY_TIPS` przeliczone pod nową tarczę i ograniczone do **czubka**
każdego promienia. Poprzednia lista blankowała tyle, że klatka „retracted"
zwijała słońce w zwykły romb.

### 9.3 Arkusz ikon w harnessie

`WidgetPreviewDebugActivity` renderuje teraz na górze **wszystkie rodzaje ikon ×
oba rozmiary (20/30dp) × wszystkie 4 klatki animacji**. Macierz rozmiarów poniżej
pokazuje tylko te rodzaje, które akurat zawiera fejkowa prognoza, przy tej klatce,
którą akurat zostawił blink tick — więc wcześniej nie było *żadnego* sposobu, żeby
obejrzeć np. ikonę mgły albo zestawić klatkę błysku z klatką pełną. Dokładnie tak
poprzednie siatki weszły na produkcję z chmurą burzową w kolorze tła.

### 9.4 Baza sigma — 111 → 193 linii

Pule mniej więcej podwojone (8 rodzajów × ~24 linie, 193 unikalne). Poprzedni
zestaw opierał się mocno na jednym żarcie — wariacji „pogoda coś robi, a ty nic" —
który pojawiał się w kilkunastu ze ~110 linii, więc na widgecie przesiewanym co
godzinę czytało się to jak ten sam gag w kółko. Przepisane pule trzymają ten sam
głos, ale różnicują formę: tryb rozkazujący, porównania, suche obserwacje i kilka
wręcz zachęcających — żeby pula miała rozpiętość, a nie jedną nutę na różnej
głośności.

Rejestr bez zmian: to aplikacja użytkownika kpiąca z własnego użytkownika, nigdy
z realnej osoby. Twardsze przekleństwa zostają tam, gdzie były — w
`RudeNotifications.kt` (powiadomienia push), nie w stopce, która stoi na ekranie
głównym na widoku.

**Twardy limit 50 znaków** na linię jest teraz egzekwowany, nie tylko opisany:
footer to jedna elipsowana linia, a najwęższa szerokość, przy której widget ją
jeszcze rysuje, mieści ~48 znaków monospace — dłuższe i puenta jest właśnie tym,
co zamienia się w „…".

### 9.5 Testy i synchronizacja

Nowy `app/src/test/.../WidgetContentTest.kt` (5 testów, zwykły JVM JUnit — moduł
dostał `testOptions { unitTests.returnDefaultValues = true }`, bo mapa kolorów
`PixelIcons` woła `android.graphics.Color.parseColor` przy inicjalizacji obiektu):

- każda linia sigma mieści się w limicie footera,
- linie sigma są unikalne we wszystkich pulach,
- każdy `WeatherKind` ma swoją pulę,
- każda ikona to siatka 16×16 wyłącznie ze znanych znaków kolorów,
- deszcz i śnieg trzymają opady **wewnątrz** animowanego pasma (rzędy 9–15) —
  piksel opadu wyżej stałby nieruchomo, podczas gdy wszystko wokół spada.

Obie webowe kopie (`src/components/PixelIcon.tsx`, `src/lib/sigma-jokes.ts`) są
od teraz **generowane ze źródeł Kotlin** i zdiffowane po wygenerowaniu (128
wierszy siatek, 193 linie żartów — zgodne co do bajtu), zamiast przepisywane
ręcznie i rozjeżdżane po cichu.

### 9.6 Weryfikacja na żywo i pusty sparkline

Wszystko powyżej było sprawdzane w harnessie na wymuszonych, celowo
najgorszych danych offline. Widget postawiony z powrotem na realnym ekranie
głównym (Lawnchair, id 233, „Tolox, Spain", przyznane **367×175dp**) domknął to,
czego harness pokazać nie mógł:

- `min_height_233 = 175` — nowy `WidgetSize` poprawnie odczytuje wysokość w
  portrecie. Stary kod zapisałby tu 96 (wysokość w pejzażu) i zjechałby na
  layout compact, dokładnie jak w §8.1.
- Ścieżka **świeżych danych**: hero w jasnym cyanie z glow (nie przygaszony
  `widget_cyan_dim`), zero bannera stanu, prawdziwa nazwa miasta. Harness
  wymusza `debugForceOfflineCache`, więc renderował wyłącznie stan „stale".
- Przycisk refresh działa end-to-end (broadcast `ACTION_REFRESH` → fetch →
  `sync` przeskoczyło 10:44:00 → 10:44:19, temperatura 24° → 25°).

**Usterka widoczna tylko na realnych danych:** prognoza bez opadów mapuje każdy
słupek sparkline'u na najkrótszy blok, więc „wykres" to cztery znaki `▁` obok
siebie — co czyta się jak przypadkowa pozioma kreska wisząca nad linią AQI, nie
jak płaski trend. Dane testowe harnessu miały PoP 12/88/100/45, więc ten
przypadek nigdy się w nim nie pojawił; w suchym klimacie to stan domyślny.
Naprawione: przy `maxPop == 0` sparkline jest ukrywany — nie ma czego rysować, a
stojące obok „▽ 0%" i tak mówi, że nie popada.

## 10. Mniej informacji naraz — widget, dashboard, powiadomienia (2026-09-24)

User: „przy obecnej wielkości widgetu mam wrażenie, że jest za dużo informacji na
raz" — plus prośba o propozycje dla całej aplikacji. Zrobione wszystkie sześć.

### 10.1 Widget: pokazuj wyjątki, nie stan (`WidgetContent.kt`, `WidgetDensity.kt`)

Przy 368×176dp widget pokazywał ~30 odczytów, część wielokrotnie: szansę opadów
**3×** (bieżąca godzina, sparkline + max, pod każdym dniem), min/max dnia 2×
(tekst i pasek zakresu), świeżość 2× (zawsze zielona kropka i zegar z sekundami).
W zwykły suchy dzień prawie wszystko to mówiło głośno „nic się nie dzieje".

Gęstość per widget (w konfiguracji, obok motywu i przezroczystości):

| | standard (domyślnie) | full | minimal |
|---|---|---|---|
| opady per dzień | tylko dni ≥30% | zawsze | nigdy |
| AQI | od 51 (moderate) | zawsze | od 101 |
| odczuwalna / wiatr / wilgotność | tylko \|Δ\|≥3° / ≥30 km/h / ≥90% lub ≤20% | zawsze | nigdy |
| kropka online, zegar sync | kropka tylko offline | zawsze | kropka tylko offline |
| sparkline, paski zakresu | nie | tak | nie |
| stopka | tak | tak | nie |

**Solver budżetuje tylko wiersze, które faktycznie będą narysowane**
(`WidgetMetrics.Rows`). Bez tego ukrycie treści zostawiłoby dziurę — dokładnie ten
problem, od którego zaczęła się cała ta historia (§7/§8). Zmierzone na tym samym
368×176dp: temperatura hero 24,8sp (full) → **31,6sp** (standard, zwykły dzień) →
35,6sp (minimal); ikony dni 18 → 23 → 25dp.

Paski zakresu, dodane w §8.5, w standardzie znikają — na realnych danych z Tolox
(30–33° / 18–21°) wyszły jako cztery identyczne kreski. Zostały w `full`.

### 10.2 „Kiedy popada" (`RainWindow.kt` + `src/lib/rain-window.ts`)

Godzina jest „mokra" przy PoP ≥50% (zdanie z godziną startu to prognoza, więc
„bardziej prawdopodobne niż nie") albo — tylko bieżąca — gdy właśnie pada. Okno =
pierwszy ciąg mokrych godzin startujący w ciągu 12h, koniec = pierwsza sucha.
Rodzaj = najpoważniejszy w oknie (burza > śnieg > deszcz). Widget: „deszcz
15:00-19:00" w linii hero; dashboard i powiadomienia: pełne zdanie. Reguła
identyczna w Kotlinie i TS; tekst po polsku w natywie, po angielsku w webie (bo
cała aplikacja webowa jest po angielsku). `WeatherApi` parsuje teraz 24 godziny
prognozy (wcześniej tylko dwie liczby z nich), cache je przechowuje.

### 10.3 Tap w widget → aplikacja na mieście widgetu

Wcześniej intencja nie niosła miasta, a `MainActivity` go nie czytała — tap w
widget z Tolox otwierał aplikację na jej własnej lokalizacji (u usera: współrzędne
Málagi z nazwą `unknown_location`). Teraz widget przekazuje miasto w extras,
`MainActivity` zapisuje je do `brew-wx:coords` przed startem WebView (zimny start),
a przy działającej aplikacji dodatkowo wysyła zdarzenie `hbw:open-city` do strony.
Powiadomienia otwierają aplikację tak samo. Zweryfikowane na urządzeniu w obu
ścieżkach.

### 10.4 Dashboard

`NowPanel` (tabela `/proc/weather`) i terminal obok pokazywały te same odczyty;
najlepsze miejsce zajmował debugowy panel `cron -l`; nie było dużej liczby — aplikacja
była mniej czytelna niż jej własny widget. Teraz: `HeroPanel` (duża temperatura +
zdanie o deszczu + odczuwalna tylko gdy się różni), `StatusLine` (jedna cicha linia,
bursztynowa tylko przy offline/stale), terminal jako pełny odczyt szczegółów
(z ciśnieniem, które było tylko w usuniętej tabeli), jeden żart zamiast tickera +
żartu. Usunięte: `NowPanel.tsx`, `JokeTicker.tsx`. Przy okazji: komunikat o odmowie
geolokalizacji znika po ręcznym wyborze miasta.

### 10.5 Powiadomienia: poranny brief + „deszcz za chwilę"

`MorningBrief.kt`: jedno powiadomienie rano (domyślnie od 7:00, okno 3h — albo
szersze, jeśli interwał odświeżania jest dłuższy, żeby 6-godzinny interwał go nie
przeskoczył). Progi z ustawień decydują, co brief wspomina. Deszcz przestał być
alertem „już pada" (wtedy już nie trzeba mówić) — teraz ostrzega, gdy okno startuje
w bieżącej lub następnej godzinie. Wyzwalany na zboczu, nie per okno: okno „trwa od
teraz" dostaje co godzinę nowy start, więc klucz po oknie wysyłałby powiadomienie
co godzinę przez cały deszcz. Brief wyłączony = osobne alerty jak wcześniej.

### 10.6 Ton aplikacji (`Tone.kt`, `settings:tone`)

`clean` / `sigma` / `rude` (domyślnie `rude` = zachowanie sprzed zmiany). Steruje
stopką widgetu (`CleanJokes.kt` — nowa pula, 94 linie ≤50 znaków), powiadomieniami
(`PlainNotifications.kt` vs `RudeNotifications.kt`; brief i ostrzeżenie o deszczu są
faktograficzne w każdym tonie, `rude` dopisuje docinkę pod faktem) i żartem na
dashboardzie — `sigma-jokes.ts` wreszcie jest w aplikacji webowej używany.

### 10.7 Drobne

- Wersja z jednego źródła: `package.json` → `src/lib/version.ts` (dashboard) i
  `versionName` w `build.gradle`. Wcześniej nawigacja pokazywała na sztywno `v1.0.0`.
- `./mockups` linkowane tylko w dev (`import.meta.env.DEV`); w bundlu prod 0 wystąpień.
- Build debug ma `applicationIdSuffix ".debug"` i instaluje się **obok** release'u —
  harness działa bez ruszania widgetu z ekranu głównego.

### 10.8 Weryfikacja

37 testów JVM (nowe: `RainWindowTest` 12, `WidgetContentRulesTest` 10,
`MorningBriefTest` 8 + długość/unikalność puli clean). Harness: 11 kombinacji
rozmiar × gęstość × dane (spokojny / wydarzeniowy dzień), przepełnienie mierzone
przez `uiautomator` — wszystkie mieszczą się, 5–20dp zapasu, symetrycznie.
Dashboard i ustawienia sprawdzone w przeglądarce (zero błędów konsoli).
**Nie** sprawdzone na żywo: faktyczne dostarczenie briefu i ostrzeżenia o deszczu
(aplikacja debug nie ma uprawnienia do powiadomień; logika pokryta testami).

**Znane, poza zakresem:** webowe `reverseGeocode` wciąż woła martwy endpoint
Open-Meteo `/v1/reverse` (natyw ma już `DeviceGeocoder`, §7), stąd
`unknown_location` przy lokalizacji z GPS w aplikacji.
