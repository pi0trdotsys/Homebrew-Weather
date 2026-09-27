package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.abs

/**
 * English footer copy, for [Lang.EN] — the counterpart of [CleanJokes] and
 * [SigmaJokes], with the same pools-by-kind shape, the same pick rule and the
 * same 50-character ceiling ([SigmaJokes.MAX_LINE_LENGTH]).
 *
 * [CLEAN] is the web app's original developer-humour set, trimmed to lines
 * that fit the widget footer. [SIGMA] is the sigma voice in English: the same
 * self-mocking grindset register, milder on the swearing because English
 * slang doesn't carry "kurwa" one-to-one. The web app gets both through
 * src/lib/jokes.generated.ts (bun scripts/gen-jokes.ts).
 */
object EnJokes {

    private val CLEAN: Map<String, List<String>> = mapOf(
        "sun" to listOf(
            "It compiles. The sun is out. Ship it.",
            "Warning: high UV. `sudo apply --sunscreen`.",
            "Perfect weather for `git push --force`. Don't.",
            "Stack Overflow is up. So is the sun.",
            "`while(sunny) { code(); hydrate(); }`",
            "Zero clouds, zero excuses. Go touch grass(1).",
            "CPU throttling from heat. So is your brain.",
            "Bright enough to finally see the whiteboard bugs.",
            "`export SUNSHINE=true` — no restart required.",
            "Clear skies, clear cache, clear conscience.",
            "`assert weather.clouds == 0` — passes for once.",
            "Solar-powered productivity, briefly.",
        ),
        "partly" to listOf(
            "50% clouds, 50% clarity. Like your specs.",
            "Partly cloudy — much like this legacy codebase.",
            "TODO: figure out if it's sunny or not.",
            "Schrödinger's sky: sunny and not, till observed.",
            "Flaky test: weather.status is nondeterministic.",
            "A/B testing the sky today.",
            "Merge conflict: sun.branch vs cloud.branch.",
            "Race condition: cloud and sun both render first.",
        ),
        "cloud" to listOf(
            "Overcast. Perfect lighting for a dark theme.",
            "The cloud is down. Wait, no — it's above you.",
            "`kubectl get weather` → still cloudy.",
            "Gray sky, gray Slack, gray coffee. Balanced.",
            "100% cloud coverage, 0% cloud compute.",
            "Overcast — nature's `prefers-color-scheme: dark`.",
            "The sky's rendering a spinner. Please wait.",
            "Gray CI, gray sky, gray hopes for the deploy.",
        ),
        "fog" to listOf(
            "Visibility low. Just like your test coverage.",
            "Fog: nature's `console.log('here')`.",
            "Can't see the deploy for the fog.",
            "`grep -r 'sun' /sky/` → no matches.",
            "Stack trace: obscured by weather.fog(dense=true).",
            "404: horizon not found.",
            "Documentation as clear as this fog.",
            "`try { see(horizon) } catch { squint() }`",
        ),
        "rain" to listOf(
            "It's raining. Perfect time to `git blame`.",
            "Rain detected. Rebooting the umbrella.",
            "`npm install rain-jacket --save`.",
            "Water is falling. So is your uptime probably.",
            "It's raining bugs. And water.",
            "Precipitation: 100%. Motivation: 404.",
            "`try { walk() } catch (Rain e) { stayIndoors() }`",
            "The cloud finally pushed its changes.",
            "Umbrella.exe has stopped working.",
            "Every drop a tiny `console.log('wet')`.",
            "The forecast API returned 200 OK, you did not.",
        ),
        "snow" to listOf(
            "Snow day. Merge conflicts still exist.",
            "❄ = new File(); cold.deploy();",
            "The build is frozen. Literally.",
            "Snowflake type detected. Everywhere.",
            "White screen of death, but outside.",
            "Cache invalidated by frost.",
            "Every flake unique, unlike your commit messages.",
            "`git status`: all white, nothing staged.",
        ),
        "thunder" to listOf(
            "Thunder detected. Unplug the servers.",
            "`throw new Storm();`",
            "Lightning strike: instant `git reset --hard`.",
            "Prod is down. Also the power.",
            "Uncaught exception: SkyException at line 0.",
            "Latency spike caused by literal lightning.",
            "The sky just force-pushed to main.",
            "Segmentation fault (core dumped by Zeus).",
        ),
        "night" to listOf(
            "It's late. `commit -m 'wip'` and sleep.",
            "The sun has 404'd. Try again in 8 hours.",
            "Dark mode: engaged by nature.",
            "Night shift. The rubber duck is listening.",
            "`cron.schedule('0 3 * * *', panic)`",
            "Still awake debugging life choices.",
            "`while(true) { doomscroll(); regret(); }`",
            "The moon compiled fine. You did not.",
        ),
    )

    private val SIGMA: Map<String, List<String>> = mapOf(
        "sun" to listOf(
            "sun's out. betas complain about UV",
            "+25°C: chest day weather, not crying weather",
            "grindset weather. go out or stay a nobody",
            "nice day and you're inside like an NPC",
            "the sun shines harder than your career",
            "UV index higher than your self-esteem",
            "free vitamin D. discipline sold separately",
        ),
        "partly" to listOf(
            "half sun, half clouds, full excuses",
            "partly cloudy, like your five-year plan",
            "the sky can't commit either. relatable",
            "some sun. enough to have no excuses",
            "clouds come and go. so does your drive",
            "sigma walks anyway. beta checks the app again",
            "mixed skies, mixed signals, same old you",
        ),
        "cloud" to listOf(
            "grey sky. grey mood is optional",
            "overcast. perfect light for staying average",
            "no sun today. no excuses either",
            "clouds everywhere, ambition nowhere",
            "grey as your LinkedIn posts",
            "overcast. sigma doesn't need the sun",
            "dull sky. don't match it",
        ),
        "fog" to listOf(
            "fog so thick you can't see your goals either",
            "visibility zero. same as your plan",
            "foggy. like your head before coffee",
            "can't see a thing. perfect cover for cardio",
            "fog out there, fog in your inbox",
            "the horizon is gone. so are your excuses",
            "misty morning, sigma still shows up",
        ),
        "rain" to listOf(
            "rain. real ones bring a jacket, not excuses",
            "it's pouring. betas cancel, sigmas go",
            "wet out there. harden up",
            "rain again. umbrella or cope",
            "it rains on everyone. only you complain",
            "soaked shoes build character. allegedly",
            "rain check? no. rain grind.",
        ),
        "snow" to listOf(
            "snow. shovel first, whine later",
            "white out there, cold feet in here",
            "snow day. still a work day for sigmas",
            "it's snowing. layers, not excuses",
            "frozen roads, frozen ambitions. fix one",
            "cold enough to test your whole personality",
            "snowman built. career still not",
        ),
        "thunder" to listOf(
            "thunder. louder than your ambitions",
            "storm outside. stay in and plot something",
            "lightning hits harder than your alarm clock",
            "storm warning. unplug and do push-ups",
            "the sky is raging. you're just lazy",
            "thunderstorm. even the sky has more drive",
            "loud storm. still quieter than your excuses",
        ),
        "night" to listOf(
            "it's late. sigma sleeps, beta scrolls",
            "night. tomorrow you won't be ready either",
            "dark out. go to bed, champ",
            "midnight grind or midnight doomscroll?",
            "the moon is up. so are you. why",
            "sleep is gains. scrolling isn't",
            "night mode on. discipline mode off, again",
        ),
    )

    internal val cleanPools: Map<String, List<String>> get() = CLEAN
    internal val sigmaPools: Map<String, List<String>> get() = SIGMA

    fun pickClean(kind: String, isNight: Boolean = false, seed: Int = 0): String = pick(CLEAN, kind, isNight, seed)

    fun pickSigma(kind: String, isNight: Boolean = false, seed: Int = 0): String = pick(SIGMA, kind, isNight, seed)

    private fun pick(pools: Map<String, List<String>>, kind: String, isNight: Boolean, seed: Int): String {
        val kindPool = pools[kind] ?: pools.getValue("cloud")
        val pool = if (isNight) (pools.getValue("night") + kindPool) else kindPool
        return pool[abs(seed) % pool.size]
    }
}

/** The footer / brief-tail line for a tone and language, from the right pool. */
object Jokes {
    fun pick(tone: Tone, lang: Lang, kind: String, isNight: Boolean, seed: Int): String = when (tone) {
        Tone.CLEAN -> if (lang == Lang.EN) EnJokes.pickClean(kind, isNight, seed) else CleanJokes.pick(kind, isNight, seed)
        Tone.SIGMA, Tone.RUDE -> if (lang == Lang.EN) EnJokes.pickSigma(kind, isNight, seed) else SigmaJokes.pick(kind, isNight, seed)
    }
}
