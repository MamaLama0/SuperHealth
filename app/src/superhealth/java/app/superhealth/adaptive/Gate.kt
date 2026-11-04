package app.superhealth.adaptive

/**
 * Porte de confiance commune : un modèle personnel ne remplace la règle de base que s'il la bat sur des données qu'il
 * n'a pas encore vues. Les erreurs sont comparées par blocs successifs de [blockSize] observations.
 *
 * - activation après deux blocs gagnés de suite (erreur médiane ≤ 95 % de celle de la base) ;
 * - retrait après deux blocs perdus de suite (erreur médiane supérieure à la base) ;
 * - une fois actif, le poids du modèle personnel monte d'un quart par bloc gagné et baisse d'un quart par bloc perdu.
 *
 * Calcul pur et rejouable : on le refait depuis l'historique plutôt que de stocker un état.
 */
class Gate(private val blockSize: Int, private val margin: Double = 0.95) {
    data class Result(val weight: Double, val active: Boolean, val blocks: Int, val wins: Int)

    /** [errors] : paires (erreur de base, erreur personnelle) dans l'ordre chronologique. */
    fun evaluate(errors: List<Pair<Double, Double>>): Result {
        var active = false
        var credit = 0
        var winRun = 0
        var lossRun = 0
        var wins = 0
        val blocks = errors.chunked(blockSize).filter { it.size == blockSize }
        for (block in blocks) {
            val base = median(block.map { it.first })
            val personal = median(block.map { it.second })
            when {
                personal <= base * margin -> { winRun++; lossRun = 0; wins++ }
                personal > base -> { lossRun++; winRun = 0 }
                else -> { winRun = 0; lossRun = 0 }
            }
            if (!active && winRun >= 2) { active = true; credit = 2 }
            else if (active) {
                if (personal <= base * margin) credit = (credit + 1).coerceAtMost(4)
                else if (personal > base) credit = (credit - 1).coerceAtLeast(0)
                if (lossRun >= 2) { active = false; credit = 0 }
            }
        }
        return Result(if (active) credit / 4.0 else 0.0, active, blocks.size, wins)
    }

    companion object {
        fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val s = values.sorted()
            return (s[(s.size - 1) / 2] + s[s.size / 2]) / 2
        }

        /** Mélange borné : base + w·(personnel − base). */
        fun blend(base: Double, personal: Double, weight: Double) = base + weight.coerceIn(0.0, 1.0) * (personal - base)
    }
}
