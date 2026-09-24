package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Every size the widget draws, derived from the footprint the host actually
 * granted this instance — instead of the hard-coded dp/sp ladders that used to
 * live in res/layout/weather_widget.xml and its `_compact` twin.
 *
 * ## Why this replaced the two-fixed-layouts approach
 *
 * The old design had exactly two looks: a "true 4x2" layout whose row heights
 * were hard-coded dp tuned for 250x165dp, and a stripped `weather_widget_compact.xml`
 * for anything shorter, picked by a single height threshold. That made the widget
 * correct at exactly two sizes and wrong everywhere else — and it was wrong on the
 * reporting user's own device in a way nobody had noticed, because the size it
 * branched on was itself wrong (see [WidgetSize]). A 368x176dp widget rendered the
 * *compact* layout: 7-10sp text and 10dp icons floating in a 368dp-wide frame, with
 * the sparkline/AQI/sync/meta rows dropped for space that was in fact sitting empty
 * below the grid.
 *
 * So sizes are computed here instead, continuously, from the real footprint, and
 * pushed onto a single layout at render time via `setTextViewTextSize` (API 16+) and
 * exactly-sized icon bitmaps. res/layout/weather_widget.xml now declares *no* row
 * heights and *no* final text sizes at all — only structure.
 *
 * ## The model
 *
 * A reference design (scale 1.0) is laid out as a column of rows, each contributing
 * a scalable height (text lines, icons) and a fixed height (the margins written in
 * the layout). [forSize] picks the richest [Tier] whose rows still fit the granted
 * height at a legible minimum scale, then solves for the scale that makes that
 * tier's rows exactly fill the height. So the widget doesn't merely *fit* its
 * footprint — it consumes it, at whatever size that implies, which is what
 * "elements always sized to the widget" has to mean for a host that can grant
 * anything from 180x90dp to a full-screen-width tile.
 *
 * Width is a second, independent constraint: it never drives the vertical scale
 * (a wide, short widget must not grow text until it clips vertically), it only
 * caps per-column text and gates the optional side/stat columns. See [colWidthDp].
 */
data class WidgetMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val tier: Tier,
    val scale: Float,

    val padDp: Int,

    // header row
    val headerSp: Float,
    val dotDp: Int,
    val refreshDp: Int,

    // hero row
    val heroIconDp: Int,
    val heroTempSp: Float,
    val heroUnitSp: Float,
    val nowLineSp: Float,
    val statSp: Float,
    val sparkSp: Float,

    // 4-day grid
    val dayLabelSp: Float,
    val dayIconDp: Int,
    val dayTempSp: Float,
    val dayPopSp: Float,
    val barHeightDp: Int,

    // footer / meta / banner
    val metaSp: Float,
    val footerSp: Float,
    val bannerSp: Float,

    // what this tier actually shows
    val showMeta: Boolean,
    val showFooter: Boolean,
    val showPop: Boolean,
    val showBars: Boolean,
    val showStats: Boolean,
    val showAqi: Boolean,
    val showSync: Boolean,
    val showTempPair: Boolean,
    val showDayLabel: Boolean,
    val showNowLine: Boolean,
) {

    /**
     * Width of one of the four day columns, in dp — the grid's inner width minus
     * the three 1dp dividers, split four ways. Used to cap [dayTempSp] so
     * "30°/16°" can never be measured wider than the column that has to hold it
     * (the failure mode a previous pass hit head-on: `ellipsize="end"` does
     * nothing until a view is actually measured narrower than its content, so an
     * over-wide temp pair silently hard-clipped instead of eliding).
     */
    val colWidthDp: Float
        get() = (widthDp - 2f * padDp - 3f) / 4f

    enum class Tier { XXS, XS, S, M, L, XL }

    /**
     * Which optional rows the *content* wants this render, independent of
     * whether the footprint can fit them.
     *
     * The size ladder used to assume every row was always present. Once the
     * widget started hiding rows that have nothing worth saying (see
     * [WidgetContent]), that assumption would have left their reserved height
     * as empty space — the exact "half the widget is wasted" problem this whole
     * sizing model was built to fix. So the solver only budgets for rows that
     * will actually be drawn, and a calmer day gets bigger type rather than a
     * gap.
     *
     * [heroLineCells] is the length of the hero's condition line, which can now
     * be a sentence ("deszcz wkrótce, do ~23:00") rather than one word, so the
     * line is sized to its real text instead of an assumed width.
     */
    data class Rows(
        val meta: Boolean = true,
        val footer: Boolean = true,
        val pop: Boolean = true,
        val bars: Boolean = true,
        val stats: Boolean = true,
        val heroLineCells: Int = 20,
        val metaCells: Int = 34,
    ) {
        companion object {
            val ALL = Rows()
        }
    }

    companion object {
        // ---- reference design, scale 1.0, heights in dp ----------------------
        // Split into a SCALABLE part (text lines and icons, which grow and
        // shrink with the scale) and a FIXED part (the margins and paddings
        // literally written in res/layout/weather_widget.xml, which do not).
        //
        // Keeping them apart is what makes the solver exact rather than
        // approximate: `scale = (budget - fixed) / scalable` lands on a layout
        // that genuinely measures to the granted height. Folding the margins in
        // with everything else and scaling the sum overshoots by the margin
        // total — a few dp, which is precisely the size of the overshoot that
        // has silently clipped the bottom row of this widget more than once.
        //
        // Scalable line heights are text size x its measured line factor. Those
        // factors were read off real renders (view-tree dump, height / text
        // size), not assumed, and they are not one number: Latin monospace with
        // includeFontPadding="false" lands at ~1.16, while a line containing a
        // fallback-font glyph — the `▽` PoP marker, the `┌─ ─┐` box drawing in
        // the header — jumps to ~1.35, because the fallback font brings its own
        // taller metrics. Assuming a flat 1.25 for everything meant most rows
        // were over-reserved and two were under-reserved at the same time.
        private const val REF_HEADER = 14.7f // 11sp, box-drawing glyphs (x1.34)
        private const val REF_HERO_TEMP = 33f // 28sp (x1.17)
        private const val REF_HERO_NOW = 13f // condition line, 11sp (x1.16)

        /**
         * The hero row is as tall as its tallest child, so this budget only
         * holds while the right-hand stat column stays shorter than the
         * temp + condition stack on the left.
         *
         * At scale 1.0 the left stack is 36 + 14 = 50dp and the stat column is
         * `sparkline (14sp -> 17.5) + 2 x stat line (10sp -> 12.5)` = 42.5dp.
         * That "2 x" is why the sparkline and its "max %" figure share a row in
         * the layout: as four separate lines the stat column measured 55dp,
         * overran this budget, and clipped the footer off the bottom edge.
         * Adding a fourth stat line means raising this number.
         */
        private const val REF_HERO = REF_HERO_TEMP + REF_HERO_NOW
        private const val REF_GRID_LABEL = 13f // 11sp (x1.16)
        private const val REF_GRID_ICON = 20f
        private const val REF_GRID_TEMP = 16.3f // 14sp (x1.16)
        private const val REF_GRID_BAR = 5f
        private const val REF_GRID_POP = 13.6f // 10sp, "▽" glyph (x1.36)
        private const val REF_META = 11.7f // 10sp (x1.17)
        private const val REF_FOOTER = 12.8f // 11sp (x1.16)

        // Fixed margins, mirroring weather_widget.xml exactly. Change one there,
        // change it here.
        private const val FIX_HERO_MARGIN = 3f // widget_hero_row marginTop
        private const val FIX_RULE = 9f // widget_rule marginTop + 1dp + marginBottom
        private const val FIX_GRID_BASE = 4f // column paddings + icon margins
        private const val FIX_GRID_BAR = 3f // bar marginTop + marginBottom
        private const val FIX_META = 4f // widget_meta_line marginTop
        private const val FIX_FOOTER = 3f // widget_footer_joke marginTop

        /** Nominal content padding (dp) inside widget_root, before scaling. */
        private const val REF_PAD = 6f

        /**
         * Smallest scale a tier is allowed to be squeezed to before the next
         * leaner tier is preferred instead. Below roughly this, monospace text
         * stops being readable at arm's length on a phone home screen, and
         * dropping a row outright buys the *remaining* rows more size than
         * shrinking everything does.
         */
        private const val MIN_SCALE = 0.70f

        /**
         * Fraction of the available height the solver aims to fill.
         *
         * The reference ladder predicts measured height closely but not exactly
         * — fonts round to whole pixels, LinearLayout rounds again, and a city
         * name can pull a fallback font with its own metrics into the header.
         * Solving for a literal 100% leaves nothing for that, and being 1dp over
         * doesn't degrade gracefully: it shears the bottom row in half. 3% of a
         * 176dp widget is ~5dp, which costs nothing visible and is more than
         * the error has ever measured. widget_content's center_vertical gravity
         * splits whatever's left over between top and bottom.
         */
        private const val FILL = 0.97f

        /** Ceiling on growth, so a very tall placement doesn't turn into a poster. */
        private const val MAX_SCALE = 1.85f

        // Advance widths as a fraction of text size, used to predict how wide a
        // string will be without constructing a Paint. All three were measured off
        // real renders on device (dumping the view tree and dividing each
        // wrap_content TextView's width by its character count and text size),
        // not guessed — the second and third are why that mattered.
        //
        /** Latin/digits in the `monospace` family Android actually resolves. */
        private const val MONO_ADVANCE = 0.62f

        /** `▽`, the PoP marker. Comes from a fallback font and runs wider. */
        private const val GLYPH_ADVANCE = 0.68f

        /** `▁▂▃▄▅▆▇█`, the sparkline bars. Fallback font, wider still — assuming
         * these matched Latin is what squeezed the "▽ max%" figure beside them
         * down to "▽ 10…" on a wide widget. */
        private const val BLOCK_ADVANCE = 0.78f

        /** Widths (dp) below which the hero's right-hand stat column, the AQI
         * readout and the sync clock respectively stop earning their space —
         * they'd starve the hero temp and condition line, which is the thing the
         * widget exists to show. */
        private const val STATS_MIN_WIDTH_DP = 205
        private const val AQI_MIN_WIDTH_DP = 235
        private const val SYNC_MIN_WIDTH_DP = 260

        /**
         * What each tier renders, as (scalable dp, fixed dp).
         *
         * Leaner tiers drop whole rows rather than shrinking every row toward
         * illegibility — below roughly [MIN_SCALE] the honest trade is "show
         * less, legibly" rather than "show everything, unreadably". They're
         * dropped in reverse order of how much anyone looks at them: the
         * feels-like/humidity/wind meta line first, then the sigma joke, then
         * the per-day rain figures and range bars, then the hero condition
         * line. The header never drops: it is the only row carrying controls.
         */
        private fun refHeight(tier: Tier, rows: Rows): Pair<Float, Float> {
            // Always present, at every tier: the header (the only row with
            // controls), the hero temperature, and the day icons + temps.
            var scalable = REF_HEADER + REF_HERO_TEMP + REF_GRID_ICON + REF_GRID_TEMP
            var fixed = FIX_HERO_MARGIN + FIX_RULE + FIX_GRID_BASE

            // Dropped last, below XS: day-of-week labels. XXS exists because
            // the declared minResizeHeight (90dp) genuinely does not fit XS —
            // at 90dp the solver clamped at MIN_SCALE and still overran by
            // ~9dp, which on device sheared the 4-day temps in half.
            if (tier >= Tier.XS) scalable += REF_GRID_LABEL
            // ...then the hero condition line, below S.
            if (tier >= Tier.S) scalable += REF_HERO_NOW
            // ...then the per-day rain figures and range bars, below M.
            if (tier >= Tier.M && rows.pop) scalable += REF_GRID_POP
            if (tier >= Tier.M && rows.bars) {
                scalable += REF_GRID_BAR
                fixed += FIX_GRID_BAR
            }
            // ...then the footer line, below L.
            if (tier >= Tier.L && rows.footer) {
                scalable += REF_FOOTER
                fixed += FIX_FOOTER
            }
            // ...and first of all, the meta line, below XL.
            if (tier >= Tier.XL && rows.meta) {
                scalable += REF_META
                fixed += FIX_META
            }
            return scalable to fixed
        }

        /**
         * Resolves the full size ladder for a granted [widthDp] x [heightDp]
         * footprint.
         *
         * Picks the richest tier that still fits at [MIN_SCALE], then scales that
         * tier to fill the height exactly (clamped to [MIN_SCALE]..[MAX_SCALE]),
         * then applies the width-driven caps and gates.
         */
        fun forSize(widthDp: Int, heightDp: Int, rows: Rows = Rows.ALL): WidgetMetrics {
            val w = max(80, widthDp)
            val h = max(48, heightDp)

            // Padding scales too, but the scale depends on the budget left after
            // padding — so solve it twice: once with the nominal padding, then
            // again with the padding that scale implies. Two passes converge to
            // well under a dp; there's nothing to gain from iterating further.
            fun solve(padDp: Float): Pair<Tier, Float> {
                val budget = max(24f, h - 2f * padDp)
                val t = Tier.entries.reversed().firstOrNull { tt ->
                    val (scalable, fixed) = refHeight(tt, rows)
                    fixed + scalable * MIN_SCALE <= budget
                } ?: Tier.XXS
                val (scalable, fixed) = refHeight(t, rows)
                return t to (FILL * (budget - fixed) / scalable).coerceIn(MIN_SCALE, MAX_SCALE)
            }

            val firstPass = solve(REF_PAD)
            val padDp = (REF_PAD * firstPass.second).roundToInt().coerceIn(3, 10)
            val (tier, scale) = solve(padDp.toFloat())

            val showDayLabel = tier >= Tier.XS
            val showNowLine = tier >= Tier.S
            val showFooter = tier >= Tier.L && rows.footer
            val showMeta = tier >= Tier.XL && rows.meta
            val showPop = tier >= Tier.M && rows.pop
            val showBars = tier >= Tier.M && rows.bars
            val showStats = rows.stats && w >= STATS_MIN_WIDTH_DP && tier >= Tier.S
            val showAqi = showStats && w >= AQI_MIN_WIDTH_DP
            val showSync = showStats && w >= SYNC_MIN_WIDTH_DP && tier >= Tier.L

            // --- width-driven caps -------------------------------------------
            val innerW = w - 2f * padDp
            val colW = (innerW - 3f) / 4f

            // "30°/16°" is 8 monospace cells; "30°" is 3. Prefer the pair, fall
            // back to today's max alone only when the pair can't be drawn at a
            // size that's still legible next to the rest of the grid.
            val pairCap = colW / (8f * MONO_ADVANCE)
            val singleCap = colW / (4f * MONO_ADVANCE)
            val wantTempSp = 14f * scale
            val showTempPair = pairCap >= min(wantTempSp, 9.5f)
            val dayTempSp = min(wantTempSp, if (showTempPair) pairCap else singleCap)

            // "▽ 100%" is 6 cells, one of them the wide fallback glyph.
            val dayPopSp = min(10f * scale, colW / (5f * MONO_ADVANCE + GLYPH_ADVANCE))
            // Day labels are 4 cells at most ("dziś").
            val dayLabelSp = min(11f * scale, colW / (4.4f * MONO_ADVANCE))

            // The grid icon may grow with the column when there's width to spare,
            // but never past the room the row reserves for it.
            val dayIconDp = min(20f * scale, colW * 0.62f).roundToInt().coerceAtLeast(8)

            val heroIconDp = (30f * scale).roundToInt().coerceIn(12, 56)

            // The hero row is [icon][left cluster, weight 6][stat column, weight 5],
            // so the weights split what is left AFTER the icon and its 6dp end
            // margin — not the full inner width. Forgetting that overestimated the
            // stat column by the icon's width, which is how a 300dp-wide widget
            // ended up ellipsizing a 6-character "▽ 100%" and a fixed-width clock.
            val heroAvail = max(40f, innerW - heroIconDp - 6f)
            val heroWidth = if (showStats) heroAvail * (6f / 11f) else heroAvail
            // Hero temp is at most 4 cells ("-12°"), plus the unit glyph.
            val heroTempSp = min(28f * scale, (heroWidth - 10f) / (4.6f * MONO_ADVANCE))

            val statW = if (showStats) heroAvail * (5f / 11f) else 0f
            // Cap against the longest line the column has to render in full:
            // "AQI 142 · sensitive" / "sync 00:40:24", 19 cells at worst. Every
            // line here is a readout that loses its meaning truncated — a clock
            // missing its seconds, an AQI missing its category — so the column
            // sizes to fit them rather than letting them ellipsize.
            // WeatherWidgetProvider.aqiShortLabel exists to keep that 19 honest.
            val statSp = if (showStats) min(10f * scale, statW / (19f * MONO_ADVANCE)) else 0f
            val popMaxW = (5f * MONO_ADVANCE + GLYPH_ADVANCE) * statSp
            // The sparkline shares its row with "▽ max%", so it takes whatever
            // the figure doesn't need, less the 4dp gap between them and 4dp of
            // slack — at exactly the arithmetic limit, LinearLayout's own
            // rounding was enough to ellipsize "▽ 100%" down to "▽ 10…".
            val sparkSp = if (showStats) {
                min(14f * scale, (statW - popMaxW - 8f) / (4f * BLOCK_ADVANCE))
            } else {
                14f * scale
            }

            return WidgetMetrics(
                widthDp = w,
                heightDp = h,
                tier = tier,
                scale = scale,
                padDp = padDp,

                headerSp = min(11f * scale, innerW / (26f * MONO_ADVANCE)),
                dotDp = (8f * scale).roundToInt().coerceIn(5, 14),
                refreshDp = (13f * scale).roundToInt().coerceIn(9, 22),

                heroIconDp = heroIconDp,
                heroTempSp = heroTempSp.coerceAtLeast(13f),
                heroUnitSp = (heroTempSp * 0.42f).coerceAtLeast(6f),
                nowLineSp = min(11f * scale, heroWidth / (max(20, rows.heroLineCells) * MONO_ADVANCE))
                    .coerceAtLeast(6.5f),
                statSp = statSp.coerceAtLeast(if (showStats) 6.5f else 0f),
                sparkSp = sparkSp.coerceIn(8f, 26f),

                dayLabelSp = dayLabelSp.coerceAtLeast(6.5f),
                dayIconDp = dayIconDp,
                dayTempSp = dayTempSp.coerceAtLeast(8f),
                dayPopSp = dayPopSp.coerceAtLeast(6.5f),
                barHeightDp = (5f * scale).roundToInt().coerceIn(3, 10),

                metaSp = min(10f * scale, innerW / (max(34, rows.metaCells) * MONO_ADVANCE)).coerceAtLeast(6.5f),
                footerSp = min(11f * scale, innerW / (30f * MONO_ADVANCE)).coerceAtLeast(6.5f),
                bannerSp = (9f * scale).coerceIn(7f, 14f),

                showNowLine = showNowLine,
                showMeta = showMeta,
                showFooter = showFooter,
                showPop = showPop,
                showBars = showBars,
                showStats = showStats,
                showAqi = showAqi,
                showSync = showSync,
                showTempPair = showTempPair,
                showDayLabel = showDayLabel,
            )
        }
    }
}
