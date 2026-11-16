package com.wangzan101.mika.core.career

import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MatchComponent(val label: String, val score: Int, val weight: Int, val detail: String)

data class JobMatch(
    val jobId: String,
    val score: Int,
    val components: List<MatchComponent>,
    val matchedSkills: List<String>,
    val missingSkills: List<String>,
    val missingQualifications: List<String>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>,
    val explanation: String,
    /** Set when a hard preference or a missing must-have skill capped the score. */
    val cappedBy: String? = null,
)

/**
 * Skill text normalisation and conservative extraction. Extraction only reports terms that are
 * actually present; it never guesses a skill from a job title.
 */
object SkillVocabulary {
    private val ALIASES = mapOf(
        "js" to "javascript", "ts" to "typescript", "k8s" to "kubernetes", "postgres" to "postgresql",
        "golang" to "go", "py" to "python", "ml" to "machine learning", "ai" to "artificial intelligence",
        "ux" to "user experience", "ui" to "user interface", "ci cd" to "ci/cd", "rest" to "rest apis",
        "compose" to "jetpack compose", "compose ui" to "jetpack compose",
        "node" to "node.js", "nodejs" to "node.js", "reactjs" to "react", "android sdk" to "android",
        "kotlin multiplatform" to "kotlin", "spring boot" to "spring", "amazon web services" to "aws",
        "google cloud platform" to "gcp", "microsoft azure" to "azure", "powerbi" to "power bi",
    )

    private val COMMON = listOf(
        "kotlin", "java", "python", "javascript", "typescript", "go", "rust", "swift", "c++", "c#", "sql",
        "android", "ios", "jetpack compose", "compose", "coroutines", "flow", "room", "retrofit", "dagger",
        "spring", "node.js", "react", "angular", "vue", "django", "flask", "fastapi", "graphql", "rest apis",
        "aws", "gcp", "azure", "docker", "kubernetes", "terraform", "git", "github actions", "ci/cd",
        "machine learning", "deep learning", "pytorch", "tensorflow", "nlp", "data analysis", "pandas",
        "postgresql", "mysql", "mongodb", "redis", "kafka", "elasticsearch", "figma", "user research",
        "product management", "agile", "scrum", "excel", "tableau", "power bi", "salesforce", "seo",
        "content marketing", "copywriting", "accounting", "bookkeeping", "financial modelling", "recruiting",
    )

    fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9+#./ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun canonical(value: String): String {
        val n = normalize(value)
        return ALIASES[n] ?: n
    }

    private val DISPLAY = mapOf(
        "kotlin" to "Kotlin", "android" to "Android", "jetpack compose" to "Jetpack Compose", "kubernetes" to "Kubernetes",
        "python" to "Python", "javascript" to "JavaScript", "typescript" to "TypeScript", "node.js" to "Node.js",
        "aws" to "AWS", "gcp" to "GCP", "ci/cd" to "CI/CD", "sql" to "SQL", "rest apis" to "REST APIs",
        "graphql" to "GraphQL", "postgresql" to "PostgreSQL", "mysql" to "MySQL", "mongodb" to "MongoDB",
        "docker" to "Docker", "terraform" to "Terraform", "github actions" to "GitHub Actions", "power bi" to "Power BI",
        "machine learning" to "Machine Learning", "deep learning" to "Deep Learning", "nlp" to "NLP", "seo" to "SEO",
        "c++" to "C++", "c#" to "C#", "ios" to "iOS", "ux" to "UX", "ui" to "UI",
    )

    /** Human-readable skill name for UI and documents; matching always uses [canonical]. */
    fun display(canonical: String): String = DISPLAY[canonical] ?: canonical.split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }

    fun splitList(value: String): List<String> =
        value.split(',', ';', '|', '/', '\n', '·').map { it.trim() }.filter { it.isNotEmpty() }

    fun fromProfile(profile: CandidateProfile): List<String> =
        profile.confirmedSkills().map { canonical(it.name) }.filter { it.isNotEmpty() }.distinct()

    fun extract(text: String, known: Collection<String> = emptyList()): List<String> {
        val haystack = " ${normalize(text)} "
        val candidates = (known.map(::canonical) + COMMON).filter { it.length >= 2 }.distinct()
        return candidates.filter { skill ->
            val needle = " ${normalize(skill)} "
            haystack.contains(needle) || Regex("(?:^|\\s)${Regex.escape(normalize(skill))}(?:\\s|$)").containsMatchIn(haystack.trim())
        }.distinct()
    }

    /** True when a job requirement names a skill the candidate has no confirmed entry for. */
    fun isMustHave(skill: String, mustHave: List<String>): Boolean {
        val s = canonical(skill)
        return mustHave.map(::canonical).any { it == s || it.contains(s) || s.contains(it) }
    }
}

object SalaryParser {
    data class Parsed(val min: Int, val max: Int, val currency: String, val annual: Boolean)

    private val CURRENCY = Regex("(?i)\\b(usd|eur|gbp|inr|aed|sar|cad|aud|chf|cny|rmb|jpy)\\b|([$€£¥₹])")
    private val NUMBER = Regex("\\d[\\d,.]*")
    private val PERIOD = Regex("(?i)(?:per|/|a)\\s*(hour|hr|day|week|month|year|annum)|hourly|daily|weekly|monthly|annual(?:ly)?")

    fun parse(text: String): Parsed? {
        if (text.isBlank()) return null
        val currency = when {
            text.contains("$") || Regex("(?i)\\busd\\b").containsMatchIn(text) -> "USD"
            text.contains("€") || Regex("(?i)\\beur\\b").containsMatchIn(text) -> "EUR"
            text.contains("£") || Regex("(?i)\\bgbp\\b").containsMatchIn(text) -> "GBP"
            text.contains("₹") || Regex("(?i)\\binr\\b").containsMatchIn(text) -> "INR"
            text.contains("¥") || Regex("(?i)\\b(?:cny|rmb|jpy)\\b").containsMatchIn(text) -> "CNY"
            Regex("(?i)\\baed\\b").containsMatchIn(text) -> "AED"
            else -> ""
        }
        val period = PERIOD.find(text)?.groupValues?.get(1)?.lowercase()?.ifEmpty { null }
        val multiplier = when {
            period == "hour" || period == "hr" || text.lowercase().contains("hourly") -> 2080.0
            period == "day" || text.lowercase().contains("daily") -> 260.0
            period == "week" || text.lowercase().contains("weekly") -> 52.0
            period == "month" || text.lowercase().contains("monthly") -> 12.0
            else -> 1.0
        }
        val values = NUMBER.findAll(text).mapNotNull { m ->
            val raw = m.value.trimEnd(',', '.')
            val cleaned = raw.replace(",", "")
            val k = raw.lowercase().endsWith("k") || Regex("(?i)\\d\\s*k\\b").containsMatchIn(text.substring(m.range.first, min(text.length, m.range.last + 3)))
            val base = cleaned.toDoubleOrNull() ?: return@mapNotNull null
            val scaled = if (k && base < 1000) base * 1000 else base
            (scaled * multiplier).roundToInt()
        }.filter { it > 0 }.toList()
        if (values.isEmpty()) return null
        val lo = values.minOrNull() ?: return null
        val hi = values.maxOrNull() ?: return null
        // Two unrelated numbers far apart are more likely a bonus or a year; keep a sane range.
        if (hi > lo * 12) return null
        return Parsed(min = lo, max = hi, currency = currency, annual = multiplier != 1.0 || period == null || period == "year" || period == "annum")
    }
}

/**
 * Explainable fit scoring. The result is a convenience estimate from stated facts, never a
 * probability of being hired and never a reason to hide a job.
 */
object JobMatcher {
    fun requiredSkills(profile: CandidateProfile, job: JobPosting): List<String> {
        val explicit = job.skills.map(SkillVocabulary::canonical).filter { it.isNotBlank() }
        val inferred = SkillVocabulary.extract(
            (listOf(job.title) + job.requirements + job.description).joinToString("\n"),
            explicit + SkillVocabulary.fromProfile(profile),
        )
        return (explicit + inferred).distinct().take(30)
    }

    fun match(profile: CandidateProfile, job: JobPosting, prefs: CareerPreferences = profile.preferences, now: YearMonth = YearMonth.now()): JobMatch {
        val mine = SkillVocabulary.fromProfile(profile).toSet()
        val required = requiredSkills(profile, job)
        val matchedCanonical = required.filter { mine.contains(it) }
        val missingCanonical = required.filterNot { mine.contains(it) }
        val matched = matchedCanonical.map(SkillVocabulary::display)
        val missing = missingCanonical.map(SkillVocabulary::display)
        val mustHaveMissing = missingCanonical.filter { SkillVocabulary.isMustHave(it, prefs.mustHaveSkills) }
            .map(SkillVocabulary::display)

        val skillsScore = when {
            required.isEmpty() -> 55
            else -> (100.0 * matched.size / required.size).roundToInt()
        }

        val years = CareerDates.experienceYears(profile, now)
        val need = job.experienceYears
        val experienceScore = when {
            need == null || need <= 0 -> 60
            years >= need -> 100
            years >= need - 1 -> 75
            else -> max(10, (100.0 * years / need).roundToInt())
        }

        val educationText = profile.confirmedEducation().joinToString(" ") { "${it.degree} ${it.fieldOfStudy}" } +
            " " + profile.confirmedQualifications().joinToString(" ") { it.name }
        val educationMissing = mutableListOf<String>()
        val educationScore = if (job.educationRequirement.isBlank()) 60 else {
            val wanted = SkillVocabulary.normalize(job.educationRequirement)
            val has = educationText.isNotBlank() && SkillVocabulary.normalize(educationText).split(' ').any { w ->
                w.length >= 3 && wanted.contains(w)
            }
            if (has) 100 else {
                educationMissing += "Stated education requirement: ${job.educationRequirement.trim()}"
                35
            }
        }

        val jobText = SkillVocabulary.normalize("${job.location} ${job.description} ${job.title}")
        val locationScore = when {
            job.remote == RemotePreference.Remote && prefs.remote in setOf(RemotePreference.Remote, RemotePreference.Unspecified) -> 100
            prefs.preferredLocations.any { loc -> jobText.contains(SkillVocabulary.normalize(loc)) } -> 100
            job.location.isBlank() -> 50
            prefs.remote == RemotePreference.Remote -> 40
            prefs.preferredLocations.isEmpty() -> 55
            else -> 30
        }

        val roleHit = prefs.targetRoles.any { role ->
            val words = SkillVocabulary.normalize(role).split(' ').filter { it.length >= 3 }
            words.isNotEmpty() && words.all { it in SkillVocabulary.normalize(job.title) }
        }
        val typeHit = prefs.employmentTypes.isEmpty() || job.employmentType == EmploymentType.Unspecified ||
            job.employmentType in prefs.employmentTypes
        val industryHit = prefs.industries.isEmpty() || prefs.industries.any { jobText.contains(SkillVocabulary.normalize(it)) }
        val preferenceScore = when {
            prefs.targetRoles.isEmpty() && prefs.employmentTypes.isEmpty() && prefs.industries.isEmpty() -> 50
            else -> listOf(roleHit, typeHit, industryHit).count { it } * 100 / 3
        }

        val salary = SalaryParser.parse(job.salaryText)
        val salaryScore = when {
            salary == null || prefs.minSalary == null -> 60
            prefs.currency.isNotBlank() && salary.currency.isNotBlank() && !prefs.currency.equals(salary.currency, true) -> 55
            salary.max >= prefs.minSalary -> 100
            salary.min >= prefs.minSalary * 0.9 -> 80
            else -> max(5, (100.0 * salary.max / prefs.minSalary).roundToInt())
        }

        val w = prefs.weights
        val parts = listOf(
            MatchComponent("Skills", skillsScore, w.skills, "${matched.size} of ${required.size} named skills present"),
            MatchComponent("Experience", experienceScore, w.experience,
                need?.let { "needs about $it year(s); confirmed history is ${"%.1f".format(years)} year(s)" }
                    ?: "no explicit experience requirement"),
            MatchComponent("Education", educationScore, w.education,
                if (job.educationRequirement.isBlank()) "no explicit education requirement" else job.educationRequirement),
            MatchComponent("Location", locationScore, w.location, job.location.ifBlank { "location not stated" }),
            MatchComponent("Preferences", preferenceScore, w.preferences,
                if (prefs.targetRoles.isEmpty()) "no target role configured" else prefs.targetRoles.joinToString()),
            MatchComponent("Salary", salaryScore, w.salary,
                job.salaryText.ifBlank { "salary not stated" }),
        )
        val totalWeight = parts.sumOf { max(0, it.weight) }.takeIf { it > 0 } ?: 1
        var score = (parts.sumOf { it.score * max(0, it.weight) }.toDouble() / totalWeight).roundToInt().coerceIn(0, 100)

        var cappedBy: String? = null
        val dealbreaker = prefs.dealBreakers.firstOrNull { d -> d.isNotBlank() && jobText.contains(SkillVocabulary.normalize(d)) }
        if (dealbreaker != null) {
            score = min(score, 30)
            cappedBy = "matches your deal-breaker: $dealbreaker"
        } else if (mustHaveMissing.isNotEmpty()) {
            score = min(score, 45)
            cappedBy = "missing must-have skill(s): ${mustHaveMissing.joinToString()}"
        }

        val strengths = buildList {
            if (matched.isNotEmpty()) add("Confirmed skills named by the job: ${matched.take(6).joinToString()}")
            if (experienceScore >= 100) add("Your confirmed experience meets the stated requirement")
            if (locationScore >= 100) add("Location or remote preference lines up")
            if (salaryScore >= 100) add("The stated salary reaches your minimum")
        }
        val weaknesses = buildList {
            if (missing.isNotEmpty()) add("Skills not on your confirmed profile: ${missing.take(6).joinToString()}")
            addAll(educationMissing)
            if (experienceScore < 60) add("Stated experience requirement is higher than your confirmed history")
            if (locationScore < 50) add("Location does not match your preferences")
            if (score < 50) add("Several factors do not line up; consider a different role")
        }.distinct()
        val recommendations = buildList {
            if (missing.isNotEmpty() && mustHaveMissing.isEmpty()) {
                add("Only add ${missing.take(3).joinToString()} to your profile if you have genuinely used them")
            }
            if (mustHaveMissing.isNotEmpty()) add("This role expects must-have skills you have not listed: ${mustHaveMissing.joinToString()}")
            if (educationMissing.isNotEmpty()) add("Check whether your qualification meets the stated education requirement")
            if (prefs.targetRoles.isEmpty()) add("Set a target role so ranking reflects your intent")
            if (job.suspicious.isNotEmpty()) add("Review the listing's safety flags before applying")
        }
        val explanation = buildString {
            append("Fit score $score/100 from weighted, stated factors (")
            append(parts.joinToString(", ") { "${it.label} ${it.score}" })
            append("). ")
            cappedBy?.let { append("Capped because it $it. ") }
            append("This is a matching estimate, not a probability of being hired.")
        }
        return JobMatch(
            jobId = job.id, score = score, components = parts,
            matchedSkills = matched, missingSkills = missing, missingQualifications = educationMissing.distinct(),
            strengths = strengths, weaknesses = weaknesses, recommendations = recommendations,
            explanation = explanation, cappedBy = cappedBy,
        )
    }

    fun rank(profile: CandidateProfile, jobs: List<JobPosting>, limit: Int = 20, now: YearMonth = YearMonth.now()): List<Pair<JobPosting, JobMatch>> =
        jobs.filter { it.status == JobStatus.Active || it.status == JobStatus.Shortlisted }
            .filterNot { CareerDates.isExpired(it.deadline, now) }
            .map { it to match(profile, it, profile.preferences, now) }
            .sortedWith(compareByDescending<Pair<JobPosting, JobMatch>> { it.second.score }
                .thenByDescending { it.first.discoveredAtMs })
            .take(max(1, limit))

}
